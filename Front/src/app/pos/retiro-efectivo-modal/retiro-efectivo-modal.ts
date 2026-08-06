import { Component, inject, input, output, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CajaService, Caja, RetiroCaja } from '../../Services/caja.service';
import { MoneyInputDirective } from '../../shared/money-input/money-input.directive';

@Component({
  selector: 'app-retiro-efectivo-modal',
  imports: [CurrencyPipe, FormsModule, MoneyInputDirective],
  templateUrl: './retiro-efectivo-modal.html',
  styleUrl: './retiro-efectivo-modal.css',
})
export class RetiroEfectivoModal {
  private cajaService = inject(CajaService);

  retiros = input<RetiroCaja[]>([]);

  retiroRegistrado = output<Caja>();
  cerrar = output<void>();

  monto = signal<number | null>(null);
  motivo = signal('');
  registrando = signal(false);
  error = signal<string | null>(null);

  confirmar(): void {
    const monto = this.monto();
    const motivo = this.motivo().trim();
    if (monto === null || monto <= 0) {
      this.error.set('Ingresá un monto mayor a cero.');
      return;
    }
    if (!motivo) {
      this.error.set('Indicá el motivo del retiro.');
      return;
    }
    this.registrando.set(true);
    this.error.set(null);
    this.cajaService.registrarRetiro(monto, motivo).subscribe({
      next: (c) => {
        this.registrando.set(false);
        this.retiroRegistrado.emit(c);
      },
      error: (err) => {
        this.error.set(typeof err?.error === 'string' ? err.error : 'No se pudo registrar el retiro. Reintentá.');
        this.registrando.set(false);
      },
    });
  }
}
