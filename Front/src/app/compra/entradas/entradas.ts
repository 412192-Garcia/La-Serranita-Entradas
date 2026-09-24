import {ChangeDetectorRef, Component, ElementRef, NgZone, OnDestroy, OnInit} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { Calendario } from '../calendario/calendario';
import { SeleccionEntradas } from '../seleccion-entradas/seleccion-entradas';
import { FormCliente } from '../form-cliente/form-cliente';
import { Resumen } from '../resumen/resumen';
import { CompraService } from '../../services/compra.service';
import { ConfiguracionService } from '../../services/configuracion.service';
import { AnaliticaService } from '../../services/analitica.service';
import { ThemeService } from '../../services/theme.service';
import { PagoExitoso } from '../../resultado-pago/pago-exitoso/pago-exitoso';
import {FormaPagoType, ResumenCompraData} from "../../models/compra";
import { Cupon } from '../../models/cupon';
import { LucideClock, LucideCloudRain, LucideTriangleAlert } from '@lucide/angular';
import { Modal } from '../../shared/modal/modal';
import { Observable, Subscription, of, timer } from 'rxjs';
import { catchError, map, switchMap, take } from 'rxjs/operators';

enum etapaCompra {
  SELECCION,
  DATOS,
  RESUMEN
}

@Component({
  selector: 'app-entradas',
  imports: [
    FormsModule,
    Calendario,
    SeleccionEntradas,
    FormCliente,
    Resumen,
    PagoExitoso,
    LucideClock,
    LucideCloudRain,
    LucideTriangleAlert,
    Modal
  ],
  templateUrl: './entradas.html',
  styleUrl: './entradas.css',
})
export class Entradas implements OnInit, OnDestroy {
  /** Cada cuánto se le pregunta al backend si el pago se acreditó. */
  private static readonly INTERVALO_POLL_MS = 3000;
  /** Techo de consultas (~10 min): pasado eso se deja de insistir. */
  private static readonly MAX_CONSULTAS_POLL = 200;
  /** Estado sintético para distinguir "cerró la ventana" de un estado real del backend. */
  private static readonly VENTANA_CERRADA = 'VENTANA_CERRADA';
  /** El sitio embebido escucha este "type" para saber que el mensaje es nuestro y no de otra cosa. */
  private static readonly MENSAJE_ALTURA = 'la-serranita-alto';

  etapa: etapaCompra = etapaCompra.SELECCION;

  constructor(private compraService: CompraService,
  private cdr: ChangeDetectorRef,
              private ngZone: NgZone,
              private route: ActivatedRoute,
              private themeService: ThemeService,
              private configuracionService: ConfiguracionService,
              private analitica: AnaliticaService,
              private elementRef: ElementRef<HTMLElement>) {}

  /** Puntos de quiebre por defecto del layout responsive (ver aplicarBreakpoints()). */
  private static readonly ANCHO_MOVIL_DEFECTO = 600;
  private static readonly ANCHO_APILADO_DEFECTO = 1200;

  /** <style> inyectado por aplicarBreakpoints(); se guarda la referencia para poder sacarlo en ngOnDestroy. */
  private estiloBreakpoints: HTMLStyleElement | null = null;

  /**
   * El sitio del parque no puede tocar código ni redeployar para probar un ajuste
   * visual: por eso el embebido acepta esta configuración por query param, en vez de
   * quedar todo hardcodeado. Cubre colores (reusa ThemeService, el mismo motor de "Mi
   * cuenta" del módulo interno), radio de esquinas, los puntos de quiebre del layout
   * responsive y el on/off del panel de info. Los hex viajan SIN "#" (ej.
   * ?primario=2563eb) porque "#" en una URL abre el fragmento y cortaría el resto de
   * los parámetros.
   */
  private aplicarConfigDesdeUrl(): void {
    const params = this.route.snapshot.queryParamMap;
    const aHex = (valor: string | null): string | null => (valor && /^[0-9a-fA-F]{6}$/.test(valor)) ? `#${valor}` : null;
    const aEntero = (valor: string | null): number | null => {
      const n = Number(valor);
      return valor !== null && Number.isFinite(n) && n > 0 ? Math.round(n) : null;
    };

    this.themeService.aplicarPrimario(aHex(params.get('primario')));
    this.themeService.aplicarTarjeta(aHex(params.get('tarjeta')));
    this.themeService.aplicarBorde(aHex(params.get('borde')));

    // Fondo detrás de la tarjeta: por defecto transparente (ver styles.css,
    // body.ruta-publica) para que el fondo lo ponga la página del parque. Si en cambio
    // viene ?fondo=, se pisa el body con ese color puntual (el estilo inline gana
    // siempre por sobre la clase, sin necesitar !important).
    const fondo = aHex(params.get('fondo'));
    this.themeService.aplicarFondo(fondo);
    document.body.style.backgroundColor = fondo ?? '';

    // Radio de esquinas de tarjetas/paneles (--radius-lg, ver styles.css). En px.
    const radio = aEntero(params.get('radio'));
    if (radio !== null) {
      document.documentElement.style.setProperty('--radius-lg', `${radio}px`);
    } else {
      document.documentElement.style.removeProperty('--radius-lg');
    }

    const ocultarInfo = params.get('ocultarInfo');
    this.ocultarInfoUtil = ocultarInfo === '1' || ocultarInfo === 'true';

    this.aplicarBreakpoints(
      aEntero(params.get('anchoMovil')) ?? Entradas.ANCHO_MOVIL_DEFECTO,
      aEntero(params.get('anchoApilado')) ?? Entradas.ANCHO_APILADO_DEFECTO
    );
  }

  /**
   * Genera los dos @container de entradas.css (colapsar a una columna, achicar padding)
   * con el ancho que se les pida. Van por acá y no por CSS con var() porque una condición
   * de @container NO acepta custom properties — la única forma de que el número sea
   * configurable es armar la regla entera en JS. Siempre corre (con los valores por
   * defecto si no vinieron por query param) para que sea la ÚNICA fuente de estos dos
   * breakpoints; entradas.css ya no los declara, así nunca conviven dos reglas para el
   * mismo quiebre pisándose entre sí de forma confusa.
   *
   * Las propiedades van con !important a propósito: este <style> es CSS plano, sin el
   * atributo "_ngcontent-ng-cXXX" que Angular agrega a cada regla del componente (View
   * Encapsulation emulada) — así que aunque este <style> se inserte después, pierde por
   * especificidad contra la regla base (no-condicional) de .modulo-compra-container, que
   * sí tiene ese atributo. Sin !important, cambiar el breakpoint no tendría ningún efecto.
   */
  private aplicarBreakpoints(anchoMovil: number, anchoApilado: number): void {
    this.estiloBreakpoints ??= document.createElement('style');
    this.estiloBreakpoints.textContent = `
      @container modulo (max-width: ${anchoMovil}px) {
        .columna-izquierda-contenido { min-width: 100% !important; padding: 10px 10px 10px 0 !important; }
      }
      @container modulo (max-width: ${anchoApilado}px) {
        .modulo-compra-container { flex-direction: column !important; align-items: center !important; gap: 30px !important; padding: 0px !important; }
        .panel-derecho { flex: none !important; width: 100% !important; }
      }
    `;
    if (!this.estiloBreakpoints.isConnected) {
      document.head.appendChild(this.estiloBreakpoints);
    }
  }

  /** Panel de "Horarios / Seguro de lluvia / Prioridad de ingreso": ver ?ocultarInfo en aplicarConfigDesdeUrl(). */
  ocultarInfoUtil = false;

  /**
   * Antes era texto fijo ("Abierto de 11:00 a 18:30 hs."): si el ADMIN cambiaba el
   * horario general desde "Días y Horarios" (ConfiguracionService), el módulo público
   * seguía mostrando el horario viejo. Ahora se trae de GET /api/configuracion/horario
   * (endpoint público, ver SecurityConfig). Es el horario GENERAL nada más — no el
   * horario especial por fecha puntual, que hoy no tiene endpoint público.
   * Null mientras carga o si falla la consulta: el renglón directamente no se muestra
   * en vez de arriesgarse a mostrar un horario que ya no es el real.
   */
  horario: { apertura: string; cierre: string; limiteCompra: string | null } | null = null;

  /** "45 minutos", "1 hora", "1 hora y 30 minutos"; null si no hay corte (0 minutos) o el dato no llegó. */
  private textoLapso(minutos: number | null | undefined): string | null {
    if (!minutos || minutos <= 0) return null;
    const horas = Math.floor(minutos / 60);
    const resto = minutos % 60;
    const partes: string[] = [];
    if (horas > 0) partes.push(`${horas} ${horas === 1 ? 'hora' : 'horas'}`);
    if (resto > 0) partes.push(`${resto} ${resto === 1 ? 'minuto' : 'minutos'}`);
    return partes.join(' y ');
  }

  private cargarHorarioGeneral(): void {
    this.configuracionService.getHorarioGeneral().pipe(
      map((h) => ({
        apertura: h.horaApertura.slice(0, 5),
        cierre: h.horaCierre.slice(0, 5),
        limiteCompra: this.textoLapso(h.minutosLimiteCompra),
      })),
      catchError(() => of(null))
    ).subscribe((horario) => {
      this.ngZone.run(() => {
        this.horario = horario;
        this.cdr.detectChanges();
      });
    });
  }

  /**
   * Embebido en un <iframe> del sitio del parque, en un origen distinto: el padre no
   * puede leer la altura del documento por política de mismo origen. En vez de eso,
   * avisamos la altura por postMessage cada vez que cambia (cambio de paso, error
   * mostrado, etc.) para que el sitio pueda ajustar el alto del iframe y no le quede
   * scroll propio. El snippet que tiene que poner el sitio para escuchar esto está en
   * el README, sección "Embeber el módulo /entradas en el sitio del parque".
   */
  private observadorAltura: ResizeObserver | null = null;

  ngOnInit(): void {
    this.aplicarConfigDesdeUrl();
    this.cargarHorarioGeneral();

    if (window.parent === window) return; // no está embebido, no hay a quién avisarle

    // document.documentElement/body NO sirven acá: styles.css los fija a height:100% (para el
    // resto de la app, que sí necesita llenar el viewport), así que su propio tamaño queda
    // pegado al viewport y nunca "crece" cuando el contenido lo desborda — el ResizeObserver
    // no dispara nunca más allá del primer aviso. El host de este componente (todo el módulo
    // /entradas) no tiene esa restricción: su alto sí sigue al contenido real.
    const elementoRaiz = this.elementRef.nativeElement;
    const avisarAltura = () => {
      const alto = elementoRaiz.scrollHeight;
      // "*" porque el origen del padre varía (sitio real, túnel de prueba, etc.) y acá
      // sólo viaja un número de píxeles, nada sensible.
      window.parent.postMessage({ type: Entradas.MENSAJE_ALTURA, alto }, '*');
    };

    this.observadorAltura = new ResizeObserver(avisarAltura);
    this.observadorAltura.observe(elementoRaiz);
    avisarAltura();
  }

  ngOnDestroy(): void {
    this.detenerVerificacion();
    this.ventanaPago = null;
    this.observadorAltura?.disconnect();
    this.estiloBreakpoints?.remove();
    // aplicarConfigDesdeUrl() pisa estos dos directo en body/:root (no vía ThemeService):
    // sin deshacerlos acá, navegar por SPA desde /entradas?fondo=...&radio=... hacia una
    // pantalla interna deja el fondo y el radio de esquinas contaminados fuera de esta ruta.
    document.body.style.backgroundColor = '';
    document.documentElement.style.removeProperty('--radius-lg');
  }

  procesandoPago: boolean = false;
  compraIdActual: number | null = null;
  codigoReservaActual: string | null = null;
  private montoTotalActual: number | null = null;
  pagoConfirmado: boolean = false;
  /**
   * Aviso al usuario durante el checkout. Reemplaza a los alert() nativos, que
   * rompían la contención visual del módulo embebido en el sitio del parque y
   * eran inconsistentes con el resto de la app, que muestra los errores inline.
   */
  aviso: string | null = null;
  /**
   * El DNI tipeado ya está registrado a nombre de otra persona (backend: mensaje con
   * prefijo "DNI_YA_REGISTRADO:"). Se muestra en vez del resumen normal, con la opción
   * de confirmar (reintenta con confirmarDniExistente=true) o volver a revisar los datos.
   */
  dniAConfirmar: string | null = null;
  /** Checkbox del cartel de confirmación: si además de confirmar "soy yo" quiere pisar los datos viejos con los recién tipeados. */
  actualizarDatosCliente = false;

  private suscripcionPago: Subscription | null = null;
  private ventanaPago: Window | null = null;

  /**
   * El cupón se aplica en el resumen (ya elegida la forma de pago), pero el
   * objeto vive acá porque tiene que sobrevivir a un cambio de forma de pago:
   * si es porcentual, el monto a descontar depende del subtotal vigente
   * (precio de lista o precio de grupo), que sólo se conoce en este nivel.
   */
  cuponAplicado: Cupon | null = null;

  compraAcumulada: ResumenCompraData = {
    fechaVisita: null,
    esRegalo: false,
    entradas: [],
    cuponCodigo: null,
    descuentoMonto: 0,
    subtotal: 0,
    subtotalLista: 0,
    descuentoGrupo: 0,
    total: 0,
    formaPago: 'MERCADO_PAGO',
    cliente: null,
    receptor: null
  };

  onFechaSeleccionada(fecha: Date | null): void {
    this.compraAcumulada.fechaVisita = fecha;
  }

  onEsRegaloCambio(esRegalo: boolean): void {
    this.compraAcumulada.esRegalo = esRegalo;
    if (esRegalo) {
      this.compraAcumulada.fechaVisita = null;
      // Un regalo tiene que estar pagado de antemano: si quedó en efectivo de un
      // paso anterior, el receptor terminaría pagando de su bolsillo lo que le
      // "regalaron" al llegar al parque.
      this.compraAcumulada.formaPago = 'MERCADO_PAGO';
    }
  }
  onFormaPagoCambio(metodo: FormaPagoType): void {
    this.compraAcumulada.formaPago = metodo;
    this.actualizarCotizacion();
  }

  private actualizarCotizacion(): void {
    const entradasPayload = this.compraAcumulada.entradas
      .filter(e => e.cantidad > 0)
      .map(e => ({
        tipoEntradaId: e.id || e.tipoEntradaId,
        cantidad: e.cantidad
      }));

    if (entradasPayload.length === 0) return;

    this.compraService.cotizar({
      formaPago: this.compraAcumulada.formaPago,
      entradas: entradasPayload
    }).subscribe({
      next: (res) => {
        this.ngZone.run(() => {
          this.compraAcumulada.subtotal = res.subtotal;
          this.compraAcumulada.descuentoGrupo = res.ahorro > 0 ? res.ahorro : 0;
          this.recalcularTotal();
          this.cdr.detectChanges();
        });
      },
      error: (err) => console.error('Error al cotizar el precio:', err)
    });
  }

  /** El cupón se aplica o quita desde el resumen; ver el comentario en `cuponAplicado`. */
  onCuponCambio(cupon: Cupon | null): void {
    this.cuponAplicado = cupon;
    this.compraAcumulada.cuponCodigo = cupon?.codigo ?? null;
    this.recalcularTotal();
    this.cdr.detectChanges();
  }

  /**
   * El precio por grupo NO es un descuento (ver resumen.html): cambia el subtotal
   * en sí. El cupón sí es un descuento real, y su monto se recalcula siempre
   * contra el subtotal vigente en ese momento — así, si el cupón es porcentual,
   * un cambio posterior de forma de pago (precio de lista ↔ precio de grupo)
   * no deja un monto de cupón calculado sobre una base que ya no corresponde.
   */
  private recalcularTotal(): void {
    const descuentoCupon = this.calcularDescuentoCupon(this.cuponAplicado, this.compraAcumulada.subtotal);
    this.compraAcumulada.descuentoMonto = descuentoCupon;
    this.compraAcumulada.total = Math.max(0, this.compraAcumulada.subtotal - descuentoCupon);
  }

  private calcularDescuentoCupon(cupon: Cupon | null, subtotal: number): number {
    if (!cupon || subtotal <= 0) return 0;
    if (cupon.porcentajeDescuento) return (subtotal * cupon.porcentajeDescuento) / 100;
    if (cupon.montoDescuento) return Math.min(cupon.montoDescuento, subtotal);
    return 0;
  }

  onPasoSiguiente(datosPaso: any): void {
    if (this.etapa === etapaCompra.SELECCION) {
      this.compraAcumulada = {
        ...this.compraAcumulada,
        ...datosPaso, // fechaVisita, esRegalo, entradas, subtotal (precio de lista actualizado)
        subtotalLista: datosPaso.subtotal,
        descuentoGrupo: 0, // se vuelve a definir según la forma de pago que se elija en el resumen
      };
      // No se toca cuponAplicado/cuponCodigo acá: si el usuario ya había aplicado un
      // cupón en el resumen y vuelve a editar la selección, el cupón se mantiene — sólo
      // se recalcula el monto contra el nuevo subtotal.
      this.recalcularTotal();
      this.etapa = etapaCompra.DATOS;
    } else if (this.etapa === etapaCompra.DATOS) {
      this.compraAcumulada.cliente = datosPaso.cliente;
      this.compraAcumulada.receptor = datosPaso.receptor ?? null;
      this.etapa = etapaCompra.RESUMEN;
    }
  }

  volverPasoAnterior(): void {
    if (this.etapa === etapaCompra.RESUMEN) {
      this.etapa = etapaCompra.DATOS;
    } else if (this.etapa === etapaCompra.DATOS) {
      this.etapa = etapaCompra.SELECCION;
    }
  }

  iniciarPagoMercadoPago(confirmarDniExistente: boolean = false, esOtraPersona: boolean = false): void {
    this.procesandoPago = true;
    this.aviso = null;
    this.dniAConfirmar = null;
    // No se resetea en el reintento confirmado: ahí es cuando hace falta leer lo que
    // el usuario tildó en el cartel (ver el checkbox más abajo en el HTML).
    if (!confirmarDniExistente) {
      this.actualizarDatosCliente = false;
    }

    // 1. Convertimos la fecha al formato YYYY-MM-DD que espera Spring Boot
    let fechaFormateada = null;
    if (this.compraAcumulada.fechaVisita) {
      const d = new Date(this.compraAcumulada.fechaVisita);
      fechaFormateada = d.toISOString().split('T')[0];
    }

    // 2. Mapeamos exactamente a la estructura del CompraRequestDTO del Backend
    const payloadBackend = {
      cliente: {
        dni: this.compraAcumulada.cliente?.dni,
        nombre: this.compraAcumulada.cliente?.nombre,
        apellido: this.compraAcumulada.cliente?.apellido,
        email: this.compraAcumulada.cliente?.email,
        telefono: this.compraAcumulada.cliente?.telefono,
        edad: this.compraAcumulada.cliente?.edad || null,
        localidad: this.compraAcumulada.cliente?.localidad || null
      },
      fecha: fechaFormateada,
      formaPago: this.compraAcumulada.formaPago,
      cuponCodigo: this.compraAcumulada.cuponCodigo || null,
      entradas: this.compraAcumulada.entradas.map((item: any) => ({
        tipoEntradaId: item.id || item.tipoEntradaId,
        cantidad: item.cantidad
      })),
      // Sólo va cuando es un regalo: el backend le manda un mail de aviso al receptor.
      receptor: this.compraAcumulada.esRegalo && this.compraAcumulada.receptor ? {
        nombre: this.compraAcumulada.receptor.nombre,
        email: this.compraAcumulada.receptor.email,
        dni: this.compraAcumulada.receptor.dni,
        telefono: this.compraAcumulada.receptor.telefono || null,
      } : null,
      confirmarDniExistente,
      // Sólo tiene efecto junto con confirmarDniExistente=true (ver checkbox del cartel).
      actualizarDatosCliente: confirmarDniExistente ? this.actualizarDatosCliente : false,
      esOtraPersona,
    };

    // 3. Invocamos al backend a través del CompraService
    this.compraService.iniciarCompraConPago(payloadBackend).subscribe({
      next: (res) => {
        this.compraIdActual = res.id;
        this.codigoReservaActual = res.codigoReserva;
        // El total del backend (con cupón aplicado), que es lo que cobra Mercado Pago.
        this.montoTotalActual = res.montoTotal;

        // EVALUAMOS LA ESTRATEGIA DEVUELTA POR EL BACKEND
        if (res.formaPago === 'MERCADO_PAGO' && res.initPoint) {
          const ancho = 1000;
          const alto = 700;
          const izquierda = (window.screen.width - ancho) / 2;
          const arriba = (window.screen.height - alto) / 2;

          // El botón sigue "procesando" mientras esperamos que el usuario complete el
          // pago en la ventana de Mercado Pago (se libera al cerrarla, al confirmarse o al fallar el pago).
          this.ventanaPago = window.open(
              res.initPoint,
              'MercadoPagoCheckout',
              `width=${ancho},height=${alto},top=${arriba},left=${izquierda},scrollbars=yes,status=yes`
          );

          if (!this.ventanaPago) {
            this.finalizarConAviso('No se pudo abrir la ventana de pago. Revisá que tu navegador no esté bloqueando ventanas emergentes e intentá de nuevo.');
            return;
          }

          this.iniciarMonitoreoPago();
        } else if (res.formaPago === 'EFECTIVO_BOLETERIA') {
          // Para efectivo no abre ventana popup; congela la reserva y muestra el comprobante
          this.procesandoPago = false;
          this.ngZone.run(() => {
            this.pagoConfirmado = true;
            this.cdr.detectChanges();
          });
        }
      },
      error: (err) => {
        console.error('Error al procesar la reserva:', err);
        const mensaje = typeof err?.error === 'string' ? err.error : null;
        const marcador = 'DNI_YA_REGISTRADO:';
        if (mensaje?.startsWith(marcador)) {
          this.ngZone.run(() => {
            this.procesandoPago = false;
            this.dniAConfirmar = mensaje.slice(marcador.length).trim();
            this.cdr.detectChanges();
          });
          return;
        }
        this.finalizarConAviso(mensaje ?? 'Ocurrió un error al procesar la reserva.');
      }
    });
  }

  /** El usuario confirmó que sí es la misma persona del DNI ya registrado: reintenta el mismo pedido. */
  confirmarDniYContinuar(): void {
    this.iniciarPagoMercadoPago(true);
  }

  /** El usuario aclaró que NO es la misma persona: se crea un cliente aparte con el mismo DNI. */
  noSoyYo(): void {
    this.iniciarPagoMercadoPago(false, true);
  }

  /** El usuario prefiere revisar los datos en vez de confirmar — probablemente un DNI mal tipeado. */
  corregirDatosPorDni(): void {
    this.dniAConfirmar = null;
    this.etapa = etapaCompra.DATOS;
  }

  /**
   * Consulta periódicamente si el pago se acreditó, mientras el usuario opera en la
   * ventana de Mercado Pago.
   *
   * Usa timer + switchMap en vez de setInterval por dos motivos: switchMap descarta la
   * consulta anterior si el backend tarda más que el intervalo (con setInterval se
   * encimaban), y take() le pone un techo — antes, si la compra quedaba en
   * PENDIENTE_PAGO y el usuario dejaba la pestaña abierta, seguía consultando para siempre.
   */
  private iniciarMonitoreoPago(): void {
    this.detenerVerificacion();

    this.suscripcionPago = timer(0, Entradas.INTERVALO_POLL_MS)
      .pipe(
        take(Entradas.MAX_CONSULTAS_POLL),
        switchMap(() => this.consultarEstadoPago())
      )
      .subscribe({
        next: (estado) => this.procesarEstadoPago(estado),
        complete: () => this.alAgotarseElMonitoreo(),
      });
  }

  private consultarEstadoPago(): Observable<string | null> {
    // El usuario cerró la ventana. Antes de darlo por perdido, se reconcilia una vez
    // directamente contra Mercado Pago: si el pago se acreditó pero el webhook nunca
    // llegó (por ejemplo, con el túnel de notificaciones caído), esto lo detecta igual
    // sin depender de esa notificación — es el caso real que motivó este chequeo.
    if (this.ventanaPago?.closed && !this.pagoConfirmado) {
      return this.verificarConMercadoPago();
    }
    if (!this.compraIdActual) {
      return of(null);
    }

    return this.compraService.obtenerEstadoCompra(this.compraIdActual).pipe(
      map((res) => res.estado),
      // Un error puntual de red no corta el monitoreo: se reintenta en el próximo tick.
      catchError(() => of(null))
    );
  }

  /** Le pregunta a Mercado Pago (no a nuestra propia DB) si el pago realmente se acreditó. */
  private verificarConMercadoPago(): Observable<string> {
    if (!this.compraIdActual) return of(Entradas.VENTANA_CERRADA);
    return this.compraService.verificarPago(this.compraIdActual).pipe(
      map((res) => (res.estado === 'APROBADO' || res.estado === 'CANCELADO') ? res.estado : Entradas.VENTANA_CERRADA),
      catchError(() => of(Entradas.VENTANA_CERRADA))
    );
  }

  private procesarEstadoPago(estado: string | null): void {
    if (estado === null) return;

    if (estado === Entradas.VENTANA_CERRADA) {
      this.detenerVerificacion();
      this.finalizarConAviso('La ventana de pago se cerró sin completarse. Podés intentarlo de nuevo cuando quieras.');
      return;
    }

    if (estado === 'APROBADO') {
      this.detenerVerificacion();
      this.cerrarVentanaPago();
      this.registrarConversion();
      this.ngZone.run(() => {
        this.pagoConfirmado = true;
        this.procesandoPago = false;
        this.cdr.detectChanges(); // Re-evalúa la plantilla HTML al instante
      });
      return;
    }

    if (estado === 'CANCELADO') {
      this.detenerVerificacion();
      this.cerrarVentanaPago();
      this.finalizarConAviso('El pago fue cancelado o rechazado. Podés intentarlo de nuevo.');
    }
  }

  /** Se agotaron los intentos sin novedades del backend: último intento, directo contra Mercado Pago. */
  private alAgotarseElMonitoreo(): void {
    if (this.pagoConfirmado || !this.compraIdActual) return;

    this.compraService.verificarPago(this.compraIdActual).subscribe({
      next: (res) => {
        if (res.estado === 'APROBADO') {
          this.cerrarVentanaPago();
          this.registrarConversion();
          this.ngZone.run(() => {
            this.pagoConfirmado = true;
            this.procesandoPago = false;
            this.cdr.detectChanges();
          });
        } else {
          this.finalizarConAviso(
            'Todavía no pudimos confirmar el pago. Si ya lo completaste vas a recibir el comprobante por mail; si no, podés reintentar.'
          );
        }
      },
      error: () => this.finalizarConAviso(
        'Todavía no pudimos confirmar el pago. Si ya lo completaste vas a recibir el comprobante por mail; si no, podés reintentar.'
      ),
    });
  }

  private registrarConversion(): void {
    if (!this.codigoReservaActual || this.montoTotalActual === null) return;
    this.analitica.registrarCompra({ codigoReserva: this.codigoReservaActual, montoTotal: this.montoTotalActual });
  }

  private finalizarConAviso(mensaje: string): void {
    this.ngZone.run(() => {
      this.procesandoPago = false;
      this.aviso = mensaje;
      this.cdr.detectChanges();
    });
  }

  /**
   * Cierra el popup de Mercado Pago una vez confirmado el resultado: la confirmación ya se
   * muestra en esta página, no hace falta dejar la pantalla de "pago completado" de MP abierta.
   * close() funciona aunque la ventana esté en otro origen, porque la abrimos nosotros.
   */
  private cerrarVentanaPago(): void {
    try {
      if (this.ventanaPago && !this.ventanaPago.closed) {
        this.ventanaPago.close();
      }
    } catch {
      // Sin acceso a la ventana: no es crítico, el usuario puede cerrarla a mano.
    }
    this.ventanaPago = null;
  }

  private detenerVerificacion(): void {
    this.suscripcionPago?.unsubscribe();
    this.suscripcionPago = null;
  }

  protected readonly etapaCompra = etapaCompra;
}
