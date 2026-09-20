import { Component, TemplateRef, inject, input, output } from '@angular/core';
import { Reserva } from '../../services/boleteria.service';
import { ReservasBusqueda } from '../busqueda-reservas';
import { FilaReserva } from '../fila-reserva/fila-reserva';

/** Bloque desplegable con los regalos (reservas sin fecha de visita), aparte de la lista principal. */
@Component({
  selector: 'app-panel-regalos',
  imports: [FilaReserva],
  template: `
    @if (busqueda.totalRegalos() > 0 && busqueda.regalosExpandido()) {
      <div class="tabla-reservas tabla-regalos">
        @for (v of busqueda.regalosVisibles()!; track v.reserva.id) {
          <app-fila-reserva [vista]="v" [textoCobrar]="textoCobrar()" [menu]="menu()" (cobrar)="cobrar.emit($event)" />
        }
      </div>
    }
  `,
  styles: `
    :host { display: block; }

    /* Sin regalos desplegados el host queda vacío: que no ocupe un hueco (gap) en la pantalla. */
    :host(:empty) { display: none; }

    .tabla-reservas {
      background-color: var(--color-card-bg);
      border-radius: var(--radius-lg);
      box-shadow: var(--shadow-card);
    }

    .tabla-regalos {
      margin-bottom: 20px;
    }
  `,
})
export class PanelRegalos {
  protected busqueda = inject(ReservasBusqueda);

  textoCobrar = input('Cobrar y Validar');
  menu = input<TemplateRef<{ $implicit: Reserva }> | null>(null);
  cobrar = output<Reserva>();
}
