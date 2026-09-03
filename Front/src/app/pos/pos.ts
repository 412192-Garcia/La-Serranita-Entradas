import { Component, HostListener, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { BoleteriaService, Reserva } from '../services/boleteria.service';
import { TipoEntradaService } from '../services/tipo-entrada.service';
import { CajaService, Caja } from '../services/caja.service';
import { PromocionService } from '../services/promocion.service';
import { ArticuloVarioService } from '../services/articulo-vario.service';
import { ConfiguracionService, DescuentoEfectivo } from '../services/configuracion.service';
import { PosCacheService } from '../services/pos-cache.service';
import { PayloadRetiroAporte, PayloadIngresoEntradas } from '../services/operaciones-pendientes.service';
import { aFechaHoraISO } from '../shared/fecha.util';
import { TipoEntrada } from '../models/tipo-entrada';
import { Promocion } from '../models/promocion';
import { ArticuloVario } from '../models/articulo-vario';
import { FilaArticuloCarrito, LineaEntradaFija } from '../models/venta-pos';
import { CabeceraInterna } from '../shared/cabecera-interna/cabecera-interna';
import { Spinner } from '../shared/spinner/spinner';
import { Modal } from '../shared/modal/modal';
import { AperturaCaja } from './apertura-caja/apertura-caja';
import { BarraCaja } from './barra-caja/barra-caja';
import { RetiroEfectivoModal } from '../shared/retiro-efectivo-modal/retiro-efectivo-modal';
import { IngresoEntradasModal } from './ingreso-entradas-modal/ingreso-entradas-modal';
import { CatalogoEntradas } from './catalogo-entradas/catalogo-entradas';
import { AgregarArticulo } from './agregar-articulo/agregar-articulo';
import { CarritoVenta, VentaPosConfirmada } from './carrito-venta/carrito-venta';
import { ComprobanteVenta } from './comprobante-venta/comprobante-venta';
import { ValidarAnticipadaPos } from './validar-anticipada-pos/validar-anticipada-pos';
import { TourStep } from '../shared/tour/tour';
import { DetectorEscaneoDni, extraerDniDeEscaneo } from '../shared/escaner-dni.util';

/** Los targets sólo existen con una caja abierta (antes de eso la pantalla es sólo el
 * formulario de apertura) — asumible porque para llegar a tocar "Tutorial" acá ya hace
 * falta haber abierto la caja. */
const PASOS_TUTORIAL: TourStep[] = [
  {
    selector: '[data-tour="barra-caja"]',
    titulo: 'Tu caja',
    texto: 'Desde acá hacés un Retiro/Aporte de efectivo o reponés el talonario. Para validar el ingreso de alguien que ya tiene una entrada, escaneá su DNI en cualquier momento: se abre acá mismo la lista de sus anticipadas para validarlas sin salir del POS.',
  },
  {
    selector: '[data-tour="catalogo"]',
    titulo: 'Catálogo de entradas',
    texto: 'Tocá la cantidad de cada tipo para sumarla a la venta.',
  },
  {
    selector: '[data-tour="agregar-articulo"]',
    titulo: 'Artículos varios',
    texto: 'Souvenirs y otros artículos que no son entradas se agregan desde acá.',
  },
  {
    selector: '[data-tour="carrito"]',
    titulo: 'Cobrar',
    texto: 'Revisá el resumen, elegí la forma de pago y tocá Cobrar para cerrar la venta.',
  },
];

@Component({
  selector: 'app-pos',
  imports: [
    CabeceraInterna,
    Spinner,
    Modal,
    AperturaCaja,
    BarraCaja,
    RetiroEfectivoModal,
    IngresoEntradasModal,
    CatalogoEntradas,
    AgregarArticulo,
    CarritoVenta,
    ComprobanteVenta,
    ValidarAnticipadaPos,
  ],
  templateUrl: './pos.html',
  styleUrl: './pos.css',
})
export class Pos implements OnInit, OnDestroy {
  private boleteriaService = inject(BoleteriaService);
  private tipoEntradaService = inject(TipoEntradaService);
  private cajaService = inject(CajaService);
  private promocionService = inject(PromocionService);
  private articuloVarioService = inject(ArticuloVarioService);
  private configuracionService = inject(ConfiguracionService);
  private cache = inject(PosCacheService);

  readonly pasosTutorial = PASOS_TUTORIAL;

  // ---------- Caja: sin una abierta no se puede vender ----------
  cargandoCaja = signal(true);
  /** Caja abierta del boletero; null = no tiene ninguna en curso (hay que abrir una — o pedirle a un admin que cierre la que ya está abierta). */
  caja = signal<Caja | null>(null);

  mostrarRetiro = signal(false);
  mostrarIngresoEntradas = signal(false);

  tiposEntrada = signal<TipoEntrada[]>([]);
  cargando = signal(true);
  errorCarga = signal<string | null>(null);
  cantidades = signal<Record<number, number>>({});

  promociones = signal<Promocion[]>([]);
  articulosCatalogo = signal<ArticuloVario[]>([]);
  articulosCarrito = signal<FilaArticuloCarrito[]>([]);
  /** Escalones de precio por grupo: se cachean para poder cotizar sin conexión (ver calculo-precio.util). */
  descuentosEfectivo = signal<DescuentoEfectivo[]>([]);

  /** Venta recién cerrada: mientras esté seteada se muestra el comprobante en pantalla. */
  ultimaVenta = signal<VentaPosConfirmada | null>(null);

  /** DNI recién escaneado: mientras esté seteado, el panel de anticipadas tapa (sin destruir)
   * el catálogo/carrito para validar los ingresos de esa persona sin salir del POS. */
  dniAnticipada = signal<string | null>(null);

  /** DNI escaneado que quedó a la espera de que el boletero decida si dejar la compra en curso
   * (ver el diálogo en pos.html). Null = no hay decisión pendiente. */
  escaneoPendiente = signal<string | null>(null);

  /** Reserva RESERVADO_EFECTIVO cargada en el carrito desde el panel de anticipadas: al cobrarla
   * se cierra esa reserva en vez de crear una venta nueva. Null en una venta de puerta común. */
  compraReservada = signal<Reserva | null>(null);
  /** Líneas de la reserva cargada que no se editan desde el carrito (extras, tipos fuera del catálogo). */
  entradasReserva = signal<LineaEntradaFija[]>([]);

  ngOnInit(): void {
    // Cada carga se cachea al salir bien, y al fallar cae al último snapshot conocido: sin
    // esto un corte de señal deja el POS con el catálogo vacío y sin poder vender nada.
    this.tipoEntradaService.getTiposEntrada().subscribe({
      next: (ts) => {
        // Los extras (ej. menú almuerzo) no se venden en boletería, sólo en la compra online:
        // el catálogo del POS ya se carga filtrado a ENTRADA.
        const soloEntradas = ts.filter((t) => t.activo && t.tipo === 'ENTRADA');
        this.tiposEntrada.set(soloEntradas);
        this.cache.guardar('tiposEntrada', soloEntradas);
        this.cargando.set(false);
      },
      error: (err) => {
        console.error('Error al cargar los tipos de entrada:', err);
        const cacheados = this.cache.leer<TipoEntrada[]>('tiposEntrada');
        if (cacheados?.length) {
          this.tiposEntrada.set(cacheados);
        } else {
          this.errorCarga.set('No se pudieron cargar las entradas. Recargá la página.');
        }
        this.cargando.set(false);
      },
    });

    this.cajaService.getActual().subscribe({
      next: (c) => {
        this.actualizarCaja(c);
        this.cargandoCaja.set(false);
      },
      error: (err) => {
        console.error('Error al consultar la caja:', err);
        // Sólo sirve si la caja cacheada seguía abierta: si el último snapshot ya estaba
        // cerrada, mostrar la pantalla de apertura (que igual va a pedir conexión) es correcto.
        const cacheada = this.cache.leer<Caja>('caja');
        if (cacheada && !cacheada.fechaCierre) {
          this.caja.set(cacheada);
        }
        this.cargandoCaja.set(false);
      },
    });

    this.promocionService.getPromociones().subscribe({
      next: (ps) => {
        this.promociones.set(ps);
        this.cache.guardar('promociones', ps);
      },
      error: (err) => {
        console.error('Error al cargar las promociones:', err);
        this.promociones.set(this.cache.leer<Promocion[]>('promociones') ?? []);
      },
    });

    this.articuloVarioService.getArticulos().subscribe({
      next: (as) => {
        this.articulosCatalogo.set(as);
        this.cache.guardar('articulos', as);
      },
      error: (err) => {
        console.error('Error al cargar los artículos varios:', err);
        this.articulosCatalogo.set(this.cache.leer<ArticuloVario[]>('articulos') ?? []);
      },
    });

    this.configuracionService.getDescuentosEfectivo().subscribe({
      next: (ds) => {
        this.descuentosEfectivo.set(ds);
        this.cache.guardar('descuentosEfectivo', ds);
      },
      error: (err) => {
        console.error('Error al cargar los precios por grupo:', err);
        this.descuentosEfectivo.set(this.cache.leer<DescuentoEfectivo[]>('descuentosEfectivo') ?? []);
      },
    });
  }

  /** Ver ngOnInit: mismo criterio, cualquier cambio a la caja tiene que quedar cacheado, no
   *  sólo la carga inicial — si no, un reload offline a mitad de turno muestra "Abrir caja" o
   *  totales viejos aunque la caja siga abierta de verdad del lado del servidor. */
  private actualizarCaja(c: Caja | null): void {
    this.caja.set(c);
    this.cache.guardar('caja', c);
  }

  onCajaAbierta(c: Caja): void {
    this.actualizarCaja(c);
  }

  onRetiroRegistrado(c: Caja): void {
    this.actualizarCaja(c);
    this.mostrarRetiro.set(false);
  }

  /** Se encoló sin conexión: se refleja en la caja local para que el boletero vea el movimiento igual. */
  onRetiroEncolado(mov: PayloadRetiroAporte): void {
    const actual = this.caja();
    if (actual) {
      const signo = mov.tipo === 'APORTE' ? -1 : 1;
      this.actualizarCaja({
        ...actual,
        totalRetiros: actual.totalRetiros + signo * mov.monto,
        retiros: [
          ...actual.retiros,
          { id: 0, monto: mov.monto, motivo: mov.motivo, tipo: mov.tipo, fecha: aFechaHoraISO() },
        ],
      });
    }
    this.mostrarRetiro.set(false);
  }

  onIngresoEntradasRegistrado(c: Caja): void {
    this.actualizarCaja(c);
    this.mostrarIngresoEntradas.set(false);
  }

  /** Ver onRetiroEncolado. */
  onIngresoEntradasEncolado(mov: PayloadIngresoEntradas): void {
    const actual = this.caja();
    if (actual) {
      const signo = mov.tipo === 'RETIRO' ? -1 : 1;
      this.actualizarCaja({
        ...actual,
        totalIngresosEntradas: actual.totalIngresosEntradas + signo * mov.cantidad,
        ingresosEntradas: [
          ...actual.ingresosEntradas,
          { id: 0, cantidad: mov.cantidad, motivo: mov.motivo ?? null, tipo: mov.tipo, fecha: aFechaHoraISO() },
        ],
      });
    }
    this.mostrarIngresoEntradas.set(false);
  }

  onArticuloAgregado(fila: FilaArticuloCarrito): void {
    this.articulosCarrito.update((c) => [...c, fila]);
  }

  onQuitarArticulo(index: number): void {
    this.articulosCarrito.update((c) => c.filter((_, i) => i !== index));
  }

  onQuitarEntrada(tipoEntradaId: number): void {
    this.cantidades.update((c) => ({ ...c, [tipoEntradaId]: 0 }));
  }

  onLimpiar(): void {
    this.cantidades.set({});
    this.articulosCarrito.set([]);
    // Vaciar el carrito también cancela el cobro de una reserva en curso (la reserva no se
    // tocó del lado del servidor: sigue pendiente, se puede volver a escanear).
    this.compraReservada.set(null);
    this.entradasReserva.set([]);
  }

  onVentaRegistrada(venta: VentaPosConfirmada): void {
    this.ultimaVenta.set(venta);
    // huboVentaDolares es lo único de la caja que el modal de cierre necesita en vivo (para
    // decidir si mostrar el campo de dólares contados): al ser un booleano, no un monto, es
    // seguro parchearlo localmente sin pisar el resto de los totales (que siguen ocultos
    // mientras la caja sigue abierta, por diseño anti-trampa).
    if (venta.pagoEnDolares) {
      const actual = this.caja();
      if (actual) this.actualizarCaja({ ...actual, huboVentaDolares: true });
    }
  }

  /** Cierra el comprobante y deja la pantalla lista para el próximo cliente. */
  nuevaVenta(): void {
    this.ultimaVenta.set(null);
    this.compraReservada.set(null);
    this.onLimpiar();
  }

  // ---------- Escaneo de DNI de fondo: reemplaza el viejo botón "Validar reserva" ----------
  // Al escanear el DNI de alguien que ya tiene una entrada, se abre acá mismo el panel de
  // anticipadas (ver dniAnticipada / ValidarAnticipadaPos) en vez de navegar a Control de
  // Accesos: el boletero valida o cobra sin salir del POS.
  private detectorEscaneo = new DetectorEscaneoDni((escaneo) => {
    // Con un modal de retiro/ingreso abierto no se hace nada: ese DNI se escanea de nuevo
    // cuando se cierre el modal.
    if (this.mostrarRetiro() || this.mostrarIngresoEntradas()) return;
    const dni = extraerDniDeEscaneo(escaneo);
    // Si hay una compra a medio cargar (y no se está viendo un comprobante), primero se
    // pregunta: pasar al panel descarta ese carrito.
    if (!this.ultimaVenta() && this.hayCarritoEnCurso()) {
      this.escaneoPendiente.set(dni);
      return;
    }
    this.dniAnticipada.set(dni);
  });

  /** El boletero eligió dejar la compra en curso e ir al panel de anticipadas de ese DNI. */
  confirmarIrAAnticipadas(): void {
    const dni = this.escaneoPendiente();
    this.escaneoPendiente.set(null);
    if (dni === null) return;
    this.compraReservada.set(null);
    this.onLimpiar();
    this.dniAnticipada.set(dni);
  }

  /** Una anticipada APROBADO se validó desde el panel: se cierra y el POS queda limpio. */
  onAnticipadaValidada(): void {
    this.dniAnticipada.set(null);
    this.nuevaVenta();
  }

  /** Una anticipada RESERVADO_EFECTIVO: se carga en el carrito para cobrarla como venta normal.
   * Las entradas que están en el catálogo del POS quedan editables; los extras y cualquier tipo
   * fuera del catálogo se cargan fijos (se cobran igual, pero sin poder cambiarlos). */
  onCargarAnticipada(reserva: Reserva): void {
    const idsCatalogo = new Set(this.tiposEntrada().map((t) => t.id));
    const cantidades: Record<number, number> = {};
    const fijas: LineaEntradaFija[] = [];
    const articulos: FilaArticuloCarrito[] = [];
    for (const d of reserva.detalles ?? []) {
      if (d.tipoEntrada) {
        if (d.tipoEntrada.tipo === 'ENTRADA' && idsCatalogo.has(d.tipoEntrada.id)) {
          cantidades[d.tipoEntrada.id] = d.cantidad;
        } else {
          fijas.push({
            tipoEntradaId: d.tipoEntrada.id,
            nombre: d.tipoEntrada.nombre,
            precioUnitario: d.tipoEntrada.precio,
            cantidad: d.cantidad,
          });
        }
      } else if (d.articuloVario || d.descripcionLibre) {
        articulos.push({
          articuloVarioId: d.articuloVario?.id ?? null,
          descripcionLibre: d.descripcionLibre,
          nombre: d.articuloVario?.nombre ?? d.descripcionLibre ?? 'Artículo',
          precioUnitario: d.precioUnitario ?? 0,
          cantidad: d.cantidad,
        });
      }
    }
    this.cantidades.set(cantidades);
    this.entradasReserva.set(fijas);
    this.articulosCarrito.set(articulos);
    this.compraReservada.set(reserva);
    this.dniAnticipada.set(null);
  }

  @HostListener('window:keydown', ['$event'])
  onKeydownGlobal(event: KeyboardEvent): void {
    this.detectorEscaneo.procesarTecla(event);
  }

  ngOnDestroy(): void {
    this.detectorEscaneo.destruir();
  }

  private hayCarritoEnCurso(): boolean {
    return Object.values(this.cantidades()).some((c) => c > 0) || this.articulosCarrito().length > 0;
  }
}
