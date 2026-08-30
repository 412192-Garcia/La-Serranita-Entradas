import { Component, computed, inject, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CajaService, Caja } from '../../services/caja.service';
import { MoneyInputDirective } from '../../shared/money-input/money-input.directive';
import { PesosPipe } from '../../shared/pesos.pipe';
import { LucideLockOpen } from '@lucide/angular';

/** Billetes de curso legal actual en Argentina (sin monedas). Mismo listado que el cierre de caja. */
const DENOMINACIONES = [100, 200, 500, 1000, 2000, 10000, 20000];

@Component({
  selector: 'app-apertura-caja',
  imports: [FormsModule, MoneyInputDirective, PesosPipe, LucideLockOpen],
  templateUrl: './apertura-caja.html',
  styleUrl: './apertura-caja.css',
})
export class AperturaCaja {
  private cajaService = inject(CajaService);

  cajaAbierta = output<Caja>();

  readonly denominaciones = DENOMINACIONES;
  /** Cuántos billetes de cada denominación hay en la caja al arrancar el turno. */
  conteoEfectivo = signal<Record<number, number>>({});
  /** Total en billetes chicos (50, 20, etc.), cargado de una vez en vez de billete por billete. */
  cambioApertura = signal<number | null>(null);
  entradasFisicasApertura = signal<number | null>(null);
  abriendoCaja = signal(false);
  errorApertura = signal<string | null>(null);

  /** Efectivo inicial calculado en vivo a partir del conteo por denominación más el cambio. */
  montoAperturaCalculado = computed(() =>
    this.denominaciones.reduce((acc, d) => acc + d * (this.conteoEfectivo()[d] ?? 0), 0) + (this.cambioApertura() ?? 0)
  );

  setConteoEfectivo(denominacion: number, cantidad: number): void {
    const valor = Math.max(0, Math.floor(cantidad) || 0);
    this.conteoEfectivo.update((c) => ({ ...c, [denominacion]: valor }));
  }

  abrirCaja(): void {
    const entradasFisicas = this.entradasFisicasApertura();
    if (entradasFisicas === null || entradasFisicas < 0) {
      this.errorApertura.set('Indicá con cuántas entradas arranca el turno.');
      return;
    }
    this.abriendoCaja.set(true);
    this.errorApertura.set(null);
    this.cajaService.abrir(this.montoAperturaCalculado(), entradasFisicas).subscribe({
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
