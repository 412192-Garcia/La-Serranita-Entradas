import {ChangeDetectorRef, Component, EventEmitter, Input, Output} from '@angular/core';
import {DatePipe} from '@angular/common';
import {FormsModule} from '@angular/forms';
import { FormaPagoType, ResumenCompraData } from '../../models/compra';
import { CompraService } from '../../services/compra.service';
import { LucideCalendar, LucideTicket, LucideCreditCard, LucideTag, LucideGift, LucideBanknote } from '@lucide/angular';
import { PesosPipe } from '../../shared/pesos.pipe';

@Component({
  selector: 'app-resumen',
  imports: [
    DatePipe,
    PesosPipe,
    FormsModule,
    LucideCalendar,
    LucideTicket,
    LucideCreditCard,
    LucideTag,
    LucideGift,
    LucideBanknote
  ],
  templateUrl: './resumen.html',
  styleUrl: './resumen.css',
})
export class Resumen {
  @Input() datosCompra!: ResumenCompraData;
  @Input() cargandoPago: boolean = false;
  /** Código del cupón aplicado. Vive en el padre, que lo valida al cotizar. */
  @Input() cuponAplicado: string | null = null;
  /** Por qué el cupón aplicado no descuenta con las entradas elegidas, o null. */
  @Input() avisoCupon: string | null = null;
  /** Por qué no se pudo aplicar el código ingresado, o null. */
  @Input() errorCupon: string | null = null;

  @Output() confirmarPago = new EventEmitter<void>();
  @Output() volverAtras = new EventEmitter<void>();
  @Output() formaPagoChange = new EventEmitter<FormaPagoType>();
  @Output() aplicarCodigoCupon = new EventEmitter<string>();
  @Output() quitarCupon = new EventEmitter<void>();

  codigoInput: string = '';
  /** "Efectivo en Boletería": se puede apagar desde Sistema > Módulos. Mientras no se sabe, se muestra. */
  reservaEfectivo = true;

  constructor(private cdr: ChangeDetectorRef, compraService: CompraService) {
    compraService.opcionesPago().subscribe({
      next: ({ reservaEfectivo }) => {
        this.reservaEfectivo = reservaEfectivo;
        // Estaba elegida y se apagó: vuelve a Mercado Pago (el backend la rechazaría igual).
        if (!reservaEfectivo && this.datosCompra?.formaPago === 'EFECTIVO_BOLETERIA') this.onCambiarFormaPago('MERCADO_PAGO');
        this.cdr.markForCheck();
      },
      // Sin respuesta se deja como estaba: si está apagada, el backend la rechaza con el motivo.
      error: () => {},
    });
  }

  onPagar(): void {
    this.confirmarPago.emit();
  }

  onVolver(): void {
    this.volverAtras.emit();
  }

  onCambiarFormaPago(metodo: FormaPagoType): void {
    if (this.datosCompra) {
      this.datosCompra.formaPago = metodo;
    }
    this.formaPagoChange.emit(metodo);
  }

  aplicarCupon(): void {
    const codigo = this.codigoInput.toUpperCase().trim();
    if (codigo) this.aplicarCodigoCupon.emit(codigo);
  }

  removerCupon(): void {
    this.codigoInput = '';
    this.quitarCupon.emit();
  }
}
