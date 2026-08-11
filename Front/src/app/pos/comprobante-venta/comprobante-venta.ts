import { Component, input, output } from '@angular/core';
import { Reserva } from '../../services/boleteria.service';
import { FormaPagoPos } from '../../models/compra';
import { ItemVentaResumen } from '../carrito-venta/carrito-venta';
import { LucideCircleCheck, LucideCloudOff } from '@lucide/angular';
import { PesosPipe } from '../../shared/pesos.pipe';

@Component({
  selector: 'app-comprobante-venta',
  imports: [PesosPipe, LucideCircleCheck, LucideCloudOff],
  templateUrl: './comprobante-venta.html',
  styleUrl: './comprobante-venta.css',
})
export class ComprobanteVenta {
  venta = input.required<Reserva>();
  formaPago = input.required<FormaPagoPos>();
  vuelto = input<number | null>(null);
  items = input<ItemVentaResumen[]>([]);
  /** Venta cobrada sin conexión: los datos son los calculados en el navegador, todavía sin confirmar contra el servidor. */
  pendiente = input(false);

  nuevaVenta = output<void>();
}
