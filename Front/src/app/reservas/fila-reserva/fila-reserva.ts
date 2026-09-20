import { Component, TemplateRef, inject, input, output } from '@angular/core';
import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { Reserva } from '../../services/boleteria.service';
import { PesosPipe } from '../../shared/pesos.pipe';
import { ReservaVista } from '../../shared/reserva-vista.util';
import { ReservasBusqueda } from '../busqueda-reservas';

/**
 * Una fila del listado de reservas: titular, código, estado y la acción que corresponde. Lo
 * relativo a validar ("Validar Ingreso", la cuenta regresiva de "Cancelar validación") lo hace
 * ella sola contra `ReservasBusqueda`; lo que cambia según la pantalla lo decide quien la usa:
 *
 *  - `cobrar`: una RESERVADO_EFECTIVO no se cobra acá adentro, se avisa. Control de Accesos la
 *    cobra directo en efectivo; el POS la carga en el carrito para elegir forma de pago.
 *  - `menu`: plantilla opcional con el menú ⋮ (editar, reenviar mail, reembolsar). Sin ella la
 *    fila no reserva la columna.
 */
@Component({
  selector: 'app-fila-reserva',
  imports: [DatePipe, NgTemplateOutlet, PesosPipe],
  templateUrl: './fila-reserva.html',
  styleUrl: './fila-reserva.css',
})
export class FilaReserva {
  protected busqueda = inject(ReservasBusqueda);

  vista = input.required<ReservaVista>();
  /** Texto del botón de una RESERVADO_EFECTIVO. */
  textoCobrar = input('Cobrar y Validar');
  menu = input<TemplateRef<{ $implicit: Reserva }> | null>(null);
  cobrar = output<Reserva>();
}
