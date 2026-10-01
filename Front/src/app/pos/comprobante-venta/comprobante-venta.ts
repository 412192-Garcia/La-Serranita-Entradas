import { Component, DestroyRef, OnInit, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { of, timer } from 'rxjs';
import { catchError, switchMap, take, takeWhile } from 'rxjs/operators';
import { Reserva } from '../../services/boleteria.service';
import { FormaPagoPos, FormaPagoVentaPos } from '../../models/compra';
import { etiquetaFormaPago } from '../../models/forma-pago';
import { Factura, FacturacionPos, numeroComprobante } from '../../models/factura';
import { FacturaService } from '../../services/factura.service';
import { ItemVentaResumen } from '../carrito-venta/carrito-venta';
import { LucideCircleAlert, LucideCircleCheck, LucideCloudOff, LucideFileText, LucideLoaderCircle } from '@lucide/angular';
import { PesosPipe } from '../../shared/pesos.pipe';

/** Cada cuánto se pregunta por la factura y hasta cuándo: ARCA suele contestar en 2-3 s; pasado
 * ~1 minuto ya quedó en la cola de reintentos y no tiene sentido seguir esperando en pantalla. */
const INTERVALO_CONSULTA_MS = 2000;
const MAX_CONSULTAS = 30;

@Component({
  selector: 'app-comprobante-venta',
  imports: [PesosPipe, LucideCircleCheck, LucideCloudOff, LucideCircleAlert, LucideFileText, LucideLoaderCircle],
  templateUrl: './comprobante-venta.html',
  styleUrl: './comprobante-venta.css',
})
export class ComprobanteVenta implements OnInit {
  private facturaService = inject(FacturaService);
  private destroyRef = inject(DestroyRef);

  venta = input.required<Reserva>();
  formaPago = input.required<FormaPagoVentaPos>();
  vuelto = input<number | null>(null);
  items = input<ItemVentaResumen[]>([]);
  /** Venta cobrada sin conexión: los datos son los calculados en el navegador, todavía sin confirmar contra el servidor. */
  pendiente = input(false);
  /** Pago mixto: segunda forma de pago usada, con su monto. Null en una venta con una sola forma. */
  formaPagoSecundaria = input<FormaPagoPos | null>(null);
  montoFormaPagoSecundaria = input<number | null>(null);
  /** Lo que se pidió de factura al cobrar. Null = no se facturó. */
  facturacion = input<FacturacionPos | null>(null);

  readonly etiquetaFormaPago = etiquetaFormaPago;
  readonly numeroComprobante = numeroComprobante;

  factura = signal<Factura | null>(null);
  /** Se dejó de consultar sin que quedara emitida: sigue en la cola de reintentos del servidor. */
  facturaDemorada = signal(false);

  nuevaVenta = output<void>();

  ngOnInit(): void {
    const venta = this.venta();
    if (!this.facturacion() || this.pendiente() || !venta.id) return;

    timer(0, INTERVALO_CONSULTA_MS)
      .pipe(
        take(MAX_CONSULTAS),
        switchMap(() => this.facturaService.porCompra(venta.id).pipe(catchError(() => of(null)))),
        takeWhile((f) => f === null || f.estado === 'PENDIENTE', true),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: (f) => {
          if (f) this.factura.set(f);
        },
        complete: () => {
          if (this.factura()?.estado !== 'EMITIDA' && this.factura()?.estado !== 'ERROR') {
            this.facturaDemorada.set(true);
          }
        },
      });
  }
}
