import { Directive, ElementRef, booleanAttribute, effect, inject, input, signal } from '@angular/core';

/**
 * Tarjeta que en celular se pliega tocando su título (el h2 hijo directo). Si tiene un
 * `.btn-ver-detalle`, ese botón muestra u oculta su `.tabla-simple`. En pantallas más anchas no
 * cambia nada. Los estilos están en styles.css.
 */
@Directive({
  selector: '[appPlegableCelular]',
  host: {
    class: 'plegable-celular',
    '[class.plegada]': 'plegada()',
    '[class.con-detalle]': 'conDetalle()',
    '(click)': 'alTocar($event)',
  },
})
export class PlegableCelular {
  private elemento = inject<ElementRef<HTMLElement>>(ElementRef);

  /** Abierta. Si pasa a true después de arrancar (ej. "Editar" en una lista), se abre y la muestra. */
  plegableAbierta = input(false, { transform: booleanAttribute });

  plegada = signal(true);
  conDetalle = signal(false);

  private arranco = false;

  constructor() {
    effect(() => {
      const abierta = this.plegableAbierta();
      // Terminó la edición: vuelve a plegarse.
      this.plegada.set(!abierta);
      if (abierta && this.arranco && window.matchMedia('(max-width: 600px)').matches) {
        // Después de que se despliegue: antes de eso la posición todavía no es la final.
        setTimeout(() => this.elemento.nativeElement.scrollIntoView({ block: 'start' }));
      }
      this.arranco = true;
    });
  }

  alTocar(evento: Event): void {
    const tocado = evento.target as HTMLElement;
    if (tocado.closest('.btn-ver-detalle')) {
      this.conDetalle.update((v) => !v);
    } else if (tocado.closest('h2')?.parentElement === this.elemento.nativeElement) {
      this.plegada.update((v) => !v);
    }
  }
}
