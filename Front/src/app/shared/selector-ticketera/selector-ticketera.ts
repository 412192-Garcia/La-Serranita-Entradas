import { Component, inject, input } from '@angular/core';
import { ImpresorasService } from '../../services/impresoras.service';

/** Desplegable con las ticketeras conectadas. La elección se guarda en la tablet, igual que en el POS. */
@Component({
  selector: 'app-selector-ticketera',
  templateUrl: './selector-ticketera.html',
  styleUrl: './selector-ticketera.css',
})
export class SelectorTicketera {
  readonly impresoras = inject(ImpresorasService);
  /** Mostrar abajo por qué no se puede imprimir (apagar si la pantalla tiene su propio aviso). */
  conAviso = input(true);

  elegir(evento: Event): void {
    const nombre = (evento.target as HTMLSelectElement).value;
    if (nombre) this.impresoras.elegir(nombre);
  }
}
