import { Component, input } from '@angular/core';
import { LucideArrowUp, LucideArrowDown, LucideChevronsUpDown } from '@lucide/angular';
import { EstadoOrden } from '../ordenable';

@Component({
  selector: 'app-columna-ordenable',
  imports: [LucideArrowUp, LucideArrowDown, LucideChevronsUpDown],
  templateUrl: './columna-ordenable.html',
})
export class ColumnaOrdenable {
  estado = input.required<EstadoOrden>();
}
