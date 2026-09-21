import { Component, ElementRef, effect, inject, input, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LucideChevronDown, LucideGift } from '@lucide/angular';
import { ReservasBusqueda } from '../busqueda-reservas';

/**
 * Controles de búsqueda de reservas: un campo para DNI / nombre / email / código, el selector de
 * día ("Ver esa fecha" / "Ver todas las fechas") y el botón de regalos. No guarda estado propio:
 * todo vive en `ReservasBusqueda`, de la pantalla donde esté puesto.
 */
@Component({
  selector: 'app-buscador-reservas',
  imports: [FormsModule, LucideChevronDown, LucideGift],
  templateUrl: './buscador-reservas.html',
  styleUrl: './buscador-reservas.css',
})
export class BuscadorReservas {
  protected busqueda = inject(ReservasBusqueda);

  /** Foco en el campo al abrir. El POS lo apaga en táctil: levantaría el teclado encima de la
   * lista del día, que es lo primero que el boletero necesita ver. */
  autoenfocar = input(true);

  private campo = viewChild<ElementRef<HTMLInputElement>>('campo');

  constructor() {
    effect(() => {
      const campo = this.campo();
      if (campo && this.autoenfocar()) campo.nativeElement.focus();
    });
  }

  /** Lleva el foco al campo y selecciona lo que tenga, para escribir encima. */
  enfocar(): void {
    const campo = this.campo()?.nativeElement;
    if (!campo) return;
    campo.focus();
    campo.select();
  }
}
