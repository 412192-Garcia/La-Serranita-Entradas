import { Component, OnInit, computed, effect, inject, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LucideMinus, LucidePlus, LucideTrash2 } from '@lucide/angular';
import { FacturaManualItem } from '../../models/factura';
import { TipoEntrada } from '../../models/tipo-entrada';
import { ArticuloVario } from '../../models/articulo-vario';
import { FacturaService } from '../../services/factura.service';
import { TipoEntradaService } from '../../services/tipo-entrada.service';
import { ArticuloVarioService } from '../../services/articulo-vario.service';
import { PesosPipe } from '../../shared/pesos.pipe';

/** Un botón del catálogo: `clave` es "e:<id>" (entrada) o "a:<id>" (artículo). */
interface ItemCatalogo {
  clave: string;
  tipo: 'ENTRADA' | 'ARTICULO';
  nombre: string;
  precio: number | null;
}

/** Un renglón de la venta que se está armando. */
interface LineaVenta {
  clave: string;
  tipo: 'ENTRADA' | 'ARTICULO';
  nombre: string;
  cantidad: number;
  precio: number | null;
}

/** Una venta del lote, repetida `veces` veces (varias ventas iguales = varias facturas iguales). */
interface VentaLote {
  id: number;
  lineas: LineaVenta[];
  veces: number;
}

/** Igual que el backend: alcanza para un día entero. */
const MAX_FACTURAS = 100;
/** Borrador del lote en esta compu: armar 40 ventas y perderlas por un F5 sería lo peor. */
const CLAVE_BORRADOR = 'factura-lote-borrador';

/**
 * Acciones > Facturación manual > En lote: al final del día, las ventas que quedaron sin facturar.
 * Se arma cada venta tocando los ítems del catálogo (cada toque suma uno), se agrega al lote las
 * veces que se repitió, y al final se emiten todas juntas: una Factura B por venta. No se
 * imprimen ni se mandan por mail (el cliente ya se fue); quedan los PDF en la lista.
 */
@Component({
  selector: 'app-facturacion-lote',
  imports: [FormsModule, PesosPipe, LucidePlus, LucideMinus, LucideTrash2],
  templateUrl: './facturacion-lote.html',
  styleUrls: ['../facturacion-manual/facturacion-manual.css', './facturacion-lote.css'],
})
export class FacturacionLote implements OnInit {
  private facturaService = inject(FacturaService);
  private tipoEntradaService = inject(TipoEntradaService);
  private articuloVarioService = inject(ArticuloVarioService);

  readonly habilitada = this.facturaService.habilitada;
  readonly maxFacturas = MAX_FACTURAS;

  /** Avisa al padre cuántas se pidieron, para que refresque la lista de abajo. */
  emitido = output<number>();

  catalogo = signal<ItemCatalogo[]>([]);
  venta = signal<LineaVenta[]>([]);
  veces = signal(1);
  lote = signal<VentaLote[]>(this.leerBorrador());
  private proximoId = Math.max(0, ...this.lote().map((v) => v.id)) + 1;

  confirmando = signal(false);
  emitiendo = signal(false);
  error = signal<string | null>(null);
  aviso = signal<string | null>(null);

  totalVenta = computed(() => this.totalDe(this.venta()));
  /** Los renglones sin cargo (un menor gratis) se pueden sumar para que la venta se lea como fue,
   * pero no van en la factura: ARCA no acepta renglones en $0. Hace falta algo cobrado. */
  ventaValida = computed(() =>
    this.venta().length > 0 && this.venta().every((l) => l.cantidad >= 1 && l.precio != null && l.precio >= 0) && this.totalVenta() > 0);
  cantidadFacturas = computed(() => this.lote().reduce((n, v) => n + v.veces, 0));
  totalLote = computed(() => this.lote().reduce((suma, v) => suma + v.veces * this.totalDe(v.lineas), 0));
  lugar = computed(() => MAX_FACTURAS - this.cantidadFacturas());

  constructor() {
    effect(() => {
      const lote = this.lote();
      try {
        if (lote.length) localStorage.setItem(CLAVE_BORRADOR, JSON.stringify(lote));
        else localStorage.removeItem(CLAVE_BORRADOR);
      } catch {
        // Sin storage: el borrador vale hasta recargar.
      }
    });
  }

  ngOnInit(): void {
    this.facturaService.actualizarEstadoServicio();
    let tipos: ItemCatalogo[] = [];
    let articulos: ItemCatalogo[] = [];
    const juntar = () => this.catalogo.set([...tipos, ...articulos]);
    this.tipoEntradaService.getTiposEntrada().subscribe({
      next: (t: TipoEntrada[]) => {
        tipos = t.filter((x) => x.activo).map((x) => ({ clave: 'e:' + x.id, tipo: 'ENTRADA', nombre: x.nombre, precio: x.precio }));
        juntar();
      },
      error: () => {},
    });
    this.articuloVarioService.getArticulos().subscribe({
      next: (a: ArticuloVario[]) => {
        articulos = a.filter((x) => x.activo).map((x) => ({ clave: 'a:' + x.id, tipo: 'ARTICULO', nombre: x.nombre, precio: x.precioSugerido }));
        juntar();
      },
      error: () => {},
    });
  }

  // ---------- venta en armado ----------

  sumar(item: ItemCatalogo): void {
    this.limpiarMensajes();
    this.venta.update((ls) => {
      const existe = ls.find((l) => l.clave === item.clave);
      if (existe) return ls.map((l) => (l === existe ? { ...l, cantidad: l.cantidad + 1 } : l));
      return [...ls, { clave: item.clave, tipo: item.tipo, nombre: item.nombre, cantidad: 1, precio: item.precio }];
    });
  }

  cambiarCantidad(clave: string, delta: number): void {
    this.venta.update((ls) =>
      ls.map((l) => (l.clave === clave ? { ...l, cantidad: l.cantidad + delta } : l)).filter((l) => l.cantidad > 0));
  }

  cambiarPrecio(clave: string, precio: number | null): void {
    this.venta.update((ls) => ls.map((l) => (l.clave === clave ? { ...l, precio } : l)));
  }

  cantidadEnVenta(clave: string): number {
    return this.venta().find((l) => l.clave === clave)?.cantidad ?? 0;
  }

  agregarAlLote(): void {
    const veces = Math.floor(Number(this.veces()) || 0);
    if (!this.ventaValida() || veces < 1) return;
    if (veces > this.lugar()) {
      this.error.set(`El lote admite hasta ${MAX_FACTURAS} facturas: quedan ${this.lugar()} lugares.`);
      return;
    }
    this.lote.update((l) => [...l, { id: this.proximoId++, lineas: this.venta(), veces }]);
    this.venta.set([]);
    this.veces.set(1);
    this.confirmando.set(false);
    this.limpiarMensajes();
  }

  quitarDelLote(id: number): void {
    this.lote.update((l) => l.filter((v) => v.id !== id));
    this.confirmando.set(false);
  }

  vaciarLote(): void {
    this.lote.set([]);
    this.confirmando.set(false);
  }

  // ---------- emitir ----------

  pedirConfirmacion(): void {
    this.limpiarMensajes();
    if (this.cantidadFacturas() > 0) this.confirmando.set(true);
  }

  emitir(): void {
    if (this.emitiendo() || this.cantidadFacturas() === 0) return;
    this.emitiendo.set(true);
    this.error.set(null);
    const facturas: FacturaManualItem[][] = [];
    for (const v of this.lote()) {
      const items = v.lineas.filter((l) => this.subtotal(l) > 0).map((l) => ({ tipo: l.tipo, cantidad: l.cantidad, descripcion: l.nombre, subtotal: this.subtotal(l) }));
      for (let i = 0; i < v.veces; i++) facturas.push(items);
    }
    this.facturaService.emitirManualLote(facturas).subscribe({
      next: (r) => {
        this.emitiendo.set(false);
        this.confirmando.set(false);
        this.lote.set([]);
        this.aviso.set(`${r.length} ${r.length === 1 ? 'factura pedida' : 'facturas pedidas'}: en unos segundos están los PDF en la lista de abajo.`);
        this.emitido.emit(r.length);
      },
      error: (err) => {
        this.emitiendo.set(false);
        this.confirmando.set(false);
        this.error.set(this.conVenta(this.mensaje(err, 'No se pudo emitir el lote. No se pidió ninguna factura.')));
      },
    });
  }

  // ---------- auxiliares ----------

  subtotal(l: LineaVenta): number {
    return l.precio != null && l.precio > 0 && l.cantidad > 0 ? Math.round(l.cantidad * l.precio * 100) / 100 : 0;
  }

  totalDe(lineas: LineaVenta[]): number {
    return lineas.reduce((suma, l) => suma + this.subtotal(l), 0);
  }

  /** "2× General, 1× Menú Almuerzo". */
  resumen(lineas: LineaVenta[]): string {
    return lineas.map((l) => `${l.cantidad}× ${l.nombre}`).join(', ');
  }

  /** El servidor numera las facturas ya repetidas ("Factura 7 del lote"); acá se ven agrupadas,
   * así que se traduce a la venta de la lista. */
  private conVenta(mensaje: string): string {
    const m = /^Factura (\d+) del lote: (.*)$/s.exec(mensaje);
    if (!m) return mensaje;
    let resto = Number(m[1]);
    const ventas = this.lote();
    for (let i = 0; i < ventas.length; i++) {
      resto -= ventas[i].veces;
      if (resto <= 0) return `Venta ${i + 1} del lote: ${m[2].replace(/\.?$/, '.')} No se pidió ninguna factura.`;
    }
    return mensaje;
  }

  private limpiarMensajes(): void {
    this.error.set(null);
    this.aviso.set(null);
  }

  private leerBorrador(): VentaLote[] {
    try {
      const crudo = localStorage.getItem(CLAVE_BORRADOR);
      const lote = crudo ? (JSON.parse(crudo) as VentaLote[]) : [];
      return Array.isArray(lote) ? lote.filter((v) => Array.isArray(v?.lineas) && v.veces >= 1) : [];
    } catch {
      return [];
    }
  }

  private mensaje(err: unknown, porDefecto: string): string {
    const e = err as { error?: unknown; status?: number };
    if (typeof e?.error === 'string' && e.error.trim()) return e.error;
    if (e?.status === 0) return 'No se pudo contactar con el servidor.';
    return porDefecto;
  }
}
