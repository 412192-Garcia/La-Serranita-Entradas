import { Component, computed, inject, input, output, signal, viewChild } from '@angular/core';
import { DatePipe, DecimalPipe, NgTemplateOutlet } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AjusteCaja, AjusteCajaInput, Caja, CajaService, OperacionCaja, SegmentoEntrada, etiquetaTipoOperacion } from '../../services/caja.service';
import { ConfiguracionService, DescuentoEfectivo } from '../../services/configuracion.service';
import { TipoEntradaService } from '../../services/tipo-entrada.service';
import { TipoEntrada } from '../../models/tipo-entrada';
import { FormaPagoPos } from '../../models/compra';
import { etiquetaFormaPago } from '../../models/forma-pago';
import { cotizarLocalmente } from '../../shared/calculo-precio.util';
import { MoneyInputDirective } from '../../shared/money-input/money-input.directive';
import { Modal } from '../../shared/modal/modal';
import { PesosPipe } from '../../shared/pesos.pipe';
import { ConteoCierre } from '../conteo-cierre/conteo-cierre';
import { LucideShoppingCart, LucideArrowDownRight, LucideArrowUpRight, LucideTicketPlus, LucideTicketMinus, LucideChevronDown } from '@lucide/angular';

type FiltroOperaciones = 'TODAS' | 'VENTAS' | 'MOVIMIENTOS';

type ColumnaClave = FormaPagoPos; // 'EFECTIVO_BOLETERIA' | 'TARJETA' | 'MERCADO_PAGO_QR'

interface ColumnaDesglose {
  clave: ColumnaClave;
  etiqueta: string;
}

/** Una fila del desglose de solo lectura, agrupada por forma de pago. */
interface SubLectura {
  descKey: string;
  /** "−15%" / "−$500" / null (venta a precio de lista). */
  descLabel: string | null;
  cantidad: number;
  monto: number;
}

interface ItemLectura {
  firma: string;
  cantidadTotal: number;
  montoTotal: number;
  /** Desglose por descuento. Una sola sub sin descuento → se muestra plano. */
  subs: SubLectura[];
}

interface GrupoLectura {
  clave: string;
  etiqueta: string;
  subtotal: number;
  items: ItemLectura[];
}

interface CeldaVista {
  clave: ColumnaClave;
  cantidad: number;
  monto: number;
  /** Delta de ESTA sesión de revisión (mover/agregar). */
  ajuste: number;
  /** Delta ya aplicado en correcciones previas. */
  aplicado: number;
  /** El cambio de esta sesión en la celda es (todo) un traslado a/desde otra columna (badge amarillo). */
  esTraslado: boolean;
  /** Cuántas de las ventas de la celda se cobraron en dólares (no se pueden mover). */
  dolares: number;
  puedeQuitar: boolean;
  puedeAgregar: boolean;
}

/** Una fila de una matriz = un tamaño de grupo + un descuento de ese tipo de entrada. */
interface FilaTipo {
  key: string;
  cantidad: number;
  desc: Descuento;
  descKey: string;
  /** "−15%" / "−$500" / null (venta normal). */
  descLabel: string | null;
  celdas: CeldaVista[];
}

/** Filas del mismo tamaño de grupo, juntas: si son varias (normal + con descuento) se
 * muestran como un bloque con el "10×" centrado a la izquierda. */
interface GrupoFilas {
  cantidad: number;
  filas: FilaTipo[];
}

/** Una venta que el admin agrega en revisión (por el form o con el + de una celda). */
interface VentaExtra {
  id: number;
  tipoId: number;
  tipoNombre: string;
  cantidad: number;
  desc: Descuento;
  descKey: string;
  /** Monto real cobrado por esta venta (tarifa menos el descuento). */
  monto: number;
  forma: ColumnaClave;
}

interface MatrizTipo {
  tipoId: number;
  tipoNombre: string;
  filas: FilaTipo[];
  grupos: GrupoFilas[];
}

interface TotalVista {
  clave: string;
  etiqueta: string;
  esperado: number;
  contado: number | null;
  diferencia: number | null;
}

/**
 * El descuento con el que se cobró una venta, tal cual fue (no convertido): así una venta normal,
 * una con −15% y una con −$500 caen en filas distintas de la matriz.
 */
type Descuento =
  | { tipo: 'NINGUNO' }
  | { tipo: 'PORCENTAJE'; valor: number }
  | { tipo: 'MONTO'; valor: number };

const SIN_DESCUENTO: Descuento = { tipo: 'NINGUNO' };

function descuentoDeSegmento(s: SegmentoEntrada): Descuento {
  if (s.descuentoPorcentaje != null && s.descuentoPorcentaje > 0) {
    return { tipo: 'PORCENTAJE', valor: Math.round(s.descuentoPorcentaje) };
  }
  if (s.descuentoMonto != null && s.descuentoMonto > 0) {
    return { tipo: 'MONTO', valor: Math.round(s.descuentoMonto) };
  }
  return SIN_DESCUENTO;
}

/** Clave estable para agrupar/filtrar por descuento. */
function descuentoKey(d: Descuento): string {
  if (d.tipo === 'PORCENTAJE') return `P${d.valor}`;
  if (d.tipo === 'MONTO') return `M${d.valor}`;
  return '0';
}

/** Monto que se cobra por una venta de tarifa `tarifa` con el descuento `d`. */
function montoConDescuento(tarifa: number, d: Descuento): number {
  if (d.tipo === 'PORCENTAJE') return Math.max(0, Math.round(tarifa * (1 - d.valor / 100)));
  if (d.tipo === 'MONTO') return Math.max(0, Math.round(tarifa - d.valor));
  return Math.round(tarifa);
}

/** Un segmento de entrada de una venta real, ya listo para la matriz. */
interface SegView {
  id: string;
  compraId: number;
  tipoId: number;
  tipoNombre: string;
  cantidad: number;
  monto: number;
  desc: Descuento;
  descKey: string;
  formaOriginal: ColumnaClave;
  /** Venta cobrada en efectivo-dólares: no se puede sacar de su forma (entró otra moneda, no pesos). */
  esDolar: boolean;
}

const ORDEN_LECTURA = ['EFECTIVO', 'TARJETA', 'QR'];

const COLUMNAS: ColumnaDesglose[] = [
  { clave: 'EFECTIVO_BOLETERIA', etiqueta: 'Efectivo' },
  { clave: 'TARJETA', etiqueta: 'Tarjeta' },
  { clave: 'MERCADO_PAGO_QR', etiqueta: 'QR' },
];

@Component({
  selector: 'app-resumen-cierre',
  imports: [PesosPipe, DatePipe, DecimalPipe, NgTemplateOutlet, FormsModule, MoneyInputDirective, Modal, ConteoCierre, LucideShoppingCart, LucideArrowDownRight, LucideArrowUpRight, LucideTicketPlus, LucideTicketMinus, LucideChevronDown],
  templateUrl: './resumen-cierre.html',
  styleUrl: './resumen-cierre.css',
})
export class ResumenCierre {
  private cajaService = inject(CajaService);
  private tipoEntradaService = inject(TipoEntradaService);
  private configuracionService = inject(ConfiguracionService);

  private tiposEntrada = signal<TipoEntrada[]>([]);
  private descuentosEfectivo = signal<DescuentoEfectivo[]>([]);

  constructor() {
    this.tipoEntradaService.getTiposEntrada().subscribe({
      next: (ts) => this.tiposEntrada.set(ts.filter((t) => t.tipo === 'ENTRADA')),
      error: () => {},
    });
    this.configuracionService.getDescuentosEfectivo().subscribe({
      next: (ds) => this.descuentosEfectivo.set(ds),
      error: () => {},
    });
  }

  caja = input.required<Caja>();
  mostrarAcciones = input(true);

  cajaActualizada = output<Caja>();
  cajaDeshabilitada = output<Caja>();

  /** El formulario de recuento del cierre, sólo montado en modo revisión (ver template). */
  private conteoCierre = viewChild(ConteoCierre);

  readonly columnas = COLUMNAS;
  readonly etiquetaTipoOperacion = etiquetaTipoOperacion;

  // ---------- Borrar la caja (irreversible) ----------
  readonly PALABRA_CONFIRMACION = 'BORRAR';
  mostrarModalDeshabilitar = signal(false);
  textoConfirmacion = signal('');
  deshabilitando = signal(false);
  errorDeshabilitar = signal<string | null>(null);

  confirmacionOk = computed(
    () => this.textoConfirmacion().trim().toUpperCase() === this.PALABRA_CONFIRMACION
  );

  abrirModalDeshabilitar(): void {
    this.textoConfirmacion.set('');
    this.errorDeshabilitar.set(null);
    this.mostrarModalDeshabilitar.set(true);
  }

  confirmarDeshabilitar(): void {
    if (!this.confirmacionOk() || this.deshabilitando()) return;
    this.deshabilitando.set(true);
    this.errorDeshabilitar.set(null);
    this.cajaService.deshabilitarCaja(this.caja().id).subscribe({
      next: (c) => {
        this.deshabilitando.set(false);
        this.mostrarModalDeshabilitar.set(false);
        this.cajaDeshabilitada.emit(c);
      },
      error: (err) => {
        this.deshabilitando.set(false);
        this.errorDeshabilitar.set(
          typeof err?.error === 'string' ? err.error : 'No se pudo borrar la caja. Reintentá.'
        );
      },
    });
  }

  totalVendido(): number {
    const c = this.caja();
    return (c.totalVentasEfectivo ?? 0) + (c.totalVentasTarjeta ?? 0) + (c.totalVentasQr ?? 0);
  }

  mostrarBilletes = signal(false);
  mostrarOperaciones = signal(false);

  /** Ventas y movimientos (retiros/aportes/entradas físicas) mezclados hacen difícil encontrar
   * algo puntual: mismo filtro que el detalle de una caja abierta (ver CajaOperaciones). */
  filtroOperaciones = signal<FiltroOperaciones>('TODAS');

  operacionesFiltradas = computed<OperacionCaja[]>(() => {
    const ops = this.caja().operaciones ?? [];
    const filtro = this.filtroOperaciones();
    if (filtro === 'VENTAS') return ops.filter((o) => o.tipo === 'VENTA');
    if (filtro === 'MOVIMIENTOS') return ops.filter((o) => o.tipo !== 'VENTA');
    return ops;
  });

  claseDiferenciaValor(v: number | null | undefined): string {
    if (v === null || v === undefined) return '';
    if (v < 0) return 'diferencia-faltante';
    if (v > 0) return 'diferencia-sobrante';
    return 'diferencia-exacta';
  }

  etiquetaForma(forma: string | null): string {
    return etiquetaFormaPago(forma);
  }

  private combinadoPosnetGuardado = computed(() =>
    this.caja().totalCerradoPosnet !== null && this.caja().totalCerradoPosnet !== undefined
  );

  /**
   * El recuento con el que se calcula la columna "Contado" de la tabla de revisión: sale EN VIVO
   * del formulario `app-conteo-cierre` cuando está montado (modo revisión), y de lo ya guardado
   * en la caja cuando no. Así editar un billete recalcula la diferencia sin guardar.
   */
  private recuentoRevision = computed(() => {
    const cc = this.conteoCierre();
    const c = this.caja();
    if (!cc) {
      return {
        combinado: this.combinadoPosnetGuardado(),
        efectivoContado: c.montoContado ?? 0,
        tarjetaContado: c.totalCerradoTarjeta ?? 0,
        qrContado: c.totalCerradoQr ?? 0,
        posnetContado: c.totalCerradoPosnet ?? 0,
        entradasCortadas: c.entradasFisicasCortadas,
      };
    }
    const cierres = cc.valor().cierresPosnet;
    const sum = (forma: 'TARJETA' | 'MERCADO_PAGO_QR' | null) =>
      cierres.filter((x) => x.formaPago === forma).reduce((acc, x) => acc + x.monto, 0);
    return {
      combinado: cc.modoPosnet() === 'COMBINADO',
      efectivoContado: cc.montoContadoCalculado(),
      tarjetaContado: sum('TARJETA'),
      qrContado: sum('MERCADO_PAGO_QR'),
      posnetContado: sum(null),
      entradasCortadas: cc.valor().entradasFisicasCortadas,
    };
  });

  private ventas = computed<OperacionCaja[]>(() =>
    (this.caja().operaciones ?? []).filter((o) => o.tipo === 'VENTA')
  );

  private formaAColumna(forma: string | null): ColumnaClave | null {
    if (forma === 'EFECTIVO_BOLETERIA' || forma === 'TARJETA' || forma === 'MERCADO_PAGO_QR') return forma;
    return null;
  }

  private grupoLectura(forma: string | null): { clave: string; etiqueta: string } {
    if (forma === 'EFECTIVO_BOLETERIA') return { clave: 'EFECTIVO', etiqueta: 'Efectivo' };
    if (forma === 'TARJETA') return { clave: 'TARJETA', etiqueta: 'Tarjeta' };
    if (forma === 'MERCADO_PAGO_QR') return { clave: 'QR', etiqueta: 'QR' };
    return { clave: forma ?? 'OTRO', etiqueta: etiquetaFormaPago(forma) };
  }

  private nombreTipo(tipoId: number): string {
    return this.tiposEntrada().find((t) => t.id === tipoId)?.nombre ?? '?';
  }

  private montoArticulos(op: OperacionCaja): number {
    return op.montoArticulos ?? 0;
  }

  // ---------- Desglose de solo lectura ----------

  desglosePorFormaPago = computed<GrupoLectura[]>(() => {
    const grupos = new Map<string, GrupoLectura>();
    const grupoDe = (forma: string | null) => {
      const { clave, etiqueta } = this.grupoLectura(forma);
      let g = grupos.get(clave);
      if (!g) { g = { clave, etiqueta, subtotal: 0, items: [] }; grupos.set(clave, g); }
      return g;
    };
    // Acumula por forma → firma ("5x Pase General") → descuento.
    const acc = (g: GrupoLectura, firma: string, desc: Descuento, monto: number) => {
      let it = g.items.find((x) => x.firma === firma);
      if (!it) { it = { firma, cantidadTotal: 0, montoTotal: 0, subs: [] }; g.items.push(it); }
      it.cantidadTotal += 1;
      it.montoTotal += monto;
      const descKey = descuentoKey(desc);
      let sub = it.subs.find((s) => s.descKey === descKey);
      if (!sub) { sub = { descKey, descLabel: this.etiquetaDescuento(desc), cantidad: 0, monto: 0 }; it.subs.push(sub); }
      sub.cantidad += 1;
      sub.monto += monto;
    };

    for (const op of this.ventas()) {
      const monto = op.monto ?? 0;
      const art = this.montoArticulos(op);
      const segs = op.segmentosEntrada ?? [];
      if (segs.length === 0 && monto === 0) continue; // venta 100% gratis: no se muestra
      const g = grupoDe(op.formaPago);
      g.subtotal += monto;
      for (const s of segs) acc(g, `${s.cantidad}x ${s.tipoNombre}`, descuentoDeSegmento(s), s.monto);
      if (art > 0) acc(g, 'Artículos varios', SIN_DESCUENTO, art);
    }
    const ordenDescKey = (k: string) => (k === '0' ? '' : k); // "sin descuento" primero
    // "5x Pase General" → 5; "Artículos varios" → Infinity (siempre al final).
    const pasesDe = (firma: string) => {
      const n = parseInt(firma, 10);
      return Number.isNaN(n) ? Infinity : n;
    };
    for (const g of grupos.values()) {
      g.items.sort((a, b) => {
        const pa = pasesDe(a.firma);
        const pb = pasesDe(b.firma);
        if (pa !== pb) return pa - pb;
        return a.firma.localeCompare(b.firma);
      });
      for (const it of g.items) {
        it.subs.sort((a, b) => ordenDescKey(a.descKey).localeCompare(ordenDescKey(b.descKey)));
      }
    }
    return [...grupos.values()].sort((a, b) => indiceOrden(a.clave) - indiceOrden(b.clave));
  });

  /** Artículos varios por forma de pago (línea aparte, tanto en el desglose como en revisión). */
  articulosPorForma = computed<{ clave: string; etiqueta: string; monto: number }[]>(() => {
    const map = new Map<string, { clave: string; etiqueta: string; monto: number }>();
    for (const op of this.ventas()) {
      const art = this.montoArticulos(op);
      if (art <= 0) continue;
      const { clave, etiqueta } = this.grupoLectura(op.formaPago);
      const e = map.get(clave) ?? { clave, etiqueta, monto: 0 };
      e.monto += art;
      map.set(clave, e);
    }
    return [...map.values()].sort((a, b) => indiceOrden(a.clave) - indiceOrden(b.clave));
  });

  // ---------- Modo revisión ----------

  modoRevision = signal(false);
  aplicando = signal(false);
  deshaciendoId = signal<number | null>(null);
  errorRevision = signal<string | null>(null);

  /** segmentoIds "sacados" con el − de una celda. Sin par (un +) al aplicar = venta fantasma; con par = reubicación. */
  private removidos = signal<Set<string>>(new Set());
  /** Ventas que el admin agrega (con el + de una celda o con el form). Con par (un −) al aplicar = reubicación; sin par = venta no registrada. */
  ventasExtra = signal<VentaExtra[]>([]);
  private proximoExtraId = 0;

  /** Montos sueltos: ± libre a una forma, sin venta que lo respalde. */
  agregados = signal<{ id: number; forma: FormaPagoPos; signo: 'AGREGAR' | 'QUITAR'; monto: number; nota: string }[]>([]);
  private proximoAgregadoId = 0;
  formaMontoSuelto = signal<FormaPagoPos>('EFECTIVO_BOLETERIA');
  signoMontoSuelto = signal<'AGREGAR' | 'QUITAR'>('AGREGAR');
  montoSuelto = signal<number | null>(null);
  notaMontoSuelto = signal('');

  /** Estado del form "agregar venta no registrada". */
  nuevaVentaTipoId = signal<number | null>(null);
  nuevaVentaCantidad = signal<number | null>(null);
  nuevaVentaForma = signal<ColumnaClave>('EFECTIVO_BOLETERIA');
  /** Descuento de la venta que se agrega: como % o como monto fijo (o ninguno). */
  nuevaVentaDescModo = signal<'ninguno' | 'porcentaje' | 'monto'>('ninguno');
  nuevaVentaDescValor = signal<number | null>(null);

  /** Tipos elegibles para agregar venta: sólo entradas con precio > 0. */
  tiposPagos = computed(() =>
    this.tiposEntrada().filter((t) => t.tipo === 'ENTRADA' && t.precio > 0).sort((a, b) => a.nombre.localeCompare(b.nombre))
  );

  /** Tarifa (por grupo, según la forma) de la venta que se está por agregar. */
  nuevaVentaTarifa = computed<number | null>(() => {
    const tipoId = this.nuevaVentaTipoId();
    const cantidad = this.nuevaVentaCantidad();
    if (tipoId === null || cantidad === null || cantidad <= 0) return null;
    return Math.round(this.montoTarifa(tipoId, cantidad, this.nuevaVentaForma()));
  });

  /** Monto real cobrado de la venta a agregar, aplicando el descuento elegido. */
  nuevaVentaMonto = computed<number | null>(() => {
    const tarifa = this.nuevaVentaTarifa();
    if (tarifa === null) return null;
    return montoConDescuento(tarifa, this.descuentoNuevaVenta());
  });

  /** Todos los segmentos de entrada de ventas reales, con su forma normalizada. */
  private segmentos = computed<SegView[]>(() => {
    const out: SegView[] = [];
    for (const op of this.ventas()) {
      const col = this.formaAColumna(op.formaPago);
      if (!col || op.compraId === null) continue;
      (op.segmentosEntrada ?? []).forEach((s: SegmentoEntrada, i) => {
        const desc = descuentoDeSegmento(s);
        out.push({
          id: `${op.compraId}:${i}`,
          compraId: op.compraId!,
          tipoId: s.tipoEntradaId,
          tipoNombre: s.tipoNombre,
          cantidad: s.cantidad,
          monto: s.monto,
          desc,
          descKey: descuentoKey(desc),
          formaOriginal: col,
          esDolar: !!op.pagoEnDolares,
        });
      });
    }
    return out;
  });

  private segById(id: string): SegView | undefined {
    return this.segmentos().find((s) => s.id === id);
  }

  /**
   * Empareja lo sacado (−) con lo agregado (+) por tipo + tamaño de grupo + nivel de descuento:
   * cada par = una reubicación (la venta se cobró en una forma y se tocó otra);
   * los − sobrantes = ventas fantasma (quitar); los + sobrantes = ventas no registradas (agregar).
   * Que el descuento entre en la clave hace que sólo se emparejen ventas del MISMO precio.
   */
  private pares = computed<{ reub: { seg: SegView; extra: VentaExtra }[]; quit: SegView[]; agr: VentaExtra[] }>(() => {
    const grupos = new Map<string, { rs: SegView[]; es: VentaExtra[] }>();
    const grupo = (tipoId: number, size: number, descKey: string) => {
      const k = `${tipoId}|${size}|${descKey}`;
      let g = grupos.get(k);
      if (!g) { g = { rs: [], es: [] }; grupos.set(k, g); }
      return g;
    };
    for (const id of this.removidos()) {
      const s = this.segById(id);
      if (s) grupo(s.tipoId, s.cantidad, s.descKey).rs.push(s);
    }
    for (const e of this.ventasExtra()) grupo(e.tipoId, e.cantidad, e.descKey).es.push(e);

    const reub: { seg: SegView; extra: VentaExtra }[] = [];
    const quit: SegView[] = [];
    const agr: VentaExtra[] = [];
    for (const { rs, es } of grupos.values()) {
      const n = Math.min(rs.length, es.length);
      for (let i = 0; i < n; i++) reub.push({ seg: rs[i], extra: es[i] });
      quit.push(...rs.slice(n));
      agr.push(...es.slice(n));
    }
    return { reub, quit, agr };
  });

  /** Precio de una venta de N pases de un tipo en esa forma (efectivo con promo, tarjeta/QR a lista). */
  private montoTarifa(tipoId: number, cantidad: number, col: ColumnaClave): number {
    return cotizarLocalmente(
      col,
      [{ tipoEntradaId: tipoId, cantidad }],
      [],
      {},
      this.tiposEntrada(),
      this.descuentosEfectivo(),
      []
    ).subtotal;
  }

  /**
   * El descuento de un ajuste YA aplicado. El AjusteCaja no lo guarda, así que se infiere del
   * precio unitario contra la tarifa (best-effort; se trata como monto fijo). Sólo afecta cómo
   * se muestra un ajuste previo — la plata del cierre ya viene calculada del backend.
   */
  private descuentoDeAjuste(tipoId: number, size: number, col: ColumnaClave, precioUnit: number): Descuento {
    const tarifa = this.montoTarifa(tipoId, size, col);
    const off = Math.round(tarifa - precioUnit);
    if (tarifa > 0 && off > tarifa * 0.005) return { tipo: 'MONTO', valor: off };
    return SIN_DESCUENTO;
  }

  /** Ajustes ya aplicados que tocan una celda (tipoId, tamaño, descuento, forma). Clave: `tipoId:size:descKey:col`. */
  private aplicadoPorCelda = computed<Map<string, number>>(() => {
    const m = new Map<string, number>();
    const add = (tipoId: number, size: number, col: ColumnaClave | null, precioUnit: number, n: number) => {
      if (!col) return;
      const descKey = descuentoKey(this.descuentoDeAjuste(tipoId, size, col, precioUnit));
      const k = `${tipoId}:${size}:${descKey}:${col}`;
      m.set(k, (m.get(k) ?? 0) + n);
    };
    for (const a of this.caja().ajustes ?? []) {
      if (a.cantidadVentas <= 0) continue;
      const entries = Object.entries(a.lineas ?? {});
      if (entries.length !== 1) continue;
      const tipoId = Number(entries[0][0]);
      const size = Number(entries[0][1]);
      const precioUnit = a.monto / a.cantidadVentas;
      add(tipoId, size, this.formaAColumna(a.formaOrigen), precioUnit, -a.cantidadVentas);
      add(tipoId, size, this.formaAColumna(a.formaDestino), precioUnit, a.cantidadVentas);
    }
    return m;
  });

  /** Tipos (id → nombre) que deben tener matriz: los que aparecen en ventas, en extras o en ajustes previos. */
  private tiposConMatriz = computed<{ id: number; nombre: string }[]>(() => {
    const m = new Map<number, string>();
    for (const s of this.segmentos()) m.set(s.tipoId, s.tipoNombre);
    for (const e of this.ventasExtra()) m.set(e.tipoId, e.tipoNombre);
    for (const a of this.caja().ajustes ?? []) {
      if (a.cantidadVentas <= 0) continue;
      const entries = Object.entries(a.lineas ?? {});
      if (entries.length === 1) {
        const id = Number(entries[0][0]);
        if (!m.has(id)) m.set(id, this.nombreTipo(id));
      }
    }
    return [...m.entries()].map(([id, nombre]) => ({ id, nombre })).sort((a, b) => a.nombre.localeCompare(b.nombre));
  });

  matrices = computed<MatrizTipo[]>(() => {
    const segs = this.segmentos();
    const extras = this.ventasExtra();
    const ajustes = (this.caja().ajustes ?? []).filter((a) => a.cantidadVentas > 0 && Object.keys(a.lineas ?? {}).length === 1);
    const { reub, agr } = this.pares();

    return this.tiposConMatriz().map(({ id: tipoId, nombre: tipoNombre }) => {
      const tSegs = segs.filter((s) => s.tipoId === tipoId);
      const tExtras = extras.filter((e) => e.tipoId === tipoId);
      const tAjustes = ajustes.filter((a) => Number(Object.keys(a.lineas!)[0]) === tipoId);

      // Combos (tamaño de grupo, descuento) que hay que mostrar como fila.
      const combos = new Map<string, { size: number; desc: Descuento; descKey: string }>();
      const addCombo = (size: number, desc: Descuento) => {
        const descKey = descuentoKey(desc);
        combos.set(`${size}|${descKey}`, { size, desc, descKey });
      };
      for (const s of tSegs) addCombo(s.cantidad, s.desc);
      for (const e of tExtras) addCombo(e.cantidad, e.desc);
      for (const a of tAjustes) {
        const size = Number(Object.values(a.lineas!)[0]);
        const col = this.formaAColumna(a.formaOrigen ?? a.formaDestino) ?? 'EFECTIVO_BOLETERIA';
        addCombo(size, this.descuentoDeAjuste(tipoId, size, col, a.monto / a.cantidadVentas));
      }

      const ordenDesc = (d: Descuento) => (d.tipo === 'NINGUNO' ? 0 : d.tipo === 'PORCENTAJE' ? 1000 + d.valor : 100000 + d.valor);
      const filas: FilaTipo[] = [...combos.values()]
        .sort((a, b) => a.size - b.size || ordenDesc(a.desc) - ordenDesc(b.desc))
        .map(({ size, desc, descKey }) => {
          const rowSegs = tSegs.filter((s) => s.cantidad === size && s.descKey === descKey);
          const reubAqui = reub.filter((p) => p.seg.tipoId === tipoId && p.seg.cantidad === size && p.seg.descKey === descKey);
          const celdas: CeldaVista[] = this.columnas.map((col) => {
            const staying = rowSegs.filter((s) => s.formaOriginal === col.clave && !this.removidos().has(s.id));
            const removedAqui = rowSegs.filter((s) => s.formaOriginal === col.clave && this.removidos().has(s.id)).length;
            const extrasAqui = tExtras.filter((e) => e.cantidad === size && e.descKey === descKey && e.forma === col.clave).length;
            const aplicado = this.aplicadoPorCelda().get(`${tipoId}:${size}:${descKey}:${col.clave}`) ?? 0;
            const cantidad = staying.length + extrasAqui + aplicado;
            const ajuste = extrasAqui - removedAqui;
            // Las ventas en dólares no se pueden sacar (entró otra moneda, no pesos): sólo bloquean
            // ESA venta, no la columna entera.
            const dolares = staying.filter((s) => s.esDolar).length;
            const movibles = staying.length - dolares;
            // Precio unitario nominal de esta fila+columna (tarifa por grupo menos el descuento).
            const precioUnit = montoConDescuento(this.montoTarifa(tipoId, size, col.clave), desc);
            // partes de esta celda que son un traslado (un − de acá que tiene su + en otra columna, o viceversa)
            const movidoDesde = reubAqui.filter((p) => p.seg.formaOriginal === col.clave && p.extra.forma !== col.clave).length;
            const movidoHacia = reubAqui.filter((p) => p.extra.forma === col.clave && p.seg.formaOriginal !== col.clave).length;
            const esTraslado = (movidoDesde > 0 || movidoHacia > 0) && removedAqui === movidoDesde && extrasAqui === movidoHacia;
            const entranteReub = reubAqui
              .filter((p) => p.extra.forma === col.clave)
              .reduce((acc, p) => acc + p.seg.monto, 0);
            const entranteAgr = agr
              .filter((e) => e.forma === col.clave && e.tipoId === tipoId && e.cantidad === size && e.descKey === descKey)
              .reduce((acc, e) => acc + e.monto, 0);
            const monto =
              ajuste === 0 && aplicado === 0
                ? staying.reduce((acc, s) => acc + s.monto, 0)
                : staying.reduce((acc, s) => acc + s.monto, 0) + entranteReub + entranteAgr + aplicado * precioUnit;
            return {
              clave: col.clave,
              cantidad,
              monto: cantidad <= 0 ? 0 : monto,
              ajuste,
              aplicado,
              esTraslado,
              dolares,
              puedeQuitar: movibles > 0 || extrasAqui > 0,
              puedeAgregar: true,
            };
          });
          return {
            key: `${size}|${descKey}`, cantidad: size, desc, descKey,
            descLabel: this.etiquetaDescuento(desc), celdas,
          };
        });
      // Junta las filas del mismo tamaño de grupo (misma "10×") en un bloque.
      const grupos: GrupoFilas[] = [];
      for (const f of filas) {
        const ultimo = grupos[grupos.length - 1];
        if (ultimo && ultimo.cantidad === f.cantidad) ultimo.filas.push(f);
        else grupos.push({ cantidad: f.cantidad, filas: [f] });
      }
      return { tipoId, tipoNombre, filas, grupos };
    });
  });

  etiquetaDescuento(d: Descuento): string | null {
    if (d.tipo === 'PORCENTAJE') return `−${d.valor}%`;
    if (d.tipo === 'MONTO') return `−$${d.valor.toLocaleString('es-AR')}`;
    return null;
  }

  private deltas = computed<Record<ColumnaClave, number>>(() => {
    const d: Record<ColumnaClave, number> = { EFECTIVO_BOLETERIA: 0, TARJETA: 0, MERCADO_PAGO_QR: 0 };
    const { reub, quit, agr } = this.pares();
    for (const { seg, extra } of reub) {
      if (extra.forma === seg.formaOriginal) continue;
      d[extra.forma] += seg.monto;
      d[seg.formaOriginal] -= seg.monto;
    }
    for (const seg of quit) d[seg.formaOriginal] -= seg.monto;
    for (const e of agr) d[e.forma] += e.monto;
    for (const a of this.agregados()) d[a.forma] += a.signo === 'AGREGAR' ? a.monto : -a.monto;
    return d;
  });

  totalesRevision = computed<TotalVista[]>(() => {
    const c = this.caja();
    const d = this.deltas();
    const r = this.recuentoRevision();
    const combinado = r.combinado;
    const filas: TotalVista[] = [];

    const efEsp = (c.efectivoEsperado ?? 0) + d.EFECTIVO_BOLETERIA;
    filas.push({ clave: 'EFECTIVO_BOLETERIA', etiqueta: 'Efectivo', esperado: efEsp, contado: r.efectivoContado, diferencia: r.efectivoContado - efEsp });

    const tarEsp = (c.totalVentasTarjeta ?? 0) + d.TARJETA;
    const qrEsp = (c.totalVentasQr ?? 0) + d.MERCADO_PAGO_QR;
    filas.push({
      clave: 'TARJETA', etiqueta: 'Tarjeta', esperado: tarEsp,
      contado: combinado ? null : r.tarjetaContado,
      diferencia: combinado ? null : r.tarjetaContado - tarEsp,
    });
    filas.push({
      clave: 'MERCADO_PAGO_QR', etiqueta: 'QR', esperado: qrEsp,
      contado: combinado ? null : r.qrContado,
      diferencia: combinado ? null : r.qrContado - qrEsp,
    });
    if (combinado) {
      const esp = tarEsp + qrEsp;
      filas.push({
        clave: 'POSNET', etiqueta: 'Tarjeta + QR (cerrado junto)', esperado: esp,
        contado: r.posnetContado, diferencia: r.posnetContado - esp,
      });
    }
    return filas;
  });

  entradasRevision = computed(() => {
    const c = this.caja();
    if (c.entradasFisicasEsperadas === null) return null;
    // Cuántas entradas del talonario deberían cortarse: sólo cuentan los tipos que entregan
    // una entrada física (no toda venta paga la entrega — ver TipoEntrada.entregaEntrada).
    let dTal = 0;
    const aplica = (tipoId: number, cantidad: number, signo: number) => {
      const tipo = this.tiposEntrada().find((t) => t.id === tipoId);
      if (tipo?.entregaEntrada) dTal += signo * cantidad;
    };
    const { quit, agr } = this.pares();
    for (const seg of quit) aplica(seg.tipoId, seg.cantidad, -1);
    for (const e of agr) aplica(e.tipoId, e.cantidad, 1);
    // las reubicaciones no cambian el conteo de entradas (son las mismas ventas)

    const esperadasTalonario = (c.entradasFisicasEsperadas ?? 0) + dTal;
    const cortadas = this.recuentoRevision().entradasCortadas ?? null;
    return {
      esperadasTalonario,
      cortadas,
      diferenciaTalonario: cortadas === null ? null : esperadasTalonario - cortadas,
      cambio: dTal !== 0,
    };
  });

  /** ¿El recuento del cierre se editó respecto de lo que ya tenía la caja? */
  private conteoModificado = computed(() => {
    const cc = this.conteoCierre();
    if (!cc) return false;
    const c = this.caja();
    const r = this.recuentoRevision();
    if (r.efectivoContado !== (c.montoContado ?? 0)) return true;
    if ((r.entradasCortadas ?? null) !== (c.entradasFisicasCortadas ?? null)) return true;
    if ((cc.valor().dolaresContado ?? null) !== (c.dolaresContado ?? null)) return true;
    if (r.combinado !== this.combinadoPosnetGuardado()) return true;
    if (r.combinado) return r.posnetContado !== (c.totalCerradoPosnet ?? 0);
    return r.tarjetaContado !== (c.totalCerradoTarjeta ?? 0) || r.qrContado !== (c.totalCerradoQr ?? 0);
  });

  hayCambios = computed(
    () =>
      this.removidos().size > 0 ||
      this.ventasExtra().length > 0 ||
      this.agregados().length > 0 ||
      this.conteoModificado()
  );

  /** Los ajustes (traspasos/agregados) que se van a mandar, sin el recuento. */
  private ajustesParaEnviar(): AjusteCajaInput[] {
    const ajustes: AjusteCajaInput[] = [];
    const grupos = new Map<string, AjusteCajaInput>();
    const { reub, quit, agr } = this.pares();

    for (const { seg, extra } of reub) {
      const destino = extra.forma;
      if (destino === seg.formaOriginal) continue;
      const key = `R|${seg.tipoId}|${seg.cantidad}|${seg.descKey}|${seg.formaOriginal}|${destino}`;
      let g = grupos.get(key);
      if (!g) {
        g = {
          formaOrigen: seg.formaOriginal,
          formaDestino: destino,
          monto: 0,
          cantidadVentas: 0,
          detalle: `${seg.cantidad}x ${seg.tipoNombre}`,
          comprasMovidas: [],
          lineas: { [seg.tipoId]: seg.cantidad },
        };
        grupos.set(key, g);
      }
      g.monto += seg.monto;
      g.cantidadVentas += 1;
      g.comprasMovidas.push(seg.compraId);
    }

    for (const seg of quit) {
      const key = `Q|${seg.tipoId}|${seg.cantidad}|${seg.descKey}|${seg.formaOriginal}`;
      let g = grupos.get(key);
      if (!g) {
        g = {
          formaOrigen: seg.formaOriginal,
          formaDestino: null,
          monto: 0,
          cantidadVentas: 0,
          detalle: `${seg.cantidad}x ${seg.tipoNombre}`,
          comprasMovidas: [],
          lineas: { [seg.tipoId]: seg.cantidad },
        };
        grupos.set(key, g);
      }
      g.monto += seg.monto;
      g.cantidadVentas += 1;
      g.comprasMovidas.push(seg.compraId);
    }
    ajustes.push(...grupos.values());

    const extraGrupos = new Map<string, AjusteCajaInput>();
    for (const e of agr) {
      const key = `${e.tipoId}|${e.cantidad}|${e.descKey}|${e.forma}`;
      let g = extraGrupos.get(key);
      if (!g) {
        g = {
          formaOrigen: null,
          formaDestino: e.forma,
          monto: 0,
          cantidadVentas: 0,
          detalle: `${e.cantidad}x ${e.tipoNombre}`,
          comprasMovidas: [],
          lineas: { [e.tipoId]: e.cantidad },
        };
        extraGrupos.set(key, g);
      }
      g.monto += Math.round(e.monto);
      g.cantidadVentas += 1;
    }
    ajustes.push(...[...extraGrupos.values()].filter((g) => g.monto > 0));

    for (const a of this.agregados()) {
      ajustes.push({
        formaOrigen: a.signo === 'QUITAR' ? a.forma : null,
        formaDestino: a.signo === 'AGREGAR' ? a.forma : null,
        monto: a.monto,
        cantidadVentas: 0,
        detalle: a.nota || null,
        comprasMovidas: [],
        lineas: {},
      });
    }
    return ajustes;
  }

  /**
   * Chips resumen abajo de la matriz. Un − que tiene su + (mismo tipo + tamaño) se muestra como
   * un traslado (amarillo, "Tarjeta → Efectivo"); los − sueltos como "sacado" (rojo) y los +
   * sueltos como "agregado" (verde).
   */
  movimientos = computed<{
    traslados: { key: string; tipoNombre: string; cantidad: number; descLabel: string | null; origen: ColumnaClave; destino: ColumnaClave; count: number; segIds: string[]; extraIds: number[] }[];
    sacados: { key: string; tipoNombre: string; cantidad: number; descLabel: string | null; forma: ColumnaClave; count: number; ids: string[] }[];
    agregados: { key: string; tipoNombre: string; cantidad: number; descLabel: string | null; forma: ColumnaClave; count: number; ids: number[] }[];
  }>(() => {
    const { reub, quit, agr } = this.pares();

    const tMap = new Map<string, { key: string; tipoNombre: string; cantidad: number; descLabel: string | null; origen: ColumnaClave; destino: ColumnaClave; count: number; segIds: string[]; extraIds: number[] }>();
    for (const { seg, extra } of reub) {
      if (extra.forma === seg.formaOriginal) continue;
      const key = `${seg.tipoId}|${seg.cantidad}|${seg.descKey}|${seg.formaOriginal}|${extra.forma}`;
      const g = tMap.get(key) ?? { key, tipoNombre: seg.tipoNombre, cantidad: seg.cantidad, descLabel: this.etiquetaDescuento(seg.desc), origen: seg.formaOriginal, destino: extra.forma, count: 0, segIds: [], extraIds: [] };
      g.count++; g.segIds.push(seg.id); g.extraIds.push(extra.id);
      tMap.set(key, g);
    }

    const sMap = new Map<string, { key: string; tipoNombre: string; cantidad: number; descLabel: string | null; forma: ColumnaClave; count: number; ids: string[] }>();
    for (const s of quit) {
      const key = `${s.tipoId}|${s.cantidad}|${s.descKey}|${s.formaOriginal}`;
      const g = sMap.get(key) ?? { key, tipoNombre: s.tipoNombre, cantidad: s.cantidad, descLabel: this.etiquetaDescuento(s.desc), forma: s.formaOriginal, count: 0, ids: [] };
      g.count++; g.ids.push(s.id);
      sMap.set(key, g);
    }

    const aMap = new Map<string, { key: string; tipoNombre: string; cantidad: number; descLabel: string | null; forma: ColumnaClave; count: number; ids: number[] }>();
    for (const e of agr) {
      const key = `${e.tipoId}|${e.cantidad}|${e.descKey}|${e.forma}`;
      const g = aMap.get(key) ?? { key, tipoNombre: e.tipoNombre, cantidad: e.cantidad, descLabel: this.etiquetaDescuento(e.desc), forma: e.forma, count: 0, ids: [] };
      g.count++; g.ids.push(e.id);
      aMap.set(key, g);
    }

    return { traslados: [...tMap.values()], sacados: [...sMap.values()], agregados: [...aMap.values()] };
  });

  /** ✕ del chip de traslado: deshace las dos puntas (el − y el +). */
  deshacerTraslado(m: { segIds: string[]; extraIds: number[] }): void {
    const r = new Set(this.removidos());
    for (const id of m.segIds) r.delete(id);
    this.removidos.set(r);
    this.ventasExtra.update((es) => es.filter((e) => !m.extraIds.includes(e.id)));
  }

  /** ✕ del chip de "sacado": devuelve esas ventas a su forma original. */
  restaurarSacados(ids: string[]): void {
    const r = new Set(this.removidos());
    for (const id of ids) r.delete(id);
    this.removidos.set(r);
  }

  /** ✕ del chip de "agregado". */
  quitarAgregados(ids: number[]): void {
    this.ventasExtra.update((es) => es.filter((e) => !ids.includes(e.id)));
  }

  toggleRevision(): void {
    if (this.modoRevision()) this.salirRevision();
    else this.modoRevision.set(true);
  }

  private salirRevision(): void {
    this.conteoCierre()?.reset();
    this.modoRevision.set(false);
    this.removidos.set(new Set());
    this.ventasExtra.set([]);
    this.agregados.set([]);
    this.montoSuelto.set(null);
    this.notaMontoSuelto.set('');
    this.nuevaVentaTipoId.set(null);
    this.nuevaVentaCantidad.set(null);
    this.nuevaVentaDescModo.set('ninguno');
    this.nuevaVentaDescValor.set(null);
    this.errorRevision.set(null);
  }

  /** − en una celda (fila = tamaño + descuento): primero cancela un + que estuviera ahí; si no, saca una venta real. */
  quitar(tipoId: number, size: number, descKey: string, col: ColumnaClave): void {
    const idx = this.ventasExtra().findIndex(
      (e) => e.tipoId === tipoId && e.cantidad === size && e.descKey === descKey && e.forma === col
    );
    if (idx >= 0) {
      this.ventasExtra.update((es) => es.filter((_, i) => i !== idx));
      return;
    }
    const reales = this.segmentos().filter(
      (s) =>
        s.tipoId === tipoId &&
        s.cantidad === size &&
        s.descKey === descKey &&
        s.formaOriginal === col &&
        !s.esDolar &&
        !this.removidos().has(s.id)
    );
    if (reales.length) {
      const r = new Set(this.removidos());
      r.add(reales[reales.length - 1].id);
      this.removidos.set(r);
    }
  }

  /** + en una celda: primero deshace un − que estuviera ahí; si no, agrega una venta a ese precio. */
  agregar(tipoId: number, size: number, desc: Descuento, col: ColumnaClave): void {
    const descKey = descuentoKey(desc);
    const removidoAqui = this.segmentos().find(
      (s) => s.tipoId === tipoId && s.cantidad === size && s.descKey === descKey && s.formaOriginal === col && this.removidos().has(s.id)
    );
    if (removidoAqui) {
      const r = new Set(this.removidos());
      r.delete(removidoAqui.id);
      this.removidos.set(r);
      return;
    }
    const monto = montoConDescuento(this.montoTarifa(tipoId, size, col), desc);
    this.ventasExtra.update((es) => [
      ...es,
      { id: this.proximoExtraId++, tipoId, tipoNombre: this.nombreTipo(tipoId), cantidad: size, desc, descKey, monto, forma: col },
    ]);
  }

  private descuentoNuevaVenta(): Descuento {
    const v = this.nuevaVentaDescValor() ?? 0;
    if (this.nuevaVentaDescModo() === 'porcentaje' && v > 0) return { tipo: 'PORCENTAJE', valor: Math.round(Math.min(v, 100)) };
    if (this.nuevaVentaDescModo() === 'monto' && v > 0) return { tipo: 'MONTO', valor: Math.round(v) };
    return SIN_DESCUENTO;
  }

  agregarVenta(): void {
    const tipoId = this.nuevaVentaTipoId();
    const cantidad = this.nuevaVentaCantidad();
    if (tipoId === null) { this.errorRevision.set('Elegí el tipo de entrada.'); return; }
    if (cantidad === null || cantidad <= 0) { this.errorRevision.set('Indicá cuántos pases (ej. 3).'); return; }
    const monto = this.nuevaVentaMonto();
    if (monto === null || monto <= 0) {
      this.errorRevision.set('El monto de la venta quedó en cero: revisá el descuento.');
      return;
    }
    this.errorRevision.set(null);
    const desc = this.descuentoNuevaVenta();
    this.ventasExtra.update((es) => [
      ...es,
      {
        id: this.proximoExtraId++,
        tipoId,
        tipoNombre: this.nombreTipo(tipoId),
        cantidad,
        desc,
        descKey: descuentoKey(desc),
        monto,
        forma: this.nuevaVentaForma(),
      },
    ]);
    this.nuevaVentaCantidad.set(null);
    this.nuevaVentaDescModo.set('ninguno');
    this.nuevaVentaDescValor.set(null);
  }

  quitarVentaExtra(id: number): void {
    this.ventasExtra.update((es) => es.filter((e) => e.id !== id));
  }

  agregarMontoSuelto(): void {
    const monto = this.montoSuelto();
    if (monto === null || monto <= 0) { this.errorRevision.set('Indicá un monto mayor a cero.'); return; }
    this.errorRevision.set(null);
    this.agregados.update((a) => [
      ...a,
      { id: this.proximoAgregadoId++, forma: this.formaMontoSuelto(), signo: this.signoMontoSuelto(), monto, nota: this.notaMontoSuelto().trim() },
    ]);
    this.montoSuelto.set(null);
    this.notaMontoSuelto.set('');
  }

  quitarMontoSuelto(id: number): void {
    this.agregados.update((a) => a.filter((x) => x.id !== id));
  }

  aplicar(): void {
    const cc = this.conteoCierre();
    if (!cc) return;

    const errorConteo = cc.validar();
    if (errorConteo) {
      this.errorRevision.set(errorConteo);
      return;
    }

    const ajustes = this.ajustesParaEnviar();
    if (ajustes.length === 0 && !this.conteoModificado()) return;

    const v = cc.valor();
    this.aplicando.set(true);
    this.errorRevision.set(null);
    this.cajaService
      .corregirCaja(this.caja().id, {
        conteoEfectivo: v.conteoEfectivo,
        cierresPosnet: v.cierresPosnet,
        entradasFisicasCortadas: v.entradasFisicasCortadas!,
        cambioContado: v.cambioContado,
        dolaresContado: v.dolaresContado,
        ajustes,
      })
      .subscribe({
        next: (c) => {
          this.aplicando.set(false);
          this.salirRevision();
          this.cajaActualizada.emit(c);
        },
        error: (err) => {
          this.aplicando.set(false);
          this.errorRevision.set(typeof err?.error === 'string' ? err.error : 'No se pudo guardar la corrección. Reintentá.');
        },
      });
  }

  deshacerAjuste(ajuste: AjusteCaja): void {
    this.deshaciendoId.set(ajuste.id);
    this.errorRevision.set(null);
    this.cajaService.eliminarAjuste(this.caja().id, ajuste.id).subscribe({
      next: (c) => { this.deshaciendoId.set(null); this.cajaActualizada.emit(c); },
      error: (err) => {
        this.deshaciendoId.set(null);
        this.errorRevision.set(typeof err?.error === 'string' ? err.error : 'No se pudo deshacer el ajuste. Reintentá.');
      },
    });
  }
}

function indiceOrden(clave: string): number {
  const i = ORDEN_LECTURA.indexOf(clave);
  return i === -1 ? ORDEN_LECTURA.length : i;
}
