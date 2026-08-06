import { Component, input, output } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { Reserva } from '../../Services/boleteria.service';
import { FormaPagoPos } from '../../models/compra';
import { ItemVentaResumen } from '../carrito-venta/carrito-venta';
import { LucideCircleCheck } from '@lucide/angular';

@Component({
  selector: 'app-comprobante-venta',
  imports: [CurrencyPipe, LucideCircleCheck],
  templateUrl: './comprobante-venta.html',
  styleUrl: './comprobante-venta.css',
})
export class ComprobanteVenta {
  venta = input.required<Reserva>();
  formaPago = input.required<FormaPagoPos>();
  vuelto = input<number | null>(null);
  items = input<ItemVentaResumen[]>([]);

  nuevaVenta = output<void>();
}
