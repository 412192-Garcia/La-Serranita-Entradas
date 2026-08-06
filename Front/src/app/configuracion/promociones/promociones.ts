import { Component, OnInit, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PromocionService } from '../../Services/promocion.service';
import { Promocion } from '../../models/promocion';
import { MoneyInputDirective } from '../../shared/money-input/money-input.directive';

@Component({
  selector: 'app-configuracion-promociones',
  imports: [FormsModule, CurrencyPipe, MoneyInputDirective],
  templateUrl: './promociones.html',
  styleUrls: ['../configuracion-shared.css', './promociones.css'],
})
export class ConfiguracionPromociones implements OnInit {
  private promocionService = inject(PromocionService);

  promociones = signal<Promocion[]>([]);
  cargando = signal(false);
  error = signal<string | null>(null);

  editandoId = signal<number | null>(null);
  nombre = signal('');
  tipoDescuento = signal<'porcentaje' | 'monto'>('porcentaje');
  valor = signal<number | null>(null);
  guardando = signal(false);

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.cargando.set(true);
    this.promocionService.getPromociones().subscribe({
      next: (ps) => {
        this.promociones.set(ps);
        this.cargando.set(false);
      },
      error: (err) => {
        console.error('Error al cargar las promociones:', err);
        this.cargando.set(false);
      },
    });
  }

  editar(p: Promocion): void {
    this.editandoId.set(p.id);
    this.nombre.set(p.nombre);
    this.tipoDescuento.set(p.montoDescuento !== null ? 'monto' : 'porcentaje');
    this.valor.set(p.montoDescuento !== null ? p.montoDescuento : p.porcentajeDescuento);
    this.error.set(null);
  }

  cancelarEdicion(): void {
    this.editandoId.set(null);
    this.nombre.set('');
    this.tipoDescuento.set('porcentaje');
    this.valor.set(null);
    this.error.set(null);
  }

  guardar(): void {
    if (this.guardando()) return;
    if (!this.nombre().trim() || this.valor() === null) {
      this.error.set('Completá nombre y valor del descuento.');
      return;
    }
    this.guardando.set(true);
    this.error.set(null);

    const payload = {
      nombre: this.nombre().trim(),
      porcentajeDescuento: this.tipoDescuento() === 'porcentaje' ? this.valor() : null,
      montoDescuento: this.tipoDescuento() === 'monto' ? this.valor() : null,
    };

    const idEditando = this.editandoId();
    const request = idEditando
      ? this.promocionService.actualizar(idEditando, payload)
      : this.promocionService.crear(payload);

    request.subscribe({
      next: (guardada) => {
        this.promociones.update((ps) => {
          const existe = ps.some((p) => p.id === guardada.id);
          return existe ? ps.map((p) => (p.id === guardada.id ? guardada : p)) : [guardada, ...ps];
        });
        this.cancelarEdicion();
        this.guardando.set(false);
      },
      error: (err) => {
        console.error('Error al guardar la promoción:', err);
        this.error.set(typeof err?.error === 'string' ? err.error : 'No se pudo guardar la promoción.');
        this.guardando.set(false);
      },
    });
  }

  toggleActivo(p: Promocion): void {
    if (p.activo) {
      this.promocionService.eliminar(p.id).subscribe({
        next: () => this.promociones.update((ps) => ps.map((x) => (x.id === p.id ? { ...x, activo: false } : x))),
        error: (err) => {
          console.error('Error al desactivar la promoción:', err);
          this.error.set('No se pudo desactivar la promoción.');
        },
      });
    } else {
      this.promocionService.actualizar(p.id, { ...p, activo: true }).subscribe({
        next: (actualizada) => this.promociones.update((ps) => ps.map((x) => (x.id === p.id ? actualizada : x))),
        error: (err) => {
          console.error('Error al reactivar la promoción:', err);
          this.error.set('No se pudo reactivar la promoción.');
        },
      });
    }
  }
}
