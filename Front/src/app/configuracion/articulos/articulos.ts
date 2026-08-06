import { Component, OnInit, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ArticuloVarioService } from '../../Services/articulo-vario.service';
import { ArticuloVario } from '../../models/articulo-vario';
import { MoneyInputDirective } from '../../shared/money-input/money-input.directive';

@Component({
  selector: 'app-configuracion-articulos',
  imports: [FormsModule, CurrencyPipe, MoneyInputDirective],
  templateUrl: './articulos.html',
  styleUrls: ['../configuracion-shared.css', './articulos.css'],
})
export class ConfiguracionArticulos implements OnInit {
  private articuloVarioService = inject(ArticuloVarioService);

  articulos = signal<ArticuloVario[]>([]);
  cargando = signal(false);
  error = signal<string | null>(null);

  editandoId = signal<number | null>(null);
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
    this.editandoId.set(a.id);
    this.nombre.set(a.nombre);
    this.precioSugerido.set(a.precioSugerido);
    this.error.set(null);
  }

  cancelarEdicion(): void {
    this.editandoId.set(null);
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
