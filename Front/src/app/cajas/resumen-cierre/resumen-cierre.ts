import { Component, computed, inject, input, output, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
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
import { LucideShoppingCart, LucideArrowDownRight, LucideArrowUpRight, LucideTicketPlus, LucideTicketMinus } from '@lucide/angular';

type ColumnaClave = FormaPagoPos; // 'EFECTIVO_BOLETERIA' | 'TARJETA' | 'MERCADO_PAGO_QR'

interface ColumnaDesglose {
  clave: ColumnaClave;
  etiqueta: string;
}

/** Una fila del desglose de solo lectura, agrupada por forma de pago. */
interface GrupoLectura {
  clave: string;
  etiqueta: string;
  subtotal: number;
  filas: { firma: string; cantidad: number; monto: number }[];
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
  puedeQuitar: boolean;
  puedeAgregar: boolean;
}

/** Una fila de una matriz = un tamaño de grupo de ese tipo de entrada. */
interface FilaTipo {
  cantidad: number;
  celdas: CeldaVista[];
}

/** Una venta que el admin agrega en revisión (por el form o con el + de una celda). */
interface VentaExtra {
  id: number;
  tipoId: number;
  tipoNombre: string;
  cantidad: number;
  forma: ColumnaClave;
}

interface MatrizTipo {
  tipoId: number;
  tipoNombre: string;
  filas: FilaTipo[];
}

interface TotalVista {
  clave: string;
  etiqueta: string;
  esperado: number;
  contado: number | null;
  diferencia: number | null;
}

/** Un segmento de entrada de una venta real, ya listo para la matriz. */
interface SegView {
  id: string;
  compraId: number;
  tipoId: number;
  tipoNombre: string;
  cantidad: number;
  monto: number;
  formaOriginal: ColumnaClave;
}

const ORDEN_LECTURA = ['EFECTIVO', 'TARJETA', 'QR'];

const COLUMNAS: ColumnaDesglose[] = [
  { clave: 'EFECTIVO_BOLETERIA', etiqueta: 'Efectivo' },
  { clave: 'TARJETA', etiqueta: 'Tarjeta' },
  { clave: 'MERCADO_PAGO_QR', etiqueta: 'QR' },
];

@Component({
  selector: 'app-resumen-cierre',
  imports: [PesosPipe, DatePipe, DecimalPipe, FormsModule, MoneyInputDirective, Modal, LucideShoppingCart, LucideArrowDownRight, LucideArrowUpRight, LucideTicketPlus, LucideTicketMinus],
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

  corregir = output<void>();
  cajaActualizada = output<Caja>();
  cajaDeshabilitada = output<Caja>();

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

  claseDiferenciaValor(v: number | null | undefined): string {
    if (v === null || v === undefined) return '';
    if (v < 0) return 'diferencia-faltante';
    if (v > 0) return 'diferencia-sobrante';
    return 'diferencia-exacta';
  }

  etiquetaForma(forma: string | null): string {
    return etiquetaFormaPago(forma);
  }

  private combinadoPosnet = computed(() =>
    this.caja().totalCerradoPosnet !== null && this.caja().totalCerradoPosnet !== undefined
  );

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
      if (!g) { g = { clave, etiqueta, subtotal: 0, filas: [] }; grupos.set(clave, g); }
      return g;
    };
    const merge = (g: GrupoLectura, firma: string, monto: number) => {
      const f = g.filas.find((x) => x.firma === firma);
      if (f) { f.cantidad += 1; f.monto += monto; }
      else g.filas.push({ firma, cantidad: 1, monto });
    };

    for (const op of this.ventas()) {
      const monto = op.monto ?? 0;
      const art = this.montoArticulos(op);
      const segs = op.segmentosEntrada ?? [];
      if (segs.length === 0 && monto === 0) continue; // venta 100% gratis: no se muestra
      const g = grupoDe(op.formaPago);
      g.subtotal += monto;
      for (const s of segs) merge(g, `${s.cantidad}x ${s.tipoNombre}`, s.monto);
      if (art > 0) merge(g, 'Artículos varios', art);
    }
    for (const g of grupos.values()) {
      g.filas.sort((a, b) => {
        if (a.firma === 'Artículos varios') return 1;
        if (b.firma === 'Artículos varios') return -1;
        return b.monto - a.monto;
      });
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

  /** Tipos elegibles para agregar venta: sólo entradas con precio > 0. */
  tiposPagos = computed(() =>
    this.tiposEntrada().filter((t) => t.tipo === 'ENTRADA' && t.precio > 0).sort((a, b) => a.nombre.localeCompare(b.nombre))
  );

  private efectivoBloqueado = computed(() => this.caja().huboVentaDolares);

  /** Todos los segmentos de entrada de ventas reales, con su forma normalizada. */
  private segmentos = computed<SegView[]>(() => {
    const out: SegView[] = [];
    for (const op of this.ventas()) {
      const col = this.formaAColumna(op.formaPago);
      if (!col || op.compraId === null) continue;
      (op.segmentosEntrada ?? []).forEach((s: SegmentoEntrada, i) => {
        out.push({
          id: `${op.compraId}:${i}`,
          compraId: op.compraId!,
          tipoId: s.tipoEntradaId,
          tipoNombre: s.tipoNombre,
          cantidad: s.cantidad,
          monto: s.monto,
          formaOriginal: col,
        });
      });
    }
    return out;
  });

  hayVentasAjustables = computed(() => this.segmentos().length > 0 || this.tiposPagos().length > 0);

  private segById(id: string): SegView | undefined {
    return this.segmentos().find((s) => s.id === id);
  }

  /**
   * Empareja lo sacado (−) con lo agregado (+) por tipo + tamaño de grupo:
   * cada par = una reubicación (la venta se cobró en una forma y se tocó otra);
   * los − sobrantes = ventas fantasma (quitar); los + sobrantes = ventas no registradas (agregar).
   */
  private pares = computed<{ reub: { seg: SegView; extra: VentaExtra }[]; quit: SegView[]; agr: VentaExtra[] }>(() => {
    const grupos = new Map<string, { rs: SegView[]; es: VentaExtra[] }>();
    const grupo = (tipoId: number, size: number) => {
      const k = `${tipoId}|${size}`;
      let g = grupos.get(k);
      if (!g) { g = { rs: [], es: [] }; grupos.set(k, g); }
      return g;
    };
    for (const id of this.removidos()) {
      const s = this.segById(id);
      if (s) grupo(s.tipoId, s.cantidad).rs.push(s);
    }
    for (const e of this.ventasExtra()) grupo(e.tipoId, e.cantidad).es.push(e);

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

  /** Ajustes ya aplicados que tocan una celda (tipoId, tamaño, forma). Clave: `tipoId:size:col`. */
  private aplicadoPorCelda = computed<Map<string, number>>(() => {
    const m = new Map<string, number>();
    const add = (tipoId: number, size: number, col: ColumnaClave | null, n: number) => {
      if (!col) return;
      const k = `${tipoId}:${size}:${col}`;
      m.set(k, (m.get(k) ?? 0) + n);
    };
    for (const a of this.caja().ajustes ?? []) {
      if (a.cantidadVentas <= 0) continue;
      const entries = Object.entries(a.lineas ?? {});
      if (entries.length !== 1) continue;
      const tipoId = Number(entries[0][0]);
      const size = Number(entries[0][1]);
      add(tipoId, size, this.formaAColumna(a.formaOrigen), -a.cantidadVentas);
      add(tipoId, size, this.formaAColumna(a.formaDestino), a.cantidadVentas);
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

    return this.tiposConMatriz().map(({ id: tipoId, nombre: tipoNombre }) => {
      const tSegs = segs.filter((s) => s.tipoId === tipoId);
      const tExtras = extras.filter((e) => e.tipoId === tipoId);
      const tAjustes = ajustes.filter((a) => Number(Object.keys(a.lineas!)[0]) === tipoId);

      const sizes = new Set<number>();
      for (const s of tSegs) sizes.add(s.cantidad);
      for (const e of tExtras) sizes.add(e.cantidad);
      for (const a of tAjustes) sizes.add(Number(Object.values(a.lineas!)[0]));

      const { reub, agr } = this.pares();

      const filas: FilaTipo[] = [...sizes].sort((a, b) => a - b).map((size) => {
        const sSegs = tSegs.filter((s) => s.cantidad === size);
        const reubAqui = reub.filter((p) => p.seg.tipoId === tipoId && p.seg.cantidad === size);
        const celdas: CeldaVista[] = this.columnas.map((col) => {
          const staying = sSegs.filter((s) => s.formaOriginal === col.clave && !this.removidos().has(s.id));
          const removedAqui = sSegs.filter((s) => s.formaOriginal === col.clave && this.removidos().has(s.id)).length;
          const extrasAqui = tExtras.filter((e) => e.cantidad === size && e.forma === col.clave).length;
          const aplicado = this.aplicadoPorCelda().get(`${tipoId}:${size}:${col.clave}`) ?? 0;
          const cantidad = staying.length + extrasAqui + aplicado;
          const ajuste = extrasAqui - removedAqui;
          const bloqueada = col.clave === 'EFECTIVO_BOLETERIA' && this.efectivoBloqueado();
          const tarifa = this.montoTarifa(tipoId, size, col.clave);
          // partes de esta celda que son un traslado (un − de acá que tiene su + en otra columna, o viceversa)
          const movidoDesde = reubAqui.filter((p) => p.seg.formaOriginal === col.clave && p.extra.forma !== col.clave).length;
          const movidoHacia = reubAqui.filter((p) => p.extra.forma === col.clave && p.seg.formaOriginal !== col.clave).length;
          const esTraslado = (movidoDesde > 0 || movidoHacia > 0) && removedAqui === movidoDesde && extrasAqui === movidoHacia;
          const entranteReub = reubAqui
            .filter((p) => p.extra.forma === col.clave)
            .reduce((acc, p) => acc + p.seg.monto, 0);
          const entranteAgr = agr.filter((e) => e.forma === col.clave && e.tipoId === tipoId && e.cantidad === size).length * tarifa;
          const monto =
            ajuste === 0 && aplicado === 0
              ? staying.reduce((acc, s) => acc + s.monto, 0)
              : staying.reduce((acc, s) => acc + s.monto, 0) + entranteReub + entranteAgr + aplicado * tarifa;
          return {
            clave: col.clave,
            cantidad,
            monto: cantidad <= 0 ? 0 : monto,
            ajuste,
            aplicado,
            esTraslado,
            puedeQuitar: (staying.length > 0 || extrasAqui > 0) && !bloqueada,
            puedeAgregar: true,
          };
        });
        return { cantidad: size, celdas };
      });
      return { tipoId, tipoNombre, filas };
    });
  });

  private deltas = computed<Record<ColumnaClave, number>>(() => {
    const d: Record<ColumnaClave, number> = { EFECTIVO_BOLETERIA: 0, TARJETA: 0, MERCADO_PAGO_QR: 0 };
    const { reub, quit, agr } = this.pares();
    for (const { seg, extra } of reub) {
      if (extra.forma === seg.formaOriginal) continue;
      d[extra.forma] += seg.monto;
      d[seg.formaOriginal] -= seg.monto;
    }
    for (const seg of quit) d[seg.formaOriginal] -= seg.monto;
    for (const e of agr) d[e.forma] += this.montoTarifa(e.tipoId, e.cantidad, e.forma);
    for (const a of this.agregados()) d[a.forma] += a.signo === 'AGREGAR' ? a.monto : -a.monto;
    return d;
  });

  totalesRevision = computed<TotalVista[]>(() => {
    const c = this.caja();
    const d = this.deltas();
    const combinado = this.combinadoPosnet();
    const filas: TotalVista[] = [];

    const efEsp = (c.efectivoEsperado ?? 0) + d.EFECTIVO_BOLETERIA;
    filas.push({ clave: 'EFECTIVO_BOLETERIA', etiqueta: 'Efectivo', esperado: efEsp, contado: c.montoContado ?? 0, diferencia: (c.montoContado ?? 0) - efEsp });

    const tarEsp = (c.totalVentasTarjeta ?? 0) + d.TARJETA;
    const qrEsp = (c.totalVentasQr ?? 0) + d.MERCADO_PAGO_QR;
    filas.push({
      clave: 'TARJETA', etiqueta: 'Tarjeta', esperado: tarEsp,
      contado: combinado ? null : (c.totalCerradoTarjeta ?? 0),
      diferencia: combinado ? null : (c.totalCerradoTarjeta ?? 0) - tarEsp,
    });
    filas.push({
      clave: 'MERCADO_PAGO_QR', etiqueta: 'QR', esperado: qrEsp,
      contado: combinado ? null : (c.totalCerradoQr ?? 0),
      diferencia: combinado ? null : (c.totalCerradoQr ?? 0) - qrEsp,
    });
    if (combinado) {
      const esp = tarEsp + qrEsp;
      filas.push({
        clave: 'POSNET', etiqueta: 'Tarjeta + QR (cerrado junto)', esperado: esp,
        contado: c.totalCerradoPosnet ?? 0, diferencia: (c.totalCerradoPosnet ?? 0) - esp,
      });
    }
    return filas;
  });

  entradasRevision = computed(() => {
    const c = this.caja();
    if (c.entradasFisicasEsperadas === null && c.totalEntradasPagas === null) return null;
    let dTal = 0;
    let dVen = 0;
    const aplica = (tipoId: number, cantidad: number, signo: number) => {
      const tipo = this.tiposEntrada().find((t) => t.id === tipoId);
      if (!tipo) return;
      if (tipo.entregaEntrada) dTal += signo * cantidad;
      if (tipo.tipo === 'ENTRADA' && tipo.precio > 0) dVen += signo * cantidad;
    };
    const { quit, agr } = this.pares();
    for (const seg of quit) aplica(seg.tipoId, seg.cantidad, -1);
    for (const e of agr) aplica(e.tipoId, e.cantidad, 1);
    // las reubicaciones no cambian el conteo de entradas (son las mismas ventas)

    const esperadasTalonario = (c.entradasFisicasEsperadas ?? 0) + dTal;
    const cortadas = c.entradasFisicasCortadas;
    return {
      vendidas: (c.totalEntradasPagas ?? 0) + dVen,
      esperadasTalonario,
      cortadas,
      diferenciaTalonario: cortadas === null ? null : esperadasTalonario - cortadas,
      cambio: dTal !== 0 || dVen !== 0,
    };
  });

  hayCambios = computed(
    () => this.removidos().size > 0 || this.ventasExtra().length > 0 || this.agregados().length > 0
  );

  /**
   * Chips resumen abajo de la matriz. Un − que tiene su + (mismo tipo + tamaño) se muestra como
   * un traslado (amarillo, "Tarjeta → Efectivo"); los − sueltos como "sacado" (rojo) y los +
   * sueltos como "agregado" (verde).
   */
  movimientos = computed<{
    traslados: { key: string; tipoNombre: string; cantidad: number; origen: ColumnaClave; destino: ColumnaClave; count: number; segIds: string[]; extraIds: number[] }[];
    sacados: { key: string; tipoNombre: string; cantidad: number; forma: ColumnaClave; count: number; ids: string[] }[];
    agregados: { key: string; tipoNombre: string; cantidad: number; forma: ColumnaClave; count: number; ids: number[] }[];
  }>(() => {
    const { reub, quit, agr } = this.pares();

    const tMap = new Map<string, { key: string; tipoNombre: string; cantidad: number; origen: ColumnaClave; destino: ColumnaClave; count: number; segIds: string[]; extraIds: number[] }>();
    for (const { seg, extra } of reub) {
      if (extra.forma === seg.formaOriginal) continue;
      const key = `${seg.tipoId}|${seg.cantidad}|${seg.formaOriginal}|${extra.forma}`;
      const g = tMap.get(key) ?? { key, tipoNombre: seg.tipoNombre, cantidad: seg.cantidad, origen: seg.formaOriginal, destino: extra.forma, count: 0, segIds: [], extraIds: [] };
      g.count++; g.segIds.push(seg.id); g.extraIds.push(extra.id);
      tMap.set(key, g);
    }

    const sMap = new Map<string, { key: string; tipoNombre: string; cantidad: number; forma: ColumnaClave; count: number; ids: string[] }>();
    for (const s of quit) {
      const key = `${s.tipoId}|${s.cantidad}|${s.formaOriginal}`;
      const g = sMap.get(key) ?? { key, tipoNombre: s.tipoNombre, cantidad: s.cantidad, forma: s.formaOriginal, count: 0, ids: [] };
      g.count++; g.ids.push(s.id);
      sMap.set(key, g);
    }

    const aMap = new Map<string, { key: string; tipoNombre: string; cantidad: number; forma: ColumnaClave; count: number; ids: number[] }>();
    for (const e of agr) {
      const key = `${e.tipoId}|${e.cantidad}|${e.forma}`;
      const g = aMap.get(key) ?? { key, tipoNombre: e.tipoNombre, cantidad: e.cantidad, forma: e.forma, count: 0, ids: [] };
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
    this.modoRevision.set(false);
    this.removidos.set(new Set());
    this.ventasExtra.set([]);
    this.agregados.set([]);
    this.montoSuelto.set(null);
    this.notaMontoSuelto.set('');
    this.nuevaVentaTipoId.set(null);
    this.nuevaVentaCantidad.set(null);
    this.errorRevision.set(null);
  }

  /** − en una celda: primero cancela un + que estuviera en esa celda; si no, saca una venta real. */
  quitar(tipoId: number, size: number, col: ColumnaClave): void {
    const idx = this.ventasExtra().findIndex((e) => e.tipoId === tipoId && e.cantidad === size && e.forma === col);
    if (idx >= 0) {
      this.ventasExtra.update((es) => es.filter((_, i) => i !== idx));
      return;
    }
    const reales = this.segmentos().filter(
      (s) => s.tipoId === tipoId && s.cantidad === size && s.formaOriginal === col && !this.removidos().has(s.id)
    );
    if (reales.length) {
      const r = new Set(this.removidos());
      r.add(reales[reales.length - 1].id);
      this.removidos.set(r);
    }
  }

  /** + en una celda: primero deshace un − que estuviera en esa celda; si no, agrega una venta acá. */
  agregar(tipoId: number, size: number, col: ColumnaClave): void {
    const removidoAqui = this.segmentos().find(
      (s) => s.tipoId === tipoId && s.cantidad === size && s.formaOriginal === col && this.removidos().has(s.id)
    );
    if (removidoAqui) {
      const r = new Set(this.removidos());
      r.delete(removidoAqui.id);
      this.removidos.set(r);
      return;
    }
    this.ventasExtra.update((es) => [
      ...es,
      { id: this.proximoExtraId++, tipoId, tipoNombre: this.nombreTipo(tipoId), cantidad: size, forma: col },
    ]);
  }

  agregarVenta(): void {
    const tipoId = this.nuevaVentaTipoId();
    const cantidad = this.nuevaVentaCantidad();
    if (tipoId === null) { this.errorRevision.set('Elegí el tipo de entrada.'); return; }
    if (cantidad === null || cantidad <= 0) { this.errorRevision.set('Indicá cuántos pases (ej. 3).'); return; }
    this.errorRevision.set(null);
    this.ventasExtra.update((es) => [
      ...es,
      { id: this.proximoExtraId++, tipoId, tipoNombre: this.nombreTipo(tipoId), cantidad, forma: this.nuevaVentaForma() },
    ]);
    this.nuevaVentaCantidad.set(null);
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
    const ajustes: AjusteCajaInput[] = [];
    const grupos = new Map<string, AjusteCajaInput>();

    const { reub, quit, agr } = this.pares();

    // Reubicaciones: la venta se cobró en una forma y se tocó otra en el POS.
    for (const { seg, extra } of reub) {
      const destino = extra.forma;
      if (destino === seg.formaOriginal) continue;
      const key = `R|${seg.tipoId}|${seg.cantidad}|${seg.formaOriginal}|${destino}`;
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

    // Ventas fantasma: se registraron de más (se sacaron sin agregar en otra forma).
    for (const seg of quit) {
      const key = `Q|${seg.tipoId}|${seg.cantidad}|${seg.formaOriginal}`;
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

    // Ventas que no se registraron → agregar (sólo destino).
    const extraGrupos = new Map<string, AjusteCajaInput>();
    for (const e of agr) {
      const key = `${e.tipoId}|${e.cantidad}|${e.forma}`;
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
      g.monto += Math.round(this.montoTarifa(e.tipoId, e.cantidad, e.forma));
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

    if (ajustes.length === 0) return;

    this.aplicando.set(true);
    this.errorRevision.set(null);
    this.cajaService.registrarAjustes(this.caja().id, ajustes).subscribe({
      next: (c) => {
        this.aplicando.set(false);
        this.salirRevision();
        this.cajaActualizada.emit(c);
      },
      error: (err) => {
        this.aplicando.set(false);
        this.errorRevision.set(typeof err?.error === 'string' ? err.error : 'No se pudo guardar el ajuste. Reintentá.');
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
