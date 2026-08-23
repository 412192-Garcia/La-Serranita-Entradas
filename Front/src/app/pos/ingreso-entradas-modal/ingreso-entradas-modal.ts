import { Component, inject, input, output, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Caja, CajaService, IngresoEntradas, TipoMovimientoEntradas } from '../../services/caja.service';
import { OperacionesPendientesService, PayloadIngresoEntradas } from '../../services/operaciones-pendientes.service';
import { Modal } from '../../shared/modal/modal';
import { SeleccionarAlFocoDirective } from '../../shared/seleccionar-al-foco.directive';

@Component({
  selector: 'app-ingreso-entradas-modal',
  imports: [FormsModule, DatePipe, Modal, SeleccionarAlFocoDirective],
  templateUrl: './ingreso-entradas-modal.html',
  styleUrl: './ingreso-entradas-modal.css',
})
export class IngresoEntradasModal {
  private pendientes = inject(OperacionesPendientesService);
  private cajaService = inject(CajaService);

  ingresos = input<IngresoEntradas[]>([]);
  /** Entradas físicas que el boletero tiene ahora (inicial + ingresos netos hasta este momento):
   * el mismo número que ya se ve en la barra de caja. Sirve para avisar acá mismo, sin red, si
   * un retiro pide más de lo que hay — no tiene sentido mandarlo al servidor para enterarse. */
  stockActual = input<number>(0);
  /** Si viene seteada, el movimiento se registra en la caja de OTRO usuario vía admin (usado desde el detalle de una caja abierta en Cajas); si no, en la propia caja abierta (uso normal en POS). */
  cajaId = input<number | null>(null);
  /** Caja propia del boletero, para uso normal en POS (ver cajaId de arriba, que es para el
   * camino de admin): viaja en el payload por si esto se rechaza, para que un admin sepa
   * exactamente qué caja reabrir y reintentar. */
  propiaCajaId = input<number | null>(null);

  ingresoRegistrado = output<Caja>();
  /** El movimiento quedó encolado sin conexión: el padre lo refleja en su propia caja hasta que sincronice. */
  ingresoEncolado = output<PayloadIngresoEntradas>();
  cerrar = output<void>();

  tipo = signal<TipoMovimientoEntradas>('INGRESO');
  cantidad = signal(0);
  motivo = signal('');
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
    const tipo = this.tipo();
    const motivo = this.motivo().trim();
    if (cantidad <= 0) {
      this.error.set('Ingresá una cantidad mayor a cero.');
      return;
    }
    if (tipo === 'RETIRO' && cantidad > this.stockActual()) {
      const resultante = this.stockActual() - cantidad;
      const sigue = window.confirm(
        `Tenés ${this.stockActual()} entradas y estás por retirar ${cantidad}: el conteo va a quedar en ${resultante}. ¿Confirmás igual?`
      );
      if (!sigue) return;
    }
    this.registrando.set(true);
    this.error.set(null);

    // El admin cargando en la caja de otro siempre opera con conexión (es desde el detalle de
    // una caja abierta en Cajas): no pasa por la cola offline, que es exclusiva del boletero en la puerta.
    const cajaIdAdmin = this.cajaId();
    if (cajaIdAdmin !== null) {
      this.cajaService.registrarIngresoEntradasComoAdmin(cajaIdAdmin, cantidad, motivo || undefined, tipo).subscribe({
        next: (c) => {
          this.limpiarCampos();
          this.ingresoRegistrado.emit(c);
        },
        error: (err) => {
          this.error.set(typeof err?.error === 'string' ? err.error : 'No se pudo registrar el movimiento. Reintentá.');
          this.registrando.set(false);
        },
      });
      return;
    }

    const payload: PayloadIngresoEntradas = {
      cantidad,
      tipo,
      motivo: tipo === 'RETIRO' && motivo ? motivo : undefined,
      cajaId: this.propiaCajaId()!,
    };
    const resultado = await this.pendientes.ejecutar<Caja>({ tipo: 'INGRESO_ENTRADAS', payload });
    this.registrando.set(false);

    if (resultado.confirmada) {
      this.limpiarCampos();
      this.ingresoRegistrado.emit(resultado.resultado);
      return;
    }
    // Rechazo real del servidor: no se guardó, no corresponde tratarlo como encolado.
    if (resultado.rechazada) {
      this.error.set(resultado.mensaje);
      return;
    }
    this.limpiarCampos();
    this.ingresoEncolado.emit(payload);
  }

  private limpiarCampos(): void {
    this.registrando.set(false);
    this.cantidad.set(0);
    this.motivo.set('');
  }
}
