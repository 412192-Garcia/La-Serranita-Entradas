import {Component, EventEmitter, Input, Output} from '@angular/core';
import {CurrencyPipe, DatePipe} from '@angular/common';
import { FormaPagoType, ResumenCompraData } from '../models/compra';

@Component({
  selector: 'app-resumen',
  imports: [
    DatePipe,
    CurrencyPipe
  ],
  templateUrl: './resumen.html',
  styleUrl: './resumen.css',
})
export class Resumen {
  @Input() datosCompra!: ResumenCompraData;
  @Input() cargandoPago: boolean = false;

  @Output() confirmarPago = new EventEmitter<void>();
  @Output() volverAtras = new EventEmitter<void>();
  @Output() formaPagoChange = new EventEmitter<FormaPagoType>();

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
}
