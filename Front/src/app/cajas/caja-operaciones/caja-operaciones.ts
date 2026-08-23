import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CajaDetalleAbierta, CajaService, OperacionCaja, etiquetaTipoOperacion } from '../../services/caja.service';
import { BoleteriaService, EditarVentaRequest } from '../../services/boleteria.service';
import { TipoEntradaService } from '../../services/tipo-entrada.service';
import { TipoEntrada } from '../../models/tipo-entrada';
import { FormaPagoPos } from '../../models/compra';
import { FORMAS_PAGO } from '../../models/forma-pago';
import { Spinner } from '../../shared/spinner/spinner';
import { PesosPipe } from '../../shared/pesos.pipe';
import { LucideShoppingCart, LucideArrowDownRight, LucideArrowUpRight, LucideTicketPlus, LucideTicketMinus } from '@lucide/angular';

interface LineaEdicion {
  tipoEntradaId: number | null;
  cantidad: number;
}

type FiltroTipoOperacion = 'TODAS' | 'VENTAS' | 'MOVIMIENTOS';

const OPERACIONES_POR_PAGINA = 15;

/**
 * Detalle de una caja para el admin (ADMIN-only, gateado en SecurityConfig): a diferencia del
 * detalle de una caja ya cerrada (ver ResumenCierre), funciona con la caja todavía ABIERTA —
 * para poder encontrar y corregir una venta mal cargada mientras el boletero sigue trabajando.
 * Se muestra igual que ResumenCierre: como contenido desplegado debajo de la fila en la tabla
 * de "Cajas abiertas ahora" (ver cajas.html), no como un modal aparte.
 */
@Component({
  selector: 'app-caja-operaciones',
  imports: [PesosPipe, DatePipe, FormsModule, Spinner, LucideShoppingCart, LucideArrowDownRight, LucideArrowUpRight, LucideTicketPlus, LucideTicketMinus],
  templateUrl: './caja-operaciones.html',
  styleUrl: './caja-operaciones.css',
})
export class CajaOperaciones implements OnInit {
  private cajaService = inject(CajaService);
  private boleteriaService = inject(BoleteriaService);
  private tipoEntradaService = inject(TipoEntradaService);

  cajaId = input.required<number>();

  /** Se canceló o editó una venta: el padre refresca los totales de "Cajas abiertas ahora". */
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
    if (filtro === 'VENTAS') return ops.filter((o) => o.tipo === 'VENTA');
    if (filtro === 'MOVIMIENTOS') return ops.filter((o) => o.tipo !== 'VENTA');
    return ops;
  });
  operacionesPagina = computed(() => {
    const inicio = this.paginaActual() * OPERACIONES_POR_PAGINA;
    return this.operacionesFiltradas().slice(inicio, inicio + OPERACIONES_POR_PAGINA);
  });
  totalPaginas = computed(() => Math.max(1, Math.ceil(this.operacionesFiltradas().length / OPERACIONES_POR_PAGINA)));

  cancelandoId = signal<number | null>(null);

  /** Sólo se cargan los tipos de entrada la primera vez que hace falta (al abrir una edición). */
  private tiposEntradaCargados = false;
  tiposEntrada = signal<TipoEntrada[]>([]);

  editandoCompraId = signal<number | null>(null);
  cargandoEdicion = signal(false);
  guardandoEdicion = signal(false);
  errorEdicion = signal<string | null>(null);
  formaPagoEdicion = signal<FormaPagoPos>('EFECTIVO_BOLETERIA');
  lineasEdicion = signal<LineaEdicion[]>([]);

  ngOnInit(): void {
    this.cargarOperaciones();
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

  private cargarOperaciones(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.cajaService.obtenerOperaciones(this.cajaId()).subscribe({
      next: (d) => {
        this.detalle.set(d);
        this.paginaActual.set(0);
        this.cargando.set(false);
      },
      error: (err) => {
        console.error('Error al cargar las operaciones de la caja:', err);
        this.error.set('No se pudo cargar el detalle de esta caja.');
        this.cargando.set(false);
      },
    });
  }

  cancelar(op: OperacionCaja): void {
    if (op.compraId === null) return;
    if (!window.confirm(`¿Cancelar esta venta (${op.detalle})? Deja de contar para el cupo diario, la caja y los reportes.`)) return;
    this.cancelandoId.set(op.compraId);
    this.boleteriaService.cancelarVenta(op.compraId).subscribe({
      next: () => {
        this.cancelandoId.set(null);
        this.cargarOperaciones();
        this.cambios.emit();
      },
      error: (err) => {
        console.error('Error al cancelar la venta:', err);
        window.alert(typeof err?.error === 'string' ? err.error : 'No se pudo cancelar la venta. Reintentá.');
        this.cancelandoId.set(null);
      },
    });
  }

  iniciarEdicion(op: OperacionCaja): void {
    if (op.compraId === null) return;
    if (!this.tiposEntradaCargados) {
      this.tiposEntradaCargados = true;
      this.tipoEntradaService.getTiposEntrada().subscribe({
        next: (tipos) => this.tiposEntrada.set(tipos.filter((t) => t.tipo === 'ENTRADA' && t.activo)),
        error: (err) => console.error('Error al cargar los tipos de entrada:', err),
      });
    }

    this.editandoCompraId.set(op.compraId);
    this.cargandoEdicion.set(true);
    this.errorEdicion.set(null);
    this.boleteriaService.obtenerPorId(op.compraId).subscribe({
      next: (reserva) => {
        this.formaPagoEdicion.set((reserva.formaPago as FormaPagoPos) ?? 'EFECTIVO_BOLETERIA');
        this.lineasEdicion.set(
          (reserva.detalles ?? [])
            .filter((d) => d.tipoEntrada !== null)
            .map((d) => ({ tipoEntradaId: d.tipoEntrada!.id, cantidad: d.cantidad }))
        );
        this.cargandoEdicion.set(false);
      },
      error: (err) => {
        console.error('Error al cargar la venta a editar:', err);
        this.errorEdicion.set('No se pudo cargar esta venta.');
        this.cargandoEdicion.set(false);
      },
    });
  }

  cancelarEdicion(): void {
    this.editandoCompraId.set(null);
    this.lineasEdicion.set([]);
    this.errorEdicion.set(null);
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

  guardarEdicion(): void {
    const compraId = this.editandoCompraId();
    if (compraId === null) return;

    const lineas = this.lineasEdicion();
    if (lineas.length === 0) {
      this.errorEdicion.set('Agregá al menos una entrada.');
      return;
    }
    if (lineas.some((l) => l.tipoEntradaId === null)) {
      this.errorEdicion.set('Elegí un tipo de entrada en cada línea.');
      return;
    }

    const payload: EditarVentaRequest = {
      entradas: lineas.map((l) => ({ tipoEntradaId: l.tipoEntradaId!, cantidad: l.cantidad })),
      formaPago: this.formaPagoEdicion(),
    };

    this.guardandoEdicion.set(true);
    this.errorEdicion.set(null);
    this.boleteriaService.editarVenta(compraId, payload).subscribe({
      next: () => {
        this.guardandoEdicion.set(false);
        this.cancelarEdicion();
        this.cargarOperaciones();
        this.cambios.emit();
      },
      error: (err) => {
        console.error('Error al editar la venta:', err);
        this.errorEdicion.set(typeof err?.error === 'string' ? err.error : 'No se pudo guardar la corrección. Reintentá.');
        this.guardandoEdicion.set(false);
      },
    });
  }
}
