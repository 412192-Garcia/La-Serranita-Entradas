import { Component, input, output, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { Caja, OperacionCaja } from '../../services/caja.service';
import { etiquetaFormaPago } from '../../models/forma-pago';
import { PesosPipe } from '../../shared/pesos.pipe';

@Component({
  selector: 'app-resumen-cierre',
  imports: [PesosPipe, DatePipe, DecimalPipe],
  templateUrl: './resumen-cierre.html',
  styleUrl: './resumen-cierre.css',
})
export class ResumenCierre {
  caja = input.required<Caja>();
  /** false cuando el admin sólo está revisando una caja ya cerrada, sin intención de corregirla: oculta el botón "Corregir cierre". */
  mostrarAcciones = input(true);

  /** Vuelve al modal de cierre, precargado con lo que ya se cargó, para arreglar un error de tipeo. */
  corregir = output<void>();

  /** Todo lo vendido en el turno sin importar la forma de pago (el pago en dólares ya está incluido en totalVentasEfectivo). */
  totalVendido(): number {
    const c = this.caja();
    return (c.totalVentasEfectivo ?? 0) + (c.totalVentasTarjeta ?? 0) + (c.totalVentasQr ?? 0);
  }

  /** Desplegado del conteo billete por billete, para controlar rápido si algo no cierra. */
  mostrarBilletes = signal(false);

  /** Nombre corto para el detalle de caja: la forma de pago cruda del backend es un enum, no algo para mostrar tal cual. */
  etiquetaTipoOperacion(op: OperacionCaja): string {
    if (op.tipo === 'VENTA') return etiquetaFormaPago(op.formaPago);
    if (op.tipo === 'INGRESO_ENTRADAS') return 'Entradas';
    if (op.tipo === 'APORTE') return 'Aporte';
    return 'Retiro';
  }

  claseDiferenciaValor(v: number | null | undefined): string {
    if (v === null || v === undefined) return '';
    if (v < 0) return 'diferencia-faltante';
    if (v > 0) return 'diferencia-sobrante';
    return 'diferencia-exacta';
  }
}
