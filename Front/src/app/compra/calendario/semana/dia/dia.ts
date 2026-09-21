import {Component, EventEmitter, input, Input, Output} from '@angular/core';
import {NgClass} from '@angular/common';
import {DiaCalendario} from '../../calendario-models';

@Component({
  selector: 'app-dia',
  imports: [
    NgClass
  ],
  templateUrl: './dia.html',
  styleUrl: './dia.css',
})
export class Dia {
  @Input() dia!: DiaCalendario;

  @Output() diaSeleccionado = new EventEmitter<Date>();

  onDiaClick():void{
    // "Hoy" se puede elegir mientras el servidor lo ofrezca como abierto: el backend lo saca de la
    // lista de días abiertos pasado el límite de compra (ver DiaAperturaService.getDiasAbiertos), y
    // rechaza la compra igual por si la página quedó abierta más allá de ese momento.
    if (!this.dia.esPasado && !this.dia.compraCerrada && this.dia.abierto && this.dia.numero)
    {
      this.diaSeleccionado.emit(this.dia.fecha);
    }
  }


  get clasesEstado(): string{
    if (!this.dia.numero) return 'celda-vacia';
    // Hoy con la compra ya cortada se ve igual que un día pasado: si abrió, verde pálido; si no, gris.
    if (this.dia.esPasado || this.dia.compraCerrada){
      return this.dia.abierto ? 'dia-pasado-abierto' : 'dia-pasado-cerrado';
    }
    if (this.dia.seleccionado) return 'dia-seleccionado';
    if (this.dia.abierto) return 'dia-abierto';
    return 'dia-cerrado'

  }
}

