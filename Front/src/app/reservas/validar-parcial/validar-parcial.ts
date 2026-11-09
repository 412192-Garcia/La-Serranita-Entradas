import { Component, computed, input, output, signal } from '@angular/core';
import { LucideMinus, LucidePlus } from '@lucide/angular';
import { AusenteValidacion, DetalleReserva, Reserva } from '../../services/boleteria.service';
import { Modal } from '../../shared/modal/modal';
import { PesosPipe } from '../../shared/pesos.pipe';

/** Validar una anticipada cuando no vinieron todos: lo que no se usó va en un voucher de cantina. */
@Component({
  selector: 'app-validar-parcial',
  imports: [Modal, PesosPipe, LucideMinus, LucidePlus],
  templateUrl: './validar-parcial.html',
  styleUrl: './validar-parcial.css',
})
export class ValidarParcial {
  reserva = input.required<Reserva>();
  cerrar = output<void>();
  confirmar = output<AusenteValidacion[]>();

  /** Cuántos vinieron por línea; sin tocar = todos. */
  private vinieron = signal<Record<number, number>>({});

  lineas = computed(() => (this.reserva().detalles ?? []).filter((d) => d.tipoEntrada?.tipo === 'ENTRADA'));
  extras = computed(() => (this.reserva().detalles ?? []).filter((d) => d.tipoEntrada?.tipo === 'EXTRA' && d.cantidad > 0));

  cuantosVinieron(d: DetalleReserva): number {
    return this.vinieron()[d.id] ?? d.cantidad;
  }

  cambiar(d: DetalleReserva, delta: number): void {
    const nuevo = Math.max(0, Math.min(d.cantidad, this.cuantosVinieron(d) + delta));
    this.vinieron.update((v) => ({ ...v, [d.id]: nuevo }));
  }

  totalVinieron = computed(() => this.lineas().reduce((a, d) => a + this.cuantosVinieron(d), 0));
  totalFaltan = computed(() => this.lineas().reduce((a, d) => a + d.cantidad - this.cuantosVinieron(d), 0));

  /** Lo mismo que calcula el backend: el precio de cada entrada con el descuento de la compra. */
  montoVoucher = computed(() => {
    const r = this.reserva();
    let lista = 0;
    let listaAusentes = 0;
    for (const d of r.detalles ?? []) {
      const precio = d.precioUnitario ?? d.tipoEntrada?.precio ?? 0;
      lista += precio * d.cantidad;
      if (d.tipoEntrada?.tipo === 'ENTRADA') listaAusentes += precio * (d.cantidad - this.cuantosVinieron(d));
    }
    if (lista <= 0 || listaAusentes <= 0) return 0;
    return Math.round((listaAusentes * Math.min(r.montoTotal, lista)) / lista);
  });

  validar(): void {
    const ausentes = this.lineas()
      .map((d) => ({ detalleId: d.id, cantidad: d.cantidad - this.cuantosVinieron(d) }))
      .filter((a) => a.cantidad > 0);
    this.confirmar.emit(ausentes);
  }
}
