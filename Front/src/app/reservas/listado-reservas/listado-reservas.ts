import { Component, TemplateRef, inject, input, output } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { LucideChevronLeft, LucideChevronRight, LucideSearchX } from '@lucide/angular';
import { Reserva } from '../../services/boleteria.service';
import { ColumnaOrdenable } from '../../shared/columna-ordenable/columna-ordenable';
import { Spinner } from '../../shared/spinner/spinner';
import { ReservasBusqueda } from '../busqueda-reservas';
import { FilaReserva } from '../fila-reserva/fila-reserva';

/**
 * Resultados de la búsqueda de reservas: un día puntual como tabla, o "todas las fechas" en un
 * contenedor por día con paginador por semana. Toma todo de `ReservasBusqueda`; lo que depende
 * de la pantalla (qué hace "Cobrar", si hay menú ⋮) entra por `textoCobrar`, `menu` y `cobrar`.
 */
@Component({
  selector: 'app-listado-reservas',
  imports: [
    NgTemplateOutlet,
    ColumnaOrdenable,
    Spinner,
    FilaReserva,
    LucideSearchX,
    LucideChevronLeft,
    LucideChevronRight,
  ],
  templateUrl: './listado-reservas.html',
  styleUrl: './listado-reservas.css',
})
export class ListadoReservas {
  protected busqueda = inject(ReservasBusqueda);

  textoCobrar = input('Cobrar y Validar');
  menu = input<TemplateRef<{ $implicit: Reserva }> | null>(null);
  cobrar = output<Reserva>();
}
