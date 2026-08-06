import { Component, inject, input, output, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CajaService, Caja, IngresoEntradas } from '../../Services/caja.service';

@Component({
  selector: 'app-ingreso-entradas-modal',
  imports: [FormsModule, DatePipe],
  templateUrl: './ingreso-entradas-modal.html',
  styleUrl: './ingreso-entradas-modal.css',
})
export class IngresoEntradasModal {
  private cajaService = inject(CajaService);

  ingresos = input<IngresoEntradas[]>([]);

  ingresoRegistrado = output<Caja>();
  cerrar = output<void>();

  cantidad = signal<number | null>(null);
  registrando = signal(false);
  error = signal<string | null>(null);

  confirmar(): void {
    const cantidad = this.cantidad();
    if (cantidad === null || cantidad <= 0) {
      this.error.set('Ingresá una cantidad mayor a cero.');
      return;
    }
    this.registrando.set(true);
    this.error.set(null);
    this.cajaService.registrarIngresoEntradas(cantidad).subscribe({
      next: (c) => {
        this.registrando.set(false);
        this.ingresoRegistrado.emit(c);
      },
      error: (err) => {
        this.error.set(typeof err?.error === 'string' ? err.error : 'No se pudo registrar el ingreso. Reintentá.');
        this.registrando.set(false);
      },
    });
  }
}
