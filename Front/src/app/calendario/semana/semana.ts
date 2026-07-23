import {Component, EventEmitter, Input, input, Output} from '@angular/core';
import {Dia} from './dia/dia';
import {DiaCalendario} from '../calendario-models';

@Component({
  selector: 'app-semana',
  imports: [
    Dia
  ],
  templateUrl: './semana.html',
  styleUrl: './semana.css',
})
export class Semana {
  @Input() dias: DiaCalendario[] = [];

  @Output() diaSeleccionado = new EventEmitter<Date>();

  onDiaSeleccionado(fecha: Date):void {
    this.diaSeleccionado.emit(fecha);
  }
}
