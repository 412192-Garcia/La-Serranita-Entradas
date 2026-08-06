import { Component, computed, inject, output, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CajaService, Caja, CierrePosnetInput, FormaPagoPosnet } from '../../Services/caja.service';
import { MoneyInputDirective } from '../../shared/money-input/money-input.directive';

/** Billetes de curso legal actual en Argentina (sin monedas). */
const DENOMINACIONES = [100, 200, 500, 1000, 2000, 10000, 20000];

/** Una fila de cierre de posnet dentro de su columna (Tarjeta o QR ya está implícito en cuál signal la contiene). */
interface FilaMontoNota {
  monto: number | null;
  nota: string;
}

@Component({
  selector: 'app-cierre-caja-modal',
  imports: [CurrencyPipe, FormsModule, MoneyInputDirective],
  templateUrl: './cierre-caja-modal.html',
  styleUrl: './cierre-caja-modal.css',
})
export class CierreCajaModal {
  private cajaService = inject(CajaService);

  cajaCerrada = output<Caja>();
  cerrar = output<void>();

  readonly denominaciones = DENOMINACIONES;
  /** Cuántos billetes de cada denominación contó el boletero al cerrar. */
  conteoEfectivo = signal<Record<number, number>>({});
  /** Arrancan con una fila vacía cada una: lo normal es tener al menos un cierre de cada posnet. */
  cierresTarjeta = signal<FilaMontoNota[]>([{ monto: null, nota: '' }]);
  cierresQr = signal<FilaMontoNota[]>([{ monto: null, nota: '' }]);
  entradasFisicasFinal = signal<number | null>(null);
  cerrandoCaja = signal(false);
  errorCierre = signal<string | null>(null);

  /** Total contado en efectivo, calculado en vivo a partir del conteo por denominación. */
  montoContadoCalculado = computed(() =>
    this.denominaciones.reduce((acc, d) => acc + d * (this.conteoEfectivo()[d] ?? 0), 0)
  );

  setConteoEfectivo(denominacion: number, cantidad: number): void {
    const valor = Math.max(0, Math.floor(cantidad) || 0);
    this.conteoEfectivo.update((c) => ({ ...c, [denominacion]: valor }));
  }

  private filasCierre(tipo: FormaPagoPosnet) {
    return tipo === 'TARJETA' ? this.cierresTarjeta : this.cierresQr;
  }

  agregarCierre(tipo: FormaPagoPosnet): void {
    this.filasCierre(tipo).update((c) => [...c, { monto: null, nota: '' }]);
  }

  quitarCierre(tipo: FormaPagoPosnet, index: number): void {
    this.filasCierre(tipo).update((c) => c.filter((_, i) => i !== index));
  }

  actualizarCierre(tipo: FormaPagoPosnet, index: number, cambios: Partial<FilaMontoNota>): void {
    this.filasCierre(tipo).update((c) => c.map((fila, i) => (i === index ? { ...fila, ...cambios } : fila)));
  }

  confirmarCierre(): void {
    const entradasFisicasFinal = this.entradasFisicasFinal();
    if (entradasFisicasFinal === null || entradasFisicasFinal < 0) {
      this.errorCierre.set('Indicá con cuántas entradas termina el turno.');
      return;
    }

    const conteo = this.denominaciones
      .map((denominacion) => ({ denominacion, cantidad: this.conteoEfectivo()[denominacion] ?? 0 }))
      .filter((c) => c.cantidad > 0);

    // Las filas sin monto cargado se ignoran (es normal no tener ningún cierre de un tipo
    // si esa caja no tuvo ventas con esa forma de pago): no hace falta "quitarlas" a mano.
    const cierres: CierrePosnetInput[] = [
      ...this.cierresTarjeta()
        .filter((f) => f.monto !== null && f.monto > 0)
        .map((f) => ({ formaPago: 'TARJETA' as const, monto: f.monto!, nota: f.nota.trim() || null })),
      ...this.cierresQr()
        .filter((f) => f.monto !== null && f.monto > 0)
        .map((f) => ({ formaPago: 'MERCADO_PAGO_QR' as const, monto: f.monto!, nota: f.nota.trim() || null })),
    ];

    this.cerrandoCaja.set(true);
    this.errorCierre.set(null);
    this.cajaService.cerrar(conteo, cierres, entradasFisicasFinal).subscribe({
      next: (c) => {
        this.cerrandoCaja.set(false);
        this.cajaCerrada.emit(c);
      },
      error: (err) => {
        this.errorCierre.set(typeof err?.error === 'string' ? err.error : 'No se pudo cerrar la caja. Reintentá.');
        this.cerrandoCaja.set(false);
      },
    });
  }
}
