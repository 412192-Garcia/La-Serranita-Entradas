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

  cantidad = signal<number | null>(null);
  registrando = signal(false);
  error = signal<string | null>(null);

  async confirmar(): Promise<void> {
    const cantidad = this.cantidad();
    if (cantidad === null || cantidad <= 0) {
      this.error.set('Ingresá una cantidad mayor a cero.');
      return;
    }
    this.registrando.set(true);
    this.error.set(null);

    const resultado = await this.pendientes.ejecutar<Caja>({ tipo: 'INGRESO_ENTRADAS', payload: { cantidad } });
    this.registrando.set(false);
    this.cantidad.set(null);
    if (resultado.confirmada) {
      this.ingresoRegistrado.emit(resultado.resultado);
    } else {
      this.ingresoEncolado.emit(cantidad);
    }
  }
}
