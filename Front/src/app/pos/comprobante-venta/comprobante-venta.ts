import { Component, DestroyRef, OnInit, inject, input, output, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription, of, timer } from 'rxjs';
import { catchError, switchMap, take, takeWhile } from 'rxjs/operators';
import { Reserva } from '../../services/boleteria.service';
import { FormaPagoPos, FormaPagoVentaPos } from '../../models/compra';
import { etiquetaFormaPago } from '../../models/forma-pago';
import { Factura, FacturacionPos, numeroComprobante } from '../../models/factura';
import { FacturaService } from '../../services/factura.service';
import { ItemVentaResumen } from '../carrito-venta/carrito-venta';
import {
  LucideCircleAlert,
  LucideCircleCheck,
  LucideCloudOff,
  LucideFileText,
  LucideLoaderCircle,
  LucidePrinter,
} from '@lucide/angular';
import { PesosPipe } from '../../shared/pesos.pipe';

/** Cada cuánto se pregunta por la factura y hasta cuándo: ARCA suele contestar en 2-3 s y el
 * ticket sale enseguida; pasado ~1 minuto ya quedó en la cola de reintentos y no tiene sentido
 * seguir esperando en pantalla. */
const INTERVALO_CONSULTA_MS = 2000;
const MAX_CONSULTAS = 30;

@Component({
  selector: 'app-comprobante-venta',
  imports: [PesosPipe, NgTemplateOutlet, LucideCircleCheck, LucideCloudOff, LucideCircleAlert, LucideFileText, LucideLoaderCircle, LucidePrinter],
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
  reimprimiendo = signal(false);
  errorReimpresion = signal<string | null>(null);

  nuevaVenta = output<void>();

  private consulta: Subscription | null = null;

  ngOnInit(): void {
    if (!this.facturacion() || this.pendiente() || !this.venta().id) return;
    this.consultarFactura();
  }

  /** ¿Hay que seguir preguntando? Mientras no esté emitida, y si va a la ticketera, mientras
   * no se sepa si el ticket salió. */
  private sigueEnCurso(f: Factura | null): boolean {
    if (f === null || f.estado === 'PENDIENTE') return true;
    if (f.estado !== 'EMITIDA' || f.destino !== 'IMPRIMIR') return false;
    return f.impresionEstado === null || f.impresionEstado === 'PENDIENTE' || f.impresionEstado === 'ENVIADO';
  }

  private consultarFactura(): void {
    this.consulta?.unsubscribe();
    this.facturaDemorada.set(false);
    const compraId = this.venta().id;
    this.consulta = timer(0, INTERVALO_CONSULTA_MS)
      .pipe(
        take(MAX_CONSULTAS),
        switchMap(() => this.facturaService.porCompra(compraId).pipe(catchError(() => of(null)))),
        takeWhile((f) => this.sigueEnCurso(f), true),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: (f) => {
          if (f) this.factura.set(f);
        },
        complete: () => {
          if (this.sigueEnCurso(this.factura())) this.facturaDemorada.set(true);
        },
      });
  }

  reimprimir(): void {
    const f = this.factura();
    if (!f || f.estado !== 'EMITIDA') return;
    this.reimprimiendo.set(true);
    this.errorReimpresion.set(null);
    this.facturaService.imprimir(f.id, this.facturacion()?.impresora ?? null).subscribe({
      next: () => {
        this.reimprimiendo.set(false);
        // Que se vea "Imprimiendo…" de nuevo hasta que el agente confirme.
        this.factura.set({ ...f, impresionEstado: 'PENDIENTE', impresionError: null });
        this.consultarFactura();
      },
      error: (err) => {
        this.reimprimiendo.set(false);
        this.errorReimpresion.set(typeof err?.error === 'string' ? err.error : 'No se pudo mandar a imprimir.');
      },
    });
  }
}
