import { Component, inject, input, output, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Caja, IngresoEntradas } from '../../services/caja.service';
import { OperacionesPendientesService } from '../../services/operaciones-pendientes.service';
import { Modal } from '../../shared/modal/modal';

@Component({
  selector: 'app-ingreso-entradas-modal',
  imports: [FormsModule, DatePipe, Modal],
  templateUrl: './ingreso-entradas-modal.html',
  styleUrl: './ingreso-entradas-modal.css',
})
export class IngresoEntradasModal {
  private pendientes = inject(OperacionesPendientesService);

  ingresos = input<IngresoEntradas[]>([]);

  ingresoRegistrado = output<Caja>();
  /** El ingreso quedó encolado sin conexión: el padre lo refleja en su propia caja hasta que sincronice. */
  ingresoEncolado = output<number>();
  cerrar = output<void>();

  cantidad = signal(0);
  registrando = signal(false);
  error = signal<string | null>(null);

  incrementarCantidad(): void {
    this.cantidad.update((c) => c + 1);
  }

  decrementarCantidad(): void {
    this.cantidad.update((c) => Math.max(0, c - 1));
  }

  /** El talonario se repone de a cientos, no de a uno: hace falta poder escribir el número
   * directo (el +/- solo sirve para un ajuste chico), a diferencia del stepper puro de
   * "Agregar artículo", donde las cantidades típicas son bajas. */
  setCantidad(valor: string | number | null): void {
    const numero = typeof valor === 'number' ? valor : parseInt(String(valor ?? '').replace(/\D/g, ''), 10);
    this.cantidad.set(Number.isFinite(numero) && numero >= 0 ? numero : 0);
  }

  async confirmar(): Promise<void> {
    const cantidad = this.cantidad();
    if (cantidad <= 0) {
      this.error.set('Ingresá una cantidad mayor a cero.');
      return;
    }
    this.registrando.set(true);
    this.error.set(null);

    const resultado = await this.pendientes.ejecutar<Caja>({ tipo: 'INGRESO_ENTRADAS', payload: { cantidad } });
    this.registrando.set(false);

    if (resultado.confirmada) {
      this.cantidad.set(0);
      this.ingresoRegistrado.emit(resultado.resultado);
      return;
    }
    // Rechazo real del servidor: no se guardó, no corresponde tratarlo como encolado.
    if (resultado.rechazada) {
      this.error.set(resultado.mensaje);
      return;
    }
    this.cantidad.set(0);
    this.ingresoEncolado.emit(cantidad);
  }
}
