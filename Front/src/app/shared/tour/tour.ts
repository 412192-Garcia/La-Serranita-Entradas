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
}

const ANCHO_POPOVER = 320;
const MARGEN = 14;
const ESPACIO_MINIMO_ABAJO = 200;
/** Altura de arranque antes de medir la real (ver alturaPopover): un valor típico para que el
 * primer cálculo de posición ya quede razonablemente bien, en vez de asumir 0. */
const ALTURA_POPOVER_INICIAL = 250;

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

  /** Altura real del popover del paso actual: el contenido (texto de cada paso) no cambia con
   * el scroll, así que basta medirla una vez por paso — a diferencia de re-leer la posición del
   * popover en cada scroll/resize (lo que hacía antes), que corría atrás de un DOM que Angular
   * todavía no había terminado de repintar con el `top` recién asignado, y terminaba corrigiendo
   * en base a una posición vieja. Con la altura ya conocida, calcularPosicionPopover() clampea
   * directamente sin volver a tocar el DOM del popover para nada. */
  private alturaPopover = ALTURA_POPOVER_INICIAL;

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
    if (this.activo()) this.reposicionar();
  }

  /** A propósito NO llama a posicionar() (que hace scrollIntoView): un scroll programático
   * también dispara eventos "scroll" mientras anima, así que si este listener reaccionara
   * llamando a scrollIntoView de nuevo, se arma un loop que reinicia la animación una y otra
   * vez — eso era el comportamiento raro con el scroll. Acá sólo se vuelve a medir dónde quedó
   * el elemento (llamado tanto por scroll del propio tour como por scroll manual del usuario
   * mientras el tour está abierto) sin tocar el scroll en sí. */
  @HostListener('window:scroll')
  onScroll(): void {
    if (this.activo()) this.reposicionar();
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

    // Si ya está a la vista no hace falta scrollear ni esperar nada — eso era justamente lo que
    // podía asomar la posición vieja/a mitad de camino por un instante antes de acomodarse.
    if (this.elementoYaVisible(elemento)) {
      this.medirYPosicionar(elemento);
      this.medirAlturaPopoverYRefinar(elemento);
      return;
    }

    elemento.scrollIntoView({ block: 'center', behavior: 'smooth' });
    // Se espera a que termine el scroll suave para leer la posición final del elemento.
    // 'scrollend' es lo más preciso (se dispara justo cuando el navegador termina de animar),
    // pero no lo soportan todos (ej. Safari viejo) — el timeout es el respaldo para esos casos
    // y para cuando no había nada que scrollear (ahí 'scrollend' nunca llega a disparar).
    this.esperarFinDeScroll(() => {
      this.medirYPosicionar(elemento);
      this.medirAlturaPopoverYRefinar(elemento);
    });
  }

  private esperarFinDeScroll(cb: () => void): void {
    let resuelto = false;
    const terminar = () => {
      if (resuelto) return;
      resuelto = true;
      window.removeEventListener('scrollend', terminar);
      clearTimeout(timeoutId);
      cb();
    };
    window.addEventListener('scrollend', terminar, { once: true });
    const timeoutId = setTimeout(terminar, 400);
  }

  /** Algunos componentes (ej. app-catalogo-entradas) usan `:host { display: contents }` para no
   * romper el grid/flex del padre — eso deja al elemento sin geometría propia, así que
   * getBoundingClientRect() da todo en cero (0,0,0,0) y el tour terminaba anclado arriba a la
   * izquierda sin resaltar nada. Si pasa eso, se mide la unión de los hijos directos, que sí
   * tienen caja real. */
  private medirRect(elemento: HTMLElement): RectObjetivo & { bottom: number; right: number } {
    const r = elemento.getBoundingClientRect();
    if (r.width === 0 && r.height === 0 && elemento.children.length > 0) {
      const rects = Array.from(elemento.children).map((hijo) => hijo.getBoundingClientRect());
      const top = Math.min(...rects.map((x) => x.top));
      const left = Math.min(...rects.map((x) => x.left));
      const bottom = Math.max(...rects.map((x) => x.bottom));
      const right = Math.max(...rects.map((x) => x.right));
      return { top, left, width: right - left, height: bottom - top, bottom, right };
    }
    return { top: r.top, left: r.left, width: r.width, height: r.height, bottom: r.bottom, right: r.right };
  }

  private elementoYaVisible(el: HTMLElement): boolean {
    const r = this.medirRect(el);
    return r.top >= 0 && r.bottom <= window.innerHeight;
  }

  private medirYPosicionar(elemento: HTMLElement): void {
    const r = this.medirRect(elemento);
    this.rectObjetivo.set({ top: r.top, left: r.left, width: r.width, height: r.height });
    this.posicionPopover.set(this.calcularPosicionPopover(r));
  }

  /** Se llama una vez por cambio de paso (no en cada scroll/resize): mide la altura real que
   * acaba de tomar el popover recién mostrado y, si difiere de la que se había asumido, vuelve
   * a calcular la posición con el valor correcto. La altura en sí es segura de leer acá — a
   * diferencia de la posición, no depende de un `top` que Angular todavía esté por pintar. */
  private medirAlturaPopoverYRefinar(elemento: HTMLElement): void {
    requestAnimationFrame(() => {
      const el = this.popoverEl()?.nativeElement;
      if (!el) return;
      const alturaReal = el.getBoundingClientRect().height;
      if (Math.abs(alturaReal - this.alturaPopover) > 1) {
        this.alturaPopover = alturaReal;
        this.medirYPosicionar(elemento);
      }
    });
  }

  /** Vuelve a medir el target actual y reubica spotlight/popover, sin tocar el scroll (ver el
   * comentario en onScroll: llamar a scrollIntoView acá arma un loop) y sin volver a medir el
   * popover (ver alturaPopover). La usan resize y scroll mientras el tour ya está abierto en un
   * paso; posicionar() (con scrollIntoView) sigue siendo la única forma de llegar a un paso
   * nuevo. */
  private reposicionar(): void {
    const paso = this.pasoInfo();
    if (!paso) return;
    const elemento = document.querySelector<HTMLElement>(paso.selector);
    if (!elemento) return;
    this.medirYPosicionar(elemento);
  }

  /** Devuelve directamente una posición que entra en pantalla (clampeada con la altura conocida
   * del popover), sin depender de una segunda pasada que vuelva a leer su posición renderizada
   * — esa segunda lectura era la causa real de "no tiene dónde ponerse": con un target más alto
   * que la pantalla, el popover se reposiciona en cada frame de un scroll largo, y cada
   * corrección leía el `top` de ANTES (Angular todavía no había pintado el nuevo), así que la
   * corrección siempre iba un paso atrás y nunca alcanzaba a asentarse dentro del viewport. */
  private calcularPosicionPopover(r: RectObjetivo & { bottom: number }): PosicionPopover {
    const alturaPopover = this.alturaPopover;
    const espacioAbajo = window.innerHeight - r.bottom;
    const arriba = espacioAbajo < ESPACIO_MINIMO_ABAJO && r.top > ESPACIO_MINIMO_ABAJO;
    const topPreferido = arriba ? r.top - MARGEN - alturaPopover : r.bottom + MARGEN;
    const top = Math.max(MARGEN, Math.min(topPreferido, window.innerHeight - MARGEN - alturaPopover));
    const left = Math.max(MARGEN, Math.min(r.left + r.width / 2 - ANCHO_POPOVER / 2, window.innerWidth - ANCHO_POPOVER - MARGEN));
    return { top, left };
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
