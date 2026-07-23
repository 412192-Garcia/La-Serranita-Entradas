import { Component } from '@angular/core';
import {Calendario} from '../calendario/calendario';
import {SeleccionEntradas} from '../seleccion-entradas/seleccion-entradas';

@Component({
  selector: 'app-entradas',
  imports: [
    Calendario,
    SeleccionEntradas
  ],
  templateUrl: './entradas.html',
  styleUrl: './entradas.css',
})
export class Entradas {}
