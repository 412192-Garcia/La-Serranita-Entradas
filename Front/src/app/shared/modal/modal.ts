import { Component, input, output } from '@angular/core';

@Component({
  selector: 'app-modal',
  templateUrl: './modal.html',
  styleUrl: './modal.css',
})
export class Modal {
  cerrar = output<void>();

  /** Desactivado en modales con un formulario que se puede perder con un toque afuera sin
   * querer (retiro/aporte, ingreso de entradas): ahí sólo cierra el botón explícito. En el
   * resto de la app un toque afuera nunca pierde nada, así que por defecto sigue cerrando. */
  cerrarAlTocarFondo = input(true);
}
