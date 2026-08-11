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
    if (!this.dia.esPasado && !this.dia.esHoy && this.dia.abierto && this.dia.numero)
    {
      this.diaSeleccionado.emit(this.dia.fecha);
    }
  }


  get clasesEstado(): string{
    if (!this.dia.numero) return 'celda-vacia';
    if (this.dia.esPasado){
      return this.dia.abierto ? 'dia-pasado-abierto' : 'dia-pasado-cerrado';
    }
    if (this.dia.seleccionado) return 'dia-seleccionado';
    if (this.dia.abierto) return 'dia-abierto';
    return 'dia-cerrado'

  }
}

