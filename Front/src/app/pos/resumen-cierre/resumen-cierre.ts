import { Component, input, output, signal } from '@angular/core';
import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Caja, OperacionCaja } from '../../Services/caja.service';
import { etiquetaFormaPago } from '../formas-pago-pos';
import { LucideLock } from '@lucide/angular';

@Component({
  selector: 'app-resumen-cierre',
  imports: [CurrencyPipe, DatePipe, DecimalPipe, LucideLock],
  templateUrl: './resumen-cierre.html',
  styleUrl: './resumen-cierre.css',
})
export class ResumenCierre {
  caja = input.required<Caja>();
  /** false cuando el admin sólo está revisando una caja ajena: oculta "Corregir cierre" y "Ver reservas". */
  mostrarAcciones = input(true);

  verReservas = output<void>();
  /** Vuelve al modal de cierre, precargado con lo que ya se cargó, para arreglar un error de tipeo. */
  corregir = output<void>();

  /** Todo lo vendido en el turno sin importar la forma de pago (el pago en dólares ya está incluido en totalVentasEfectivo). */
  totalVendido(): number {
    const c = this.caja();
    return (c.totalVentasEfectivo ?? 0) + (c.totalVentasTarjeta ?? 0) + (c.totalVentasQr ?? 0);
  }

  /** Desplegado del conteo billete por billete, para controlar rápido si algo no cierra. */
  mostrarDesglose = signal(false);

  /** Nombre corto para el detalle de caja: la forma de pago cruda del backend es un enum, no algo para mostrar tal cual. */
  etiquetaTipoOperacion(op: OperacionCaja): string {
    if (op.tipo === 'VENTA') return etiquetaFormaPago(op.formaPago);
    if (op.tipo === 'INGRESO_ENTRADAS') return 'Entradas';
    return 'Retiro';
  }
}
