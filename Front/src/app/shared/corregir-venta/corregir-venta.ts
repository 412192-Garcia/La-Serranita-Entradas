import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PesosPipe } from '../pesos.pipe';
import { VentaFactura } from '../../models/factura';
import { FilaArticuloCarrito } from '../../models/venta-pos';
import { FormaPagoPos } from '../../models/compra';
import { FORMAS_PAGO } from '../../models/forma-pago';
import { TipoEntrada } from '../../models/tipo-entrada';
import { ArticuloVario } from '../../models/articulo-vario';
import { BoleteriaService } from '../../services/boleteria.service';
import { TipoEntradaService } from '../../services/tipo-entrada.service';
import { ArticuloVarioService } from '../../services/articulo-vario.service';
import { AgregarArticulo } from '../../pos/agregar-articulo/agregar-articulo';
import { MoneyInputDirective } from '../money-input/money-input.directive';

/** Lo que hace falta de una venta para corregirla (lo trae Ventas del turno o el detalle de una caja). */
export type VentaACorregir = Pick<VentaFactura,
  'compraId' | 'codigoReserva' | 'montoTotal' | 'lineas' | 'formaPago' | 'formaPagoSecundaria' | 'montoFormaPagoSecundaria' | 'corregibleHasta'>;

interface LineaEntrada {
  tipoEntradaId: number | null;
  cantidad: number;
}

/** Corregir lo vendido y la forma de pago de una venta de puerta, dentro de su tarjeta en el historial. */
@Component({
  selector: 'app-corregir-venta',
  imports: [DatePipe, FormsModule, PesosPipe, AgregarArticulo, MoneyInputDirective],
  templateUrl: './corregir-venta.html',
  styleUrl: './corregir-venta.css',
})
export class CorregirVenta implements OnInit {
  private boleteriaService = inject(BoleteriaService);
  private tipoEntradaService = inject(TipoEntradaService);
  private articuloVarioService = inject(ArticuloVarioService);

  venta = input.required<VentaACorregir>();
  /** Línea arriba que la separa del resto de la tarjeta; en un modal no hace falta. */
  conSeparador = input(true);
  cerrar = output<void>();
  guardado = output<void>();

  readonly formasPago = FORMAS_PAGO;

  tiposEntrada = signal<TipoEntrada[]>([]);
  catalogoArticulos = signal<ArticuloVario[]>([]);
  entradas = signal<LineaEntrada[]>([]);
  articulos = signal<FilaArticuloCarrito[]>([]);
  formaPago = signal<FormaPagoPos>('EFECTIVO_BOLETERIA');
  /** Pago con dos formas: la primera es formaPago y se lleva el resto del total. */
  dosFormas = signal(false);
  formaSecundaria = signal<FormaPagoPos>('TARJETA');
  montoSecundaria = signal<number | null>(null);
  guardando = signal(false);
  error = signal<string | null>(null);

  /** Un tipo que ya no está activo pero era parte de la venta sigue apareciendo en su línea. */
  opcionesEntrada = computed(() => {
    const activos = this.tiposEntrada();
    const ids = new Set(activos.map((t) => t.id));
    const viejos = this.venta().lineas
      .filter((l) => l.tipoEntradaId !== null && !ids.has(l.tipoEntradaId))
      .map((l) => ({ id: l.tipoEntradaId!, nombre: l.nombre }));
    return [...activos.map((t) => ({ id: t.id, nombre: t.nombre })), ...viejos];
  });

  ngOnInit(): void {
    const v = this.venta();
    this.entradas.set(v.lineas.filter((l) => l.tipoEntradaId !== null)
      .map((l) => ({ tipoEntradaId: l.tipoEntradaId, cantidad: l.cantidad })));
    this.articulos.set(v.lineas.filter((l) => l.tipoEntradaId === null).map((l) => ({
      articuloVarioId: l.articuloVarioId,
      descripcionLibre: l.articuloVarioId === null ? l.nombre : null,
      nombre: l.nombre,
      precioUnitario: l.precioUnitario ?? 0,
      cantidad: l.cantidad,
    })));
    const forma = FORMAS_PAGO.find((f) => f.valor === v.formaPago);
    if (forma) this.formaPago.set(forma.valor);
    const secundaria = FORMAS_PAGO.find((f) => f.valor === v.formaPagoSecundaria);
    if (secundaria && v.montoFormaPagoSecundaria) {
      this.dosFormas.set(true);
      this.formaSecundaria.set(secundaria.valor);
      this.montoSecundaria.set(v.montoFormaPagoSecundaria);
    }

    this.tipoEntradaService.getTiposEntrada().subscribe({
      next: (tipos) => this.tiposEntrada.set(tipos.filter((t) => t.tipo === 'ENTRADA' && t.activo)),
      error: (err) => console.error('No se pudieron traer los tipos de entrada:', err),
    });
    this.articuloVarioService.getArticulos().subscribe({
      next: (a) => this.catalogoArticulos.set(a),
      error: (err) => console.error('No se pudo traer el catálogo de artículos:', err),
    });
  }

  agregarEntrada(): void {
    this.entradas.update((ls) => [...ls, { tipoEntradaId: null, cantidad: 1 }]);
  }

  cambiarTipo(i: number, id: number | null): void {
    this.entradas.update((ls) => ls.map((l, j) => (j === i ? { ...l, tipoEntradaId: id } : l)));
  }

  sumar(i: number, delta: number): void {
    this.entradas.update((ls) => ls.map((l, j) => (j === i ? { ...l, cantidad: Math.max(1, l.cantidad + delta) } : l)));
  }

  quitarEntrada(i: number): void {
    this.entradas.update((ls) => ls.filter((_, j) => j !== i));
  }

  agregarArticulo(fila: FilaArticuloCarrito): void {
    this.articulos.update((a) => [...a, fila]);
  }

  quitarArticulo(i: number): void {
    this.articulos.update((a) => a.filter((_, j) => j !== i));
  }

  guardar(): void {
    const entradas = this.entradas();
    const articulos = this.articulos();
    if (entradas.length === 0 && articulos.length === 0) {
      this.error.set('La venta tiene que tener al menos una entrada o un artículo. Si no va, cancelala.');
      return;
    }
    if (entradas.some((l) => l.tipoEntradaId === null)) {
      this.error.set('Elegí el tipo de cada entrada.');
      return;
    }
    const dos = this.dosFormas();
    if (dos && !(this.montoSecundaria()! > 0)) {
      this.error.set('Poné cuánto se pagó con la segunda forma.');
      return;
    }
    this.guardando.set(true);
    this.error.set(null);
    this.boleteriaService.editarVenta(this.venta().compraId, {
      entradas: entradas.map((l) => ({ tipoEntradaId: l.tipoEntradaId!, cantidad: l.cantidad })),
      articulos: articulos.map((a) => ({
        articuloVarioId: a.articuloVarioId,
        descripcionLibre: a.descripcionLibre,
        precioUnitario: a.precioUnitario,
        cantidad: a.cantidad,
      })),
      formaPago: this.formaPago(),
      formaPagoSecundaria: dos ? this.formaSecundaria() : null,
      montoFormaPagoSecundaria: dos ? this.montoSecundaria() : null,
    }).subscribe({
      next: () => {
        this.guardando.set(false);
        this.guardado.emit();
      },
      error: (err) => {
        this.guardando.set(false);
        this.error.set(typeof err?.error === 'string' && err.error ? err.error : 'No se pudo guardar. Reintentá.');
      },
    });
  }
}
