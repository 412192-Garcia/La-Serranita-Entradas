import { Component, input, signal } from '@angular/core';
import { LucideCircleHelp } from '@lucide/angular';
import { Tour, TourStep } from '../tour/tour';

/**
 * Botón "Tutorial" para la esquina superior derecha de una tarjeta (formulario, panel...):
 * al tocarlo arranca un recorrido que resalta cada campo y explica para qué sirve. Reusa el
 * mismo <app-tour> que el botón de la cabecera, así se ve y se comporta igual.
 *
 * Uso: la tarjeta lleva la clase `tarjeta-con-tutorial` (la vuelve el ancla del botón y le
 * deja lugar al título), cada campo a explicar lleva un `data-tour="..."` propio y propio de
 * esa tarjeta, y el botón recibe los pasos:
 *   <div class="tarjeta tarjeta-con-tutorial">
 *     <app-tutorial-tarjeta [pasos]="pasos" />
 *     <h2>…</h2>
 *     <label data-tour="campo">…</label>
 */
@Component({
  selector: 'app-tutorial-tarjeta',
  imports: [Tour, LucideCircleHelp],
  templateUrl: './tutorial-tarjeta.html',
  styleUrl: './tutorial-tarjeta.css',
})
export class TutorialTarjeta {
  pasos = input.required<TourStep[]>();
  /** Para el aria-label y el tooltip: de qué es el tutorial ("Ver tutorial: nuevo cupón"). */
  descripcion = input('este formulario');

  activo = signal(false);
}
