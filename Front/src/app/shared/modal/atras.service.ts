import { DestroyRef, Injectable, WritableSignal, effect, inject, signal } from '@angular/core';
import { NavigationCancel, NavigationEnd, NavigationError, NavigationStart, Router } from '@angular/router';

/** Algo abierto sobre la pantalla (modal, menú, hoja) que el botón atrás tiene que cerrar. */
export interface EntradaAtras {
  cerrar: () => void;
  estaAbierta: () => boolean;
}

/**
 * Para hojas y pantallas completas que sólo existen en celular: el botón atrás las cierra mientras
 * estén abiertas. Se llama desde un contexto de inyección. En pantallas más anchas no hace nada.
 */
export function cerrarConAtrasEnCelular(estaAbierta: () => boolean, cerrar: () => void, anchoMaximo = 600): void {
  const atras = inject(AtrasService);
  const entrada: EntradaAtras = { cerrar, estaAbierta };
  let registrada = false;

  effect(() => {
    const abierta = estaAbierta();
    if (abierta && !registrada && window.matchMedia(`(max-width: ${anchoMaximo}px)`).matches) {
      registrada = true;
      atras.abrir(entrada);
    } else if (!abierta && registrada) {
      registrada = false;
      atras.cerrado(entrada);
    }
  });

  inject(DestroyRef).onDestroy(() => {
    if (registrada) atras.cerrado(entrada);
  });
}

/** Si en celular se está dentro de una sección o en la lista (ver secciones-celular.css). */
export function crearSeccionCelular(): WritableSignal<boolean> {
  const abierta = signal(false);
  cerrarConAtrasEnCelular(() => abierta(), () => abierta.set(false));
  return abierta;
}

/**
 * Hace que el botón atrás cierre lo último que se abrió en vez de salir de la pantalla: cada cosa
 * abierta suma una entrada al historial con la misma URL, que el router ignora.
 */
@Injectable({ providedIn: 'root' })
export class AtrasService {
  private router = inject(Router);

  private pila: EntradaAtras[] = [];
  /** Entradas cerradas por la app que están saliendo del historial: ese popstate no cierra nada. */
  private popstatesPropios = 0;
  private cambiandoDeRuta = false;
  /** Lo que espera a que termine de salir del historial lo que se acaba de cerrar. */
  private pendientes: (() => void)[] = [];

  constructor() {
    window.addEventListener('popstate', () => this.alVolver());
    this.router.events.subscribe((e) => {
      if (e instanceof NavigationStart) {
        this.cambiandoDeRuta = e.navigationTrigger !== 'popstate';
      } else if (e instanceof NavigationEnd || e instanceof NavigationCancel || e instanceof NavigationError) {
        this.cambiandoDeRuta = false;
      }
    });
  }

  abrir(entrada: EntradaAtras): void {
    this.pila.push(entrada);
    history.pushState(history.state, '');
  }

  /** Se cerró por la app (botón, fondo, fin de la operación): saca su entrada del historial. */
  cerrado(entrada: EntradaAtras): void {
    if (!this.quitar(entrada) || this.cambiandoDeRuta) return;
    this.popstatesPropios++;
    history.back();
  }

  /**
   * Para cerrar un modal y abrir otro en su lugar: si se abre el nuevo antes de que el viejo salga
   * del historial, se cruzan y al cerrar el nuevo se vuelve una pantalla de más. Llamarlo después
   * de cerrar el viejo (en el próximo tick, cuando ya se destruyó).
   */
  cuandoTermineDeCerrar(fn: () => void): void {
    if (this.popstatesPropios === 0) fn();
    else this.pendientes.push(fn);
  }

  /** Para un enlace que abandona la pantalla: el router reemplaza la entrada al navegar. */
  soltar(entrada: EntradaAtras): void {
    this.quitar(entrada);
  }

  private quitar(entrada: EntradaAtras): boolean {
    const i = this.pila.indexOf(entrada);
    if (i === -1) return false;
    this.pila.splice(i, 1);
    return true;
  }

  private alVolver(): void {
    if (this.popstatesPropios > 0) {
      this.popstatesPropios--;
      if (this.popstatesPropios === 0) {
        const pendientes = this.pendientes;
        this.pendientes = [];
        pendientes.forEach((fn) => fn());
      }
      return;
    }
    const entrada = this.pila.pop();
    if (!entrada) return;
    entrada.cerrar();
    // Si no se pudo cerrar (ej. cobro en curso en el Point), sigue abierta y necesita su entrada.
    setTimeout(() => {
      if (entrada.estaAbierta()) this.abrir(entrada);
    }, 100);
  }
}
