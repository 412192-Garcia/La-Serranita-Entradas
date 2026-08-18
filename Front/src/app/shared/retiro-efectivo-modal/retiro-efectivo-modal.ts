import { Component, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CajaService, Caja, RetiroCaja, TipoMovimientoCaja } from '../../services/caja.service';
import { MoneyInputDirective } from '../money-input/money-input.directive';
import { OperacionesPendientesService, PayloadRetiroAporte } from '../../services/operaciones-pendientes.service';
import { Modal } from '../modal/modal';
import { PesosPipe } from '../pesos.pipe';

@Component({
  selector: 'app-retiro-efectivo-modal',
  imports: [PesosPipe, FormsModule, MoneyInputDirective, Modal],
  templateUrl: './retiro-efectivo-modal.html',
  styleUrl: './retiro-efectivo-modal.css',
})
export class RetiroEfectivoModal {
  private cajaService = inject(CajaService);
  private pendientes = inject(OperacionesPendientesService);

  retiros = input<RetiroCaja[]>([]);
  /** Si viene seteada, el movimiento se registra en la caja de OTRO usuario vía admin (usado desde el cierre en Cajas); si no, en la propia caja abierta (uso normal en POS). */
  cajaId = input<number | null>(null);

  retiroRegistrado = output<Caja>();
  /** El movimiento quedó encolado sin conexión: el padre lo refleja en su propia caja hasta que sincronice. */
  retiroEncolado = output<PayloadRetiroAporte>();
  cerrar = output<void>();

  tipo = signal<TipoMovimientoCaja>('RETIRO');
  monto = signal<number | null>(null);
  motivo = signal('');
  registrando = signal(false);
  error = signal<string | null>(null);

  async confirmar(): Promise<void> {
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

    // El admin cargando en la caja de otro siempre opera con conexión (es desde el cierre en
    // Cajas): no pasa por la cola offline, que es exclusiva del boletero en la puerta.
    const cajaId = this.cajaId();
    if (cajaId !== null) {
      this.cajaService.registrarRetiroComoAdmin(cajaId, monto, motivo, tipo).subscribe({
        next: (c) => {
          this.limpiarCampos();
          this.retiroRegistrado.emit(c);
        },
        error: (err) => {
          this.error.set(typeof err?.error === 'string' ? err.error : 'No se pudo registrar el movimiento. Reintentá.');
          this.registrando.set(false);
        },
      });
      return;
    }

    const payload: PayloadRetiroAporte = { monto, motivo, tipo };
    const resultado = await this.pendientes.ejecutar<Caja>({ tipo: 'RETIRO_APORTE', payload });

    if (resultado.confirmada) {
      this.limpiarCampos();
      this.retiroRegistrado.emit(resultado.resultado);
      return;
    }
    // Rechazo real del servidor: no se guardó, así que no corresponde tratarlo como encolado —
    // se deja el formulario tal cual estaba para poder corregir y reintentar.
    if (resultado.rechazada) {
      this.registrando.set(false);
      this.error.set(resultado.mensaje);
      return;
    }
    this.limpiarCampos();
    this.retiroEncolado.emit(payload);
  }

  private limpiarCampos(): void {
    this.registrando.set(false);
    this.monto.set(null);
    this.motivo.set('');
  }
}
