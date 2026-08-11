import { Component, inject, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CajaService, Caja } from '../../services/caja.service';
import { MoneyInputDirective } from '../../shared/money-input/money-input.directive';
import { LucideLockOpen } from '@lucide/angular';

@Component({
  selector: 'app-apertura-caja',
  imports: [FormsModule, MoneyInputDirective, LucideLockOpen],
  templateUrl: './apertura-caja.html',
  styleUrl: './apertura-caja.css',
})
export class AperturaCaja {
  private cajaService = inject(CajaService);

  cajaAbierta = output<Caja>();

  montoApertura = signal<number | null>(null);
  entradasFisicasApertura = signal<number | null>(null);
  abriendoCaja = signal(false);
  errorApertura = signal<string | null>(null);

  abrirCaja(): void {
    const monto = this.montoApertura();
    const entradasFisicas = this.entradasFisicasApertura();
    if (monto === null || monto < 0) {
      this.errorApertura.set('Ingresá el efectivo con el que arranca la caja.');
      return;
    }
    if (entradasFisicas === null || entradasFisicas < 0) {
      this.errorApertura.set('Indicá con cuántas entradas arranca el turno.');
      return;
    }
    this.abriendoCaja.set(true);
    this.errorApertura.set(null);
    this.cajaService.abrir(monto, entradasFisicas).subscribe({
      next: (c) => {
        this.abriendoCaja.set(false);
        this.cajaAbierta.emit(c);
      },
      error: (err) => {
        this.errorApertura.set(typeof err?.error === 'string' ? err.error : 'No se pudo abrir la caja. Reintentá.');
        this.abriendoCaja.set(false);
      },
    });
  }
}
