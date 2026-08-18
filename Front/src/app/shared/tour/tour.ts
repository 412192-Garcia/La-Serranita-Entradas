import { Component, ElementRef, HostListener, computed, effect, signal, input, model, untracked, viewChild } from '@angular/core';
import { LucideX } from '@lucide/angular';

/** Un paso del recorrido: `selector` es un CSS selector (normalmente `[data-tour="..."]`) que ya está siempre presente en el DOM del padre — no apuntar a algo detrás de un `@if` colapsado. */
export interface TourStep {
  selector: string;
  titulo: string;
  texto: string;
}

interface RectObjetivo {
  top: number;
  left: number;
  width: number;
  height: number;
}

interface PosicionPopover {
  top: number;
  left: number;
  arriba: boolean;
}

const ANCHO_POPOVER = 320;
const MARGEN = 14;
const ESPACIO_MINIMO_ABAJO = 200;

/**
 * Recorrido guiado interactivo genérico: resalta en secuencia los elementos indicados por
 * `pasos()` (vía un "agujero" de box-shadow sobre el elemento real, sin SVG ni máscaras) y
 * muestra un popover con el texto de cada paso. No depende de ninguna librería de "product
 * tour" — no había ninguna instalada en el proyecto y el alcance no lo justifica.
 */
@Component({
  selector: 'app-tour',
  imports: [LucideX],
  templateUrl: './tour.html',
  styleUrl: './tour.css',
})
export class Tour {
  pasos = input.required<TourStep[]>();
  /** Two-way: el padre decide cuándo arranca/termina el tour (ej. `[(activo)]="tourActivo"`). */
  activo = model(false);

  private popoverEl = viewChild<ElementRef<HTMLElement>>('popover');

  pasoActual = signal(0);
  rectObjetivo = signal<RectObjetivo | null>(null);
  posicionPopover = signal<PosicionPopover | null>(null);

  pasoInfo = computed(() => this.pasos()[this.pasoActual()] ?? null);
  esUltimoPaso = computed(() => this.pasoActual() >= this.pasos().length - 1);

  constructor() {
    // `untracked` es clave acá: sin esto, la llamada a posicionar() (que lee pasoActual
    // internamente vía pasoInfo()) registra pasoActual como dependencia de ESTE efecto —
    // entonces cada avance de siguiente()/anterior() reactivaba el efecto, que volvía a
    // hacer `pasoActual.set(0)` y pisaba el paso recién elegido antes de que se viera.
    effect(() => {
      const abierto = this.activo();
      untracked(() => {
        if (abierto) {
          this.pasoActual.set(0);
          this.posicionar();
          queueMicrotask(() => this.popoverEl()?.nativeElement.focus());
        } else {
          this.rectObjetivo.set(null);
          this.posicionPopover.set(null);
        }
      });
    });
  }

  @HostListener('window:resize')
  onResize(): void {
    if (this.activo()) this.posicionar();
  }

  @HostListener('window:scroll')
  onScroll(): void {
    if (this.activo()) this.posicionar();
  }

  @HostListener('window:keydown', ['$event'])
  onKeydown(event: KeyboardEvent): void {
    if (!this.activo()) return;
    if (event.key === 'Escape') {
      event.preventDefault();
      this.cerrar();
    } else if (event.key === 'ArrowRight') {
      event.preventDefault();
      this.siguiente();
    } else if (event.key === 'ArrowLeft') {
      event.preventDefault();
      this.anterior();
    }
  }

  private posicionar(): void {
    const paso = this.pasoInfo();
    if (!paso) return;
    const elemento = document.querySelector<HTMLElement>(paso.selector);
    if (!elemento) {
      this.rectObjetivo.set(null);
      this.posicionPopover.set(null);
      return;
    }

    elemento.scrollIntoView({ block: 'center', behavior: 'smooth' });
    // Se espera a que termine el scroll suave para leer la posición final del elemento.
    setTimeout(() => {
      const r = elemento.getBoundingClientRect();
      this.rectObjetivo.set({ top: r.top, left: r.left, width: r.width, height: r.height });
      this.posicionPopover.set(this.calcularPosicionPopover(r));
      // Con un target más alto que la pantalla (tarjetas grandes en mobile) el cálculo de
      // arriba/abajo no alcanza — recién acá, con el popover ya renderizado, se puede medir
      // su tamaño real y correrlo si de todos modos quedó cortado por arriba o por abajo.
      requestAnimationFrame(() => this.corregirSiSeSaleDePantalla());
    }, 280);
  }

  private calcularPosicionPopover(r: DOMRect): PosicionPopover {
    const espacioAbajo = window.innerHeight - r.bottom;
    const arriba = espacioAbajo < ESPACIO_MINIMO_ABAJO && r.top > ESPACIO_MINIMO_ABAJO;
    const top = arriba ? r.top - MARGEN : r.bottom + MARGEN;
    const left = Math.max(MARGEN, Math.min(r.left + r.width / 2 - ANCHO_POPOVER / 2, window.innerWidth - ANCHO_POPOVER - MARGEN));
    return { top, left, arriba };
  }

  private corregirSiSeSaleDePantalla(): void {
    const el = this.popoverEl()?.nativeElement;
    const pos = this.posicionPopover();
    if (!el || !pos) return;
    const rect = el.getBoundingClientRect();
    let top = pos.top;
    if (rect.top < MARGEN) {
      top += MARGEN - rect.top;
    } else if (rect.bottom > window.innerHeight - MARGEN) {
      top -= rect.bottom - (window.innerHeight - MARGEN);
    }
    if (top !== pos.top) {
      this.posicionPopover.set({ ...pos, top });
    }
  }

  siguiente(): void {
    if (this.esUltimoPaso()) {
      this.cerrar();
      return;
    }
    this.pasoActual.update((p) => p + 1);
    this.posicionar();
  }

  anterior(): void {
    if (this.pasoActual() === 0) return;
    this.pasoActual.update((p) => p - 1);
    this.posicionar();
  }

  cerrar(): void {
    this.activo.set(false);
  }
}
