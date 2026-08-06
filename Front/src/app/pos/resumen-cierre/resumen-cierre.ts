import { Component, input, output } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { Caja, OperacionCaja } from '../../Services/caja.service';
import { etiquetaFormaPago } from '../formas-pago-pos';
import { LucideLock } from '@lucide/angular';

@Component({
  selector: 'app-resumen-cierre',
  imports: [CurrencyPipe, DatePipe, LucideLock],
  templateUrl: './resumen-cierre.html',
  styleUrl: './resumen-cierre.css',
})
export class ResumenCierre {
  caja = input.required<Caja>();

  verReservas = output<void>();

  /** Nombre corto para el detalle de caja: la forma de pago cruda del backend es un enum, no algo para mostrar tal cual. */
  etiquetaTipoOperacion(op: OperacionCaja): string {
    if (op.tipo === 'VENTA') return etiquetaFormaPago(op.formaPago);
    if (op.tipo === 'INGRESO_ENTRADAS') return 'Entradas';
    return 'Retiro';
  }
}
