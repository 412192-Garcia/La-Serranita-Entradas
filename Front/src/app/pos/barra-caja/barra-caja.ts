import { Component, input, output } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { Caja } from '../../Services/caja.service';

@Component({
  selector: 'app-barra-caja',
  imports: [CurrencyPipe],
  templateUrl: './barra-caja.html',
  styleUrl: './barra-caja.css',
})
export class BarraCaja {
  caja = input.required<Caja>();

  validarReserva = output<void>();
  retirarEfectivo = output<void>();
  agregarEntradas = output<void>();
}
