import { Component, inject, input, output, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CajaService, Caja, RetiroCaja, TipoMovimientoCaja } from '../../Services/caja.service';
import { MoneyInputDirective } from '../money-input/money-input.directive';

@Component({
  selector: 'app-retiro-efectivo-modal',
  imports: [CurrencyPipe, FormsModule, MoneyInputDirective],
  templateUrl: './retiro-efectivo-modal.html',
  styleUrl: './retiro-efectivo-modal.css',
})
export class RetiroEfectivoModal {
  private cajaService = inject(CajaService);

  retiros = input<RetiroCaja[]>([]);
  /** Si viene seteada, el movimiento se registra en la caja de OTRO usuario vía admin (usado desde el cierre en Cajas); si no, en la propia caja abierta (uso normal en POS). */
  cajaId = input<number | null>(null);

  retiroRegistrado = output<Caja>();
  cerrar = output<void>();

  tipo = signal<TipoMovimientoCaja>('RETIRO');
  monto = signal<number | null>(null);
  motivo = signal('');
  registrando = signal(false);
  error = signal<string | null>(null);

  confirmar(): void {
    const monto = this.monto();
    const motivo = this.motivo().trim();
    const tipo = this.tipo();
    if (monto === null || monto <= 0) {
      this.error.set('Ingresá un monto mayor a cero.');
      return;
    }
    if (!motivo) {
      this.error.set(tipo === 'APORTE' ? 'Indicá el motivo del aporte.' : 'Indicá el motivo del retiro.');
      return;
    }
    this.registrando.set(true);
    this.error.set(null);
    const cajaId = this.cajaId();
    const request = cajaId !== null
      ? this.cajaService.registrarRetiroComoAdmin(cajaId, monto, motivo, tipo)
      : this.cajaService.registrarRetiro(monto, motivo, tipo);
    request.subscribe({
      next: (c) => {
        this.registrando.set(false);
        this.monto.set(null);
        this.motivo.set('');
        this.retiroRegistrado.emit(c);
      },
      error: (err) => {
        this.error.set(typeof err?.error === 'string' ? err.error : 'No se pudo registrar el movimiento. Reintentá.');
        this.registrando.set(false);
      },
    });
  }
}
