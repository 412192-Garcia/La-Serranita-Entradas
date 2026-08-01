import {ChangeDetectorRef, Component, NgZone, OnDestroy} from '@angular/core';
import { Calendario } from '../calendario/calendario';
import { SeleccionEntradas } from '../seleccion-entradas/seleccion-entradas';
import { FormCliente } from '../form-cliente/form-cliente';
import { Resumen } from '../resumen/resumen';
import { CompraService } from '../Services/compra.service';
import { PagoExitoso } from '../resultado-pago/pago-exitoso/pago-exitoso';
import {FormaPagoType, ResumenCompraData} from "../models/compra";
import { Cupon } from '../models/cupon';
import { LucideClock, LucideCloudRain, LucideTriangleAlert } from '@lucide/angular';
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
    Calendario,
    SeleccionEntradas,
    FormCliente,
    Resumen,
    PagoExitoso,
    LucideClock,
    LucideCloudRain,
    LucideTriangleAlert
  ],
  templateUrl: './entradas.html',
  styleUrl: './entradas.css',
})
export class Entradas implements OnDestroy {
  /** Cada cuánto se le pregunta al backend si el pago se acreditó. */
  private static readonly INTERVALO_POLL_MS = 3000;
  /** Techo de consultas (~10 min): pasado eso se deja de insistir. */
  private static readonly MAX_CONSULTAS_POLL = 200;
  /** Estado sintético para distinguir "cerró la ventana" de un estado real del backend. */
  private static readonly VENTANA_CERRADA = 'VENTANA_CERRADA';

  etapa: etapaCompra = etapaCompra.SELECCION;

  // Inyectamos únicamente CompraService
  constructor(private compraService: CompraService,
  private cdr: ChangeDetectorRef,
              private ngZone: NgZone) {}

  ngOnDestroy(): void {
    this.detenerVerificacion();
    this.ventanaPago = null;
  }

  procesandoPago: boolean = false;
  compraIdActual: number | null = null;
  codigoReservaActual: string | null = null;
  pagoConfirmado: boolean = false;
  /**
   * Aviso al usuario durante el checkout. Reemplaza a los alert() nativos, que
   * rompían la contención visual del módulo embebido en el sitio del parque y
   * eran inconsistentes con el resto de la app, que muestra los errores inline.
   */
  aviso: string | null = null;

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

  iniciarPagoMercadoPago(): void {
    this.procesandoPago = true;
    this.aviso = null;

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
    };

    // 3. Invocamos al backend a través del CompraService
    this.compraService.iniciarCompraConPago(payloadBackend).subscribe({
      next: (res) => {
        this.compraIdActual = res.id;
        this.codigoReservaActual = res.codigoReserva;

        // EVALUAMOS LA ESTRATEGIA DEVUELTA POR EL BACKEND
        if (res.formaPago === 'MERCADO_PAGO' && res.initPoint) {
          const ancho = 500;
          const alto = 650;
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
        this.finalizarConAviso(
          typeof err?.error === 'string' ? err.error : 'Ocurrió un error al procesar la reserva.'
        );
      }
    });
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
      this.ngZone.run(() => {
        this.pagoConfirmado = true;
        this.procesandoPago = false;
        this.cdr.detectChanges(); // Re-evalúa la plantilla HTML al instante
      });
      return;
    }

    if (estado === 'CANCELADO') {
      this.detenerVerificacion();
      this.finalizarConAviso('El pago fue cancelado o rechazado. Podés intentarlo de nuevo.');
    }
  }

  /** Se agotaron los intentos sin novedades del backend: último intento, directo contra Mercado Pago. */
  private alAgotarseElMonitoreo(): void {
    if (this.pagoConfirmado || !this.compraIdActual) return;

    this.compraService.verificarPago(this.compraIdActual).subscribe({
      next: (res) => {
        if (res.estado === 'APROBADO') {
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

  private finalizarConAviso(mensaje: string): void {
    this.ngZone.run(() => {
      this.procesandoPago = false;
      this.aviso = mensaje;
      this.cdr.detectChanges();
    });
  }

  private detenerVerificacion(): void {
    this.suscripcionPago?.unsubscribe();
    this.suscripcionPago = null;
  }

  protected readonly etapaCompra = etapaCompra;
}
