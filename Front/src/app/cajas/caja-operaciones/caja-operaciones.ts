import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { ToastService } from '../../shared/toast/toast.service';
import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CajaDetalleAbierta, CajaService, OperacionCaja, etiquetaTipoOperacion } from '../../services/caja.service';
import { BoleteriaService, Reserva, VentaPosRequest } from '../../services/boleteria.service';
import { TipoEntradaService } from '../../services/tipo-entrada.service';
import { ArticuloVarioService } from '../../services/articulo-vario.service';
import { TipoEntrada } from '../../models/tipo-entrada';
import { ArticuloVario } from '../../models/articulo-vario';
import { FilaArticuloCarrito } from '../../models/venta-pos';
import { FormaPagoPos } from '../../models/compra';
import { FORMAS_PAGO } from '../../models/forma-pago';
import { Spinner } from '../../shared/spinner/spinner';
import { PesosPipe } from '../../shared/pesos.pipe';
import { AgregarArticulo } from '../../pos/agregar-articulo/agregar-articulo';
import { RetiroEfectivoModal } from '../../shared/retiro-efectivo-modal/retiro-efectivo-modal';
import { IngresoEntradasModal } from '../../pos/ingreso-entradas-modal/ingreso-entradas-modal';
import { LucideShoppingCart, LucideArrowDownRight, LucideArrowUpRight, LucideTicketPlus, LucideTicketMinus, LucideTicketCheck, LucideEllipsisVertical } from '@lucide/angular';
import { AtrasService, cerrarConAtrasEnCelular } from '../../shared/modal/atras.service';

interface LineaEdicion {
  tipoEntradaId: number | null;
  cantidad: number;
}

type FiltroTipoOperacion = 'TODAS' | 'VENTAS' | 'MOVIMIENTOS';

const OPERACIONES_POR_PAGINA = 15;

/**
 * Detalle de una caja para el admin (ADMIN-only, gateado en SecurityConfig): a diferencia del
 * detalle de una caja ya cerrada (ver ResumenCierre), funciona con la caja todavía ABIERTA —
 * para poder encontrar y corregir una venta mal cargada, o cargar una que le faltó al boletero,
 * mientras sigue trabajando. Se muestra igual que ResumenCierre: como contenido desplegado
 * debajo de la fila en la tabla de "Cajas abiertas ahora" (ver cajas.html), no como un modal aparte.
 */
import { FichaCompraModal } from '../../reservas/ficha-compra-modal/ficha-compra-modal';
import { CorregirVenta, VentaACorregir } from '../../shared/corregir-venta/corregir-venta';
import { Modal } from '../../shared/modal/modal';
import { FacturaCompraModal } from '../../shared/factura-compra-modal/factura-compra-modal';
import { DialogoConfirmacion } from '../../shared/confirmacion/dialogo-confirmacion';
import { FacturaService } from '../../services/factura.service';
@Component({
  selector: 'app-caja-operaciones',
  imports: [
    PesosPipe,
    DatePipe,
    NgTemplateOutlet,
    FormsModule,
    Spinner,
    AgregarArticulo,
    RetiroEfectivoModal,
    IngresoEntradasModal,
    FichaCompraModal,
    CorregirVenta,
    Modal,
    FacturaCompraModal,
    DialogoConfirmacion,
    LucideShoppingCart,
    LucideArrowDownRight,
    LucideArrowUpRight,
    LucideTicketPlus,
    LucideTicketMinus,
    LucideTicketCheck,
    LucideEllipsisVertical,
  ],
  templateUrl: './caja-operaciones.html',
  styleUrl: './caja-operaciones.css',
})
export class CajaOperaciones implements OnInit {
  /** En celular: la venta cuya hoja de acciones (⋮) está abierta. */
  menuOperacionId = signal<number | null>(null);
  private atrasMenuOperacion = cerrarConAtrasEnCelular(
    () => this.menuOperacionId() !== null,
    () => this.menuOperacionId.set(null),
  );

  private cajaService = inject(CajaService);
  private toast = inject(ToastService);
  private boleteriaService = inject(BoleteriaService);
  private tipoEntradaService = inject(TipoEntradaService);
  private articuloVarioService = inject(ArticuloVarioService);

  cajaId = input.required<number>();

  /** Se canceló, editó o agregó una venta, o se cargó un movimiento: el padre refresca los totales de "Cajas abiertas ahora". */
  cambios = output<void>();

  readonly etiquetaTipoOperacion = etiquetaTipoOperacion;
  readonly formasPago = FORMAS_PAGO;

  detalle = signal<CajaDetalleAbierta | null>(null);
  cargando = signal(false);
  error = signal<string | null>(null);

  /** El detalle ya trae TODAS las operaciones de la caja (un turno, dataset acotado): pagina acá
   * mismo en el navegador en vez de pedirle al backend, que no tiene sentido para algo que ya
   * llegó entero. */
  paginaActual = signal(0);
  /** Ventas y movimientos (retiros/aportes/entradas físicas) mezclados en una sola lista cronológica
   * hacía difícil encontrar una venta puntual entre medio de todo lo demás. */
  filtroTipo = signal<FiltroTipoOperacion>('TODAS');

  totalVendido = computed(() => {
    const d = this.detalle();
    if (!d) return 0;
    return d.totalVentasEfectivo + d.totalVentasTarjeta + d.totalVentasQr;
  });
  operacionesFiltradas = computed(() => {
    const ops = this.detalle()?.operaciones ?? [];
    const filtro = this.filtroTipo();
    // Una anticipada validada no es ni una venta de esta caja (no cobró nada acá) ni un
    // movimiento de efectivo/talonario: sólo aparece en "Todas", no en estos dos filtros.
    if (filtro === 'VENTAS') return ops.filter((o) => o.tipo === 'VENTA');
    if (filtro === 'MOVIMIENTOS') return ops.filter((o) => o.tipo === 'RETIRO' || o.tipo === 'APORTE' || o.tipo === 'INGRESO_ENTRADAS' || o.tipo === 'RETIRO_ENTRADAS');
    return ops;
  });
  operacionesPagina = computed(() => {
    const inicio = this.paginaActual() * OPERACIONES_POR_PAGINA;
    return this.operacionesFiltradas().slice(inicio, inicio + OPERACIONES_POR_PAGINA);
  });
  totalPaginas = computed(() => Math.max(1, Math.ceil(this.operacionesFiltradas().length / OPERACIONES_POR_PAGINA)));

  cancelandoId = signal<number | null>(null);

  /** Sólo se cargan la primera vez que hace falta (al abrir un formulario de venta). */
  private catalogosCargados = false;
  tiposEntrada = signal<TipoEntrada[]>([]);
  catalogoArticulos = signal<ArticuloVario[]>([]);

  // ---------- "Nueva venta" (modal). Editar usa el formulario de corrección (ver editandoVenta). ----------
  agregandoVenta = signal(false);
  formularioVentaAbierto = computed(() => this.agregandoVenta());
  cargandoFormulario = signal(false);
  guardandoFormulario = signal(false);
  errorFormulario = signal<string | null>(null);
  formaPagoEdicion = signal<FormaPagoPos>('EFECTIVO_BOLETERIA');
  lineasEdicion = signal<LineaEdicion[]>([]);
  articulosEdicion = signal<FilaArticuloCarrito[]>([]);

  // ---------- Agregar un movimiento de caja (retiro/aporte o entradas físicas) ----------
  mostrarRetiro = signal(false);
  mostrarIngresoEntradas = signal(false);
  readonly facturacionHabilitada = inject(FacturaService).habilitada;

  /** Mismo criterio que el resumen del cierre: con la facturación prendida, o si igual hay algo facturado. */
  readonly mostrarFacturado = computed(() => {
    const d = this.detalle();
    if (!d) return false;
    return this.facturacionHabilitada() || d.totalFacturado !== 0 || d.facturasSinEmitir > 0;
  });

  ngOnInit(): void {
    this.cargarOperaciones();
  }

  /** Venta abierta en "Ver detalle". */
  fichaDe = signal<number | null>(null);

  /** La factura de una venta: se abre desde "Ver detalle", en lugar del detalle. */
  facturaDe = signal<Reserva | null>(null);
  private atras = inject(AtrasService);

  pasarAFactura(reserva: Reserva): void {
    this.fichaDe.set(null);
    setTimeout(() => this.atras.cuandoTermineDeCerrar(() => this.facturaDe.set(reserva)));
  }

  elegirFiltroTipo(filtro: FiltroTipoOperacion): void {
    this.filtroTipo.set(filtro);
    this.paginaActual.set(0);
  }

  paginaAnterior(): void {
    this.paginaActual.update((p) => Math.max(0, p - 1));
  }

  paginaSiguiente(): void {
    this.paginaActual.update((p) => Math.min(this.totalPaginas() - 1, p + 1));
  }

  /** `silencioso`: sin spinner, así lo que está abierto (las ventas) no se rearma. */
  private cargarOperaciones(silencioso = false): void {
    if (!silencioso) this.cargando.set(true);
    this.error.set(null);
    this.cajaService.obtenerOperaciones(this.cajaId()).subscribe({
      next: (d) => {
        this.detalle.set(d);
        if (!silencioso) this.paginaActual.set(0);
        this.cargando.set(false);
      },
      error: (err) => {
        console.error('Error al cargar las operaciones de la caja:', err);
        this.error.set('No se pudo cargar el detalle de esta caja.');
        this.cargando.set(false);
      },
    });
  }

  /** Venta que se está por cancelar: mientras no sea null está abierto el diálogo que pide el motivo. */
  ventaACancelar = signal<OperacionCaja | null>(null);
  /** Opcional: si se escribe, queda en la venta y se ve en Reportes > Cancelaciones. */
  motivoCancelacion = signal('');

  cancelar(op: OperacionCaja): void {
    if (op.compraId === null) return;
    this.motivoCancelacion.set('');
    this.ventaACancelar.set(op);
  }

  cerrarCancelacion(): void {
    if (this.cancelandoId() !== null) return;
    this.ventaACancelar.set(null);
  }

  confirmarCancelacion(): void {
    const op = this.ventaACancelar();
    if (!op || op.compraId === null) return;
    const compraId = op.compraId;
    this.cancelandoId.set(compraId);
    this.boleteriaService.cancelarVenta(compraId, this.motivoCancelacion().trim()).subscribe({
      next: () => {
        this.cancelandoId.set(null);
        this.ventaACancelar.set(null);
        this.toast.exito('Venta cancelada.');
        this.cargarOperaciones();
        this.cambios.emit();
      },
      error: (err) => {
        console.error('Error al cancelar la venta:', err);
        this.toast.error(typeof err?.error === 'string' && err.error ? err.error : 'No se pudo cancelar la venta. Reintentá en un rato.');
        this.cancelandoId.set(null);
      },
    });
  }

  private cargarCatalogos(): void {
    if (this.catalogosCargados) return;
    this.catalogosCargados = true;
    this.tipoEntradaService.getTiposEntrada().subscribe({
      next: (tipos) => this.tiposEntrada.set(tipos.filter((t) => t.tipo === 'ENTRADA' && t.activo)),
      error: (err) => console.error('Error al cargar los tipos de entrada:', err),
    });
    this.articuloVarioService.getArticulos().subscribe({
      next: (articulos) => this.catalogoArticulos.set(articulos),
      error: (err) => console.error('Error al cargar el catálogo de artículos:', err),
    });
  }

  /** Abre el formulario vacío para cargar una venta que le faltó registrar al boletero. */
  iniciarAgregarVenta(): void {
    this.cargarCatalogos();
    this.agregandoVenta.set(true);
    this.formaPagoEdicion.set('EFECTIVO_BOLETERIA');
    this.lineasEdicion.set([]);
    this.articulosEdicion.set([]);
    this.errorFormulario.set(null);
  }

  /** Venta abierta en "Editar" (modal con el mismo formulario de corrección que el boletero). */
  editandoVenta = signal<VentaACorregir | null>(null);
  cargandoEdicionId = signal<number | null>(null);

  iniciarEdicion(op: OperacionCaja): void {
    if (op.compraId === null) return;
    this.cargandoEdicionId.set(op.compraId);
    this.boleteriaService.obtenerFicha(op.compraId).subscribe({
      next: (f) => {
        this.cargandoEdicionId.set(null);
        const c = f.compra;
        this.editandoVenta.set({
          compraId: c.id,
          codigoReserva: c.codigoReserva,
          montoTotal: c.montoTotal,
          formaPago: c.formaPago ?? 'EFECTIVO_BOLETERIA',
          formaPagoSecundaria: f.formaPagoSecundaria,
          montoFormaPagoSecundaria: f.montoFormaPagoSecundaria,
          corregibleHasta: null,
          lineas: (c.detalles ?? []).map((d) => ({
            cantidad: d.cantidad,
            nombre: d.tipoEntrada?.nombre ?? d.articuloVario?.nombre ?? d.descripcionLibre ?? 'Artículo',
            precioUnitario: d.precioUnitario ?? d.tipoEntrada?.precio ?? null,
            entrada: d.tipoEntrada?.tipo === 'ENTRADA',
            tipoEntradaId: d.tipoEntrada?.id ?? null,
            articuloVarioId: d.articuloVario?.id ?? null,
          })),
        });
      },
      error: () => {
        this.cargandoEdicionId.set(null);
        this.toast.error('No se pudo cargar esta venta.');
      },
    });
  }

  alEditarVenta(): void {
    this.editandoVenta.set(null);
    this.toast.exito('Venta corregida.');
    this.cargarOperaciones(true);
    this.cambios.emit();
  }

  cancelarFormulario(): void {
    this.agregandoVenta.set(false);
    this.lineasEdicion.set([]);
    this.articulosEdicion.set([]);
    this.errorFormulario.set(null);
  }

  agregarLinea(): void {
    this.lineasEdicion.update((ls) => [...ls, { tipoEntradaId: null, cantidad: 1 }]);
  }

  quitarLinea(index: number): void {
    this.lineasEdicion.update((ls) => ls.filter((_, i) => i !== index));
  }

  actualizarTipoLinea(index: number, tipoEntradaId: string): void {
    const id = tipoEntradaId ? Number(tipoEntradaId) : null;
    this.lineasEdicion.update((ls) => ls.map((l, i) => (i === index ? { ...l, tipoEntradaId: id } : l)));
  }

  actualizarCantidadLinea(index: number, cantidad: number): void {
    const valor = Math.max(1, Math.floor(cantidad) || 1);
    this.lineasEdicion.update((ls) => ls.map((l, i) => (i === index ? { ...l, cantidad: valor } : l)));
  }

  onArticuloAgregado(fila: FilaArticuloCarrito): void {
    this.articulosEdicion.update((a) => [...a, fila]);
  }

  quitarArticuloEdicion(index: number): void {
    this.articulosEdicion.update((a) => a.filter((_, i) => i !== index));
  }

  guardarFormulario(): void {
    const lineas = this.lineasEdicion();
    const articulos = this.articulosEdicion();
    if (lineas.length === 0 && articulos.length === 0) {
      this.errorFormulario.set('Agregá al menos una entrada o un artículo.');
      return;
    }
    if (lineas.some((l) => l.tipoEntradaId === null)) {
      this.errorFormulario.set('Elegí un tipo de entrada en cada línea.');
      return;
    }

    const entradasPayload = lineas.map((l) => ({ tipoEntradaId: l.tipoEntradaId!, cantidad: l.cantidad }));
    const articulosPayload = articulos.map((a) => ({
      articuloVarioId: a.articuloVarioId,
      descripcionLibre: a.descripcionLibre,
      precioUnitario: a.precioUnitario,
      cantidad: a.cantidad,
    }));

    this.guardandoFormulario.set(true);
    this.errorFormulario.set(null);


    const payload: VentaPosRequest = { entradas: entradasPayload, articulos: articulosPayload, formaPago: this.formaPagoEdicion() };
    this.boleteriaService.registrarVentaPosComoAdmin(this.cajaId(), payload).subscribe({
      next: () => this.onGuardadoOk(),
      error: (err) => this.onGuardadoError(err, 'No se pudo registrar la venta. Reintentá.'),
    });
  }

  private onGuardadoOk(): void {
    this.guardandoFormulario.set(false);
    this.cancelarFormulario();
    this.cargarOperaciones();
    this.cambios.emit();
  }

  private onGuardadoError(err: unknown, fallback: string): void {
    console.error('Error al guardar la venta:', err);
    const mensaje = (err as { error?: unknown })?.error;
    this.errorFormulario.set(typeof mensaje === 'string' ? mensaje : fallback);
    this.guardandoFormulario.set(false);
  }

  // ---------- Movimientos de caja (retiro/aporte, entradas físicas) ----------

  onMovimientoRegistrado(): void {
    this.mostrarRetiro.set(false);
    this.mostrarIngresoEntradas.set(false);
    this.cargarOperaciones();
    this.cambios.emit();
  }
}
