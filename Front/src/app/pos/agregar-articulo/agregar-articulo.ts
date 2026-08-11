import { Component, computed, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ArticuloVario } from '../../models/articulo-vario';
import { FilaArticuloCarrito } from '../../models/venta-pos';
import { MoneyInputDirective } from '../../shared/money-input/money-input.directive';
import { PesosPipe } from '../../shared/pesos.pipe';

@Component({
  selector: 'app-agregar-articulo',
  imports: [FormsModule, MoneyInputDirective, PesosPipe],
  templateUrl: './agregar-articulo.html',
  styleUrl: './agregar-articulo.css',
})
export class AgregarArticulo {
  catalogo = input<ArticuloVario[]>([]);

  articuloAgregado = output<FilaArticuloCarrito>();

  catalogoActivo = computed(() => this.catalogo().filter((a) => a.activo));

  mostrarPanel = signal(false);
  /** Un id de artículo del catálogo, el sentinel 'libre' (opción "Otro" del mismo select), o null si no eligió nada. */
  seleccion = signal<number | 'libre' | null>(null);
  catalogoPrecio = signal<number | null>(null);
  libreDescripcion = signal('');
  librePrecio = signal<number | null>(null);
  cantidad = signal(1);

  articuloSeleccionado = computed<ArticuloVario | null>(() => {
    const s = this.seleccion();
    if (s === null || s === 'libre') return null;
    return this.catalogo().find((a) => a.id === s) ?? null;
  });

  /** Si el artículo de catálogo ya tiene un precio cargado, el cajero no lo puede tocar — sólo los "Otro" (sin catálogo) o los que todavía no tienen precio sugerido admiten precio libre. */
  precioBloqueado = computed(() => this.articuloSeleccionado()?.precioSugerido !== null && this.articuloSeleccionado() !== null);

  toggle(): void {
    this.mostrarPanel.update((v) => !v);
    this.seleccion.set(null);
    this.catalogoPrecio.set(null);
    this.libreDescripcion.set('');
    this.librePrecio.set(null);
    this.cantidad.set(1);
  }

  /** Al elegir un artículo del catálogo, precarga su precio sugerido (bloqueado si ya está definido — ver precioBloqueado). Elegir "Otro" no precarga nada: ahí aparece el campo de descripción libre. */
  elegirOpcion(valor: number | 'libre' | null): void {
    this.seleccion.set(valor);
    if (valor === 'libre' || valor === null) {
      this.catalogoPrecio.set(null);
      return;
    }
    const articulo = this.catalogo().find((a) => a.id === valor);
    this.catalogoPrecio.set(articulo?.precioSugerido ?? null);
  }

  confirmar(): void {
    const cantidad = this.cantidad();
    if (cantidad === null || cantidad < 1) return;

    const seleccion = this.seleccion();
    if (seleccion === 'libre') {
      const descripcion = this.libreDescripcion().trim();
      const precio = this.librePrecio();
      if (!descripcion || precio === null) return;
      this.articuloAgregado.emit({ articuloVarioId: null, descripcionLibre: descripcion, nombre: descripcion, precioUnitario: precio, cantidad });
    } else if (seleccion !== null) {
      const precio = this.catalogoPrecio();
      const articulo = this.catalogo().find((a) => a.id === seleccion);
      if (!articulo || precio === null) return;
      this.articuloAgregado.emit({ articuloVarioId: articulo.id, descripcionLibre: null, nombre: articulo.nombre, precioUnitario: precio, cantidad });
    } else {
      return;
    }

    this.toggle();
  }
}
