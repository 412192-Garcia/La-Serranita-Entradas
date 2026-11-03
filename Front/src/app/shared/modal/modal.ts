import { Component, OnDestroy, effect, inject, input, output } from '@angular/core';
import { AtrasService, EntradaAtras } from './atras.service';

@Component({
  selector: 'app-modal',
  templateUrl: './modal.html',
  styleUrl: './modal.css',
})
export class Modal implements OnDestroy {
  private atras = inject(AtrasService);

  cerrar = output<void>();

  /** Desactivado en modales con un formulario que se puede perder con un toque afuera sin
   * querer (retiro/aporte, ingreso de entradas): ahí sólo cierra el botón explícito. En el
   * resto de la app un toque afuera nunca pierde nada, así que por defecto sigue cerrando. */
  cerrarAlTocarFondo = input(true);

  /** En false para un modal que queda montado pero oculto (ver cierre-caja-modal). */
  activo = input(true);

  /** Embebido, el iframe mide lo que su contenido: "abajo de todo" puede quedar fuera de pantalla. */
  readonly comoHoja = window.parent === window;

  private abierto = false;
  private entrada: EntradaAtras = { cerrar: () => this.cerrar.emit(), estaAbierta: () => this.abierto };

  constructor() {
    effect(() => (this.activo() ? this.alAbrir() : this.alCerrar()));
  }

  ngOnDestroy(): void {
    this.alCerrar();
  }

  private alAbrir(): void {
    if (this.abierto) return;
    this.abierto = true;
    this.atras.abrir(this.entrada);
  }

  private alCerrar(): void {
    if (!this.abierto) return;
    this.abierto = false;
    this.atras.cerrado(this.entrada);
  }
}
