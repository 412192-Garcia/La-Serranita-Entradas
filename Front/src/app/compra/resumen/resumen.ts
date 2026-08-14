import {ChangeDetectorRef, Component, EventEmitter, Input, Output} from '@angular/core';
import {DatePipe} from '@angular/common';
import {FormsModule} from '@angular/forms';
import { FormaPagoType, ResumenCompraData } from '../../models/compra';
import { Cupon } from '../../models/cupon';
import { CuponService } from '../../services/cupon.service';
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
  /** Fuente de verdad: vive en el padre porque debe sobrevivir a un cambio de forma de pago. */
  @Input() cuponAplicado: Cupon | null = null;

  @Output() confirmarPago = new EventEmitter<void>();
  @Output() volverAtras = new EventEmitter<void>();
  @Output() formaPagoChange = new EventEmitter<FormaPagoType>();
  @Output() cuponCambio = new EventEmitter<Cupon | null>();

  codigoInput: string = '';
  errorCupon: string | null = null;

  constructor(private cuponService: CuponService, private cdr: ChangeDetectorRef) {}

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
    if (!this.codigoInput.trim()) return;

    this.errorCupon = null;
    this.cuponService.validarCupon(this.codigoInput.toUpperCase().trim()).subscribe({
      next: (cupon) => {
        if (cupon && cupon.activo) {
          this.cuponAplicado = cupon;
          this.errorCupon = null;
          this.cuponCambio.emit(cupon);
        } else {
          this.errorCupon = 'El cupón no se encuentra activo.';
          this.cuponAplicado = null;
        }
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Cupón inválido o expirado:', err);
        this.errorCupon = 'Cupón inválido o expirado.';
        this.cuponAplicado = null;
        this.cdr.detectChanges();
      }
    });
  }

  removerCupon(): void {
    this.cuponAplicado = null;
    this.codigoInput = '';
    this.errorCupon = null;
    this.cuponCambio.emit(null);
  }
}
