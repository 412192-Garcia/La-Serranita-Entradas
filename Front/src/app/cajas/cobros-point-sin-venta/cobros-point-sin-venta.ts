import { Component, effect, inject, input, output, signal, untracked } from '@angular/core';
import { PesosPipe } from '../../shared/pesos.pipe';
import { CobroPoint, PointService, ResumenCajaPoint } from '../../services/point.service';

/**
 * Aviso del cierre de caja (ADMIN): plata que el Point cobró en esta caja y no quedó como venta,
 * con un botón para devolvérsela al cliente. Lo usan los dos cierres: el de conteo y el de una
 * caja "sin control". Pide el resumen del Point y se lo pasa al padre (el cierre con conteo lo usa
 * para precargar el posnet). Si no hay nada sin venta, no muestra nada.
 */
@Component({
  selector: 'app-cobros-point-sin-venta',
  imports: [PesosPipe],
  templateUrl: './cobros-point-sin-venta.html',
  styleUrl: './cobros-point-sin-venta.css',
})
export class CobrosPointSinVenta {
  private point = inject(PointService);

  cajaId = input<number | null>(null);
  resumenCargado = output<ResumenCajaPoint | null>();

  resumen = signal<ResumenCajaPoint | null>(null);
  devolviendoId = signal<number | null>(null);
  error = signal<string | null>(null);

  constructor() {
    effect(() => {
      const id = this.cajaId();
      untracked(() => this.cargar(id));
    });
  }

  private cargar(id: number | null): void {
    this.resumen.set(null);
    this.error.set(null);
    this.resumenCargado.emit(null);
    if (id === null) return;
    // Sin cobros con Point (o con el módulo nunca usado) vuelve todo en 0 y no se muestra nada.
    this.point.resumenCaja(id).subscribe({
      next: (r) => {
        this.resumen.set(r);
        this.resumenCargado.emit(r);
      },
      error: (err) => console.error('Error al consultar los cobros con Point de la caja:', err),
    });
  }

  devolver(c: CobroPoint): void {
    if (!window.confirm(`¿Devolverle al cliente el cobro con Point de $${c.monto}? No tiene una venta registrada en esta caja.`)) return;
    this.devolviendoId.set(c.id);
    this.error.set(null);
    this.point.devolverCobro(c.id).subscribe({
      next: () => {
        this.devolviendoId.set(null);
        this.cargar(this.cajaId());
      },
      error: (err) => {
        this.devolviendoId.set(null);
        this.error.set(typeof err?.error === 'string' && err.error ? err.error : 'No se pudo devolver el cobro.');
      },
    });
  }
}
