import { Directive, ElementRef } from '@angular/core';

/**
 * Selecciona todo el texto del input al hacer foco (click o tab). Sin esto, tocar un campo que
 * ya tiene un valor (ej. el "0" inicial de un stepper) deja el cursor pegado a ese texto en vez
 * de reemplazarlo: escribir "100" a continuación puede terminar dando "1000" en vez de "100"
 * según dónde haya caído el cursor. Pensado para los steppers de cantidad (ver .stepper-input en
 * pos-caja.css), que se completan de a cientos y no de a uno.
 */
@Directive({
  selector: '[appSeleccionarAlFoco]',
  standalone: true,
  host: {
    '(focus)': 'seleccionarTodo()',
  },
})
export class SeleccionarAlFocoDirective {
  constructor(private el: ElementRef<HTMLInputElement>) {}

  seleccionarTodo(): void {
    this.el.nativeElement.select();
  }
}
