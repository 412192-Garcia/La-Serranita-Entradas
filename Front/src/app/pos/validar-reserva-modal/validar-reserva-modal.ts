import { Component, inject, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { BoleteriaService, Reserva } from '../../services/boleteria.service';
import { Modal } from '../../shared/modal/modal';
import { PesosPipe } from '../../shared/pesos.pipe';

@Component({
  selector: 'app-validar-reserva-modal',
  imports: [PesosPipe, FormsModule, Modal],
  templateUrl: './validar-reserva-modal.html',
  styleUrl: './validar-reserva-modal.css',
})
export class ValidarReservaModal {
  private boleteriaService = inject(BoleteriaService);

  cerrar = output<void>();

  textoBusqueda = signal('');
  buscando = signal(false);
  error = signal<string | null>(null);
  resultados = signal<Reserva[] | null>(null);
  private procesandoId = signal<number | null>(null);

  buscar(): void {
    const texto = this.textoBusqueda().trim();
    if (!texto || this.buscando()) return;

    this.buscando.set(true);
    this.error.set(null);

    this.boleteriaService.buscar({ texto, tipo: 'ANTICIPADA', page: 0, size: 10 }).subscribe({
      next: (pagina) => {
        this.resultados.set(pagina.content);
        this.buscando.set(false);
      },
      error: (err) => {
        console.error('Error al buscar la reserva:', err);
        this.error.set('No se pudo buscar. Revisá la conexión y reintentá.');
        this.buscando.set(false);
      },
    });
  }

  procesando(reserva: Reserva): boolean {
    return this.procesandoId() === reserva.id;
  }

  /** Compra ya pagada online (APROBADO) → habilita el ingreso. */
  validarReserva(reserva: Reserva): void {
    this.ejecutarAccion(reserva, this.boleteriaService.validarIngreso(reserva.id));
  }

  /** Reserva con pago en efectivo (RESERVADO_EFECTIVO) → cobra y habilita el ingreso. */
  cobrarReserva(reserva: Reserva): void {
    this.ejecutarAccion(reserva, this.boleteriaService.cobrarEfectivoYValidar(reserva.id));
  }

  private ejecutarAccion(reserva: Reserva, accion: Observable<Reserva>): void {
    this.procesandoId.set(reserva.id);
    this.error.set(null);

    accion.subscribe({
      next: (actualizada) => {
        this.resultados.update((rs) => (rs ?? []).map((r) => (r.id === actualizada.id ? actualizada : r)));
        this.procesandoId.set(null);
      },
      error: (err) => {
        console.error('Error al validar la reserva:', err);
        this.error.set(typeof err?.error === 'string' ? err.error : 'No se pudo completar la validación. Reintentá.');
        this.procesandoId.set(null);
      },
    });
  }
}
