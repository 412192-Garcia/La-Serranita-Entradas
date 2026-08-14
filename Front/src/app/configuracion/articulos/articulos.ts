import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ArticuloVarioService } from '../../services/articulo-vario.service';
import { ArticuloVario } from '../../models/articulo-vario';
import { MoneyInputDirective } from '../../shared/money-input/money-input.directive';
import { compararTexto, compararNumero, compararBooleano } from '../../shared/orden.util';
import { Spinner } from '../../shared/spinner/spinner';
import { crearOrdenable } from '../../shared/ordenable';
import { crearEstadoEdicion } from '../../shared/edicion.util';
import { ColumnaOrdenable } from '../../shared/columna-ordenable/columna-ordenable';
import { PesosPipe } from '../../shared/pesos.pipe';

type CampoOrdenArticulo = 'nombre' | 'precioSugerido' | 'estado';

@Component({
  selector: 'app-configuracion-articulos',
  imports: [FormsModule, PesosPipe, MoneyInputDirective, Spinner, ColumnaOrdenable],
  templateUrl: './articulos.html',
  styleUrls: ['../configuracion-shared.css', './articulos.css'],
})
export class ConfiguracionArticulos implements OnInit {
  private articuloVarioService = inject(ArticuloVarioService);

  articulos = signal<ArticuloVario[]>([]);
  cargando = signal(false);
  error = signal<string | null>(null);

  mostrarInactivos = signal(false);
  private orden = crearOrdenable<CampoOrdenArticulo>('nombre');
  ordenarColumna = this.orden.ordenarColumna;
  estadoOrden = this.orden.estadoOrden;

  /** Con muchos artículos desactivados con el tiempo, mezclarlos todos vuelve la lista inmanejable: por defecto se ocultan. */
  articulosVisibles = computed(() => {
    const base = this.mostrarInactivos() ? this.articulos() : this.articulos().filter((a) => a.activo);
    const dir = this.orden.direccionOrden() === 'ASC' ? 1 : -1;
    const campo = this.orden.ordenarPor();
    return [...base].sort((a, b) => {
      switch (campo) {
        case 'nombre':
          return compararTexto(a.nombre, b.nombre) * dir;
        case 'precioSugerido':
          return compararNumero(a.precioSugerido, b.precioSugerido) * dir;
        case 'estado':
          return compararBooleano(a.activo, b.activo) * dir;
      }
    });
  });

  private edicion = crearEstadoEdicion<number>();
  editandoId = this.edicion.editandoId;
  nombre = signal('');
  precioSugerido = signal<number | null>(null);
  guardando = signal(false);

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.cargando.set(true);
    this.articuloVarioService.getArticulos().subscribe({
      next: (as) => {
        this.articulos.set(as);
        this.cargando.set(false);
      },
      error: (err) => {
        console.error('Error al cargar los artículos varios:', err);
        this.cargando.set(false);
      },
    });
  }

  editar(a: ArticuloVario): void {
    this.edicion.editar(a.id);
    this.nombre.set(a.nombre);
    this.precioSugerido.set(a.precioSugerido);
    this.error.set(null);
  }

  cancelarEdicion(): void {
    this.edicion.cancelarEdicion();
    this.nombre.set('');
    this.precioSugerido.set(null);
    this.error.set(null);
  }

  guardar(): void {
    if (this.guardando()) return;
    if (!this.nombre().trim()) {
      this.error.set('Completá el nombre del artículo.');
      return;
    }
    this.guardando.set(true);
    this.error.set(null);

    const payload = {
      nombre: this.nombre().trim(),
      precioSugerido: this.precioSugerido(),
    };

    const idEditando = this.editandoId();
    const request = idEditando
      ? this.articuloVarioService.actualizar(idEditando, payload)
      : this.articuloVarioService.crear(payload);

    request.subscribe({
      next: (guardado) => {
        this.articulos.update((as) => {
          const existe = as.some((a) => a.id === guardado.id);
          return existe ? as.map((a) => (a.id === guardado.id ? guardado : a)) : [guardado, ...as];
        });
        this.cancelarEdicion();
        this.guardando.set(false);
      },
      error: (err) => {
        console.error('Error al guardar el artículo:', err);
        this.error.set(typeof err?.error === 'string' ? err.error : 'No se pudo guardar el artículo.');
        this.guardando.set(false);
      },
    });
  }

  toggleActivo(a: ArticuloVario): void {
    if (a.activo) {
      this.articuloVarioService.eliminar(a.id).subscribe({
        next: () => this.articulos.update((as) => as.map((x) => (x.id === a.id ? { ...x, activo: false } : x))),
        error: (err) => {
          console.error('Error al desactivar el artículo:', err);
          this.error.set('No se pudo desactivar el artículo.');
        },
      });
    } else {
      this.articuloVarioService.actualizar(a.id, { ...a, activo: true }).subscribe({
        next: (actualizado) => this.articulos.update((as) => as.map((x) => (x.id === a.id ? actualizado : x))),
        error: (err) => {
          console.error('Error al reactivar el artículo:', err);
          this.error.set('No se pudo reactivar el artículo.');
        },
      });
    }
  }
}
