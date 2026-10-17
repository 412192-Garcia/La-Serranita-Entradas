import { Injectable, signal } from '@angular/core';

export type TipoToast = 'exito' | 'error' | 'info';

export interface Toast {
  id: number;
  tipo: TipoToast;
  texto: string;
}

/** Cuánto queda en pantalla. Los errores un poco más: hay que alcanzar a leerlos. */
const DURACION_MS: Record<TipoToast, number> = { exito: 4000, info: 5000, error: 7000 };
/** Más de esto en pantalla a la vez se va el más viejo: que no tape la pantalla. */
const MAXIMO = 3;

/**
 * Avisos cortos de la app ("Mail reenviado", "Horario guardado"): aparecen abajo a la derecha
 * (abajo al centro en el celular), se van solos y no corren el resto de la pantalla. Para el
 * resultado de una acción que ya terminó; lo que hay que corregir en un formulario sigue al lado
 * del campo, y lo que hay que confirmar va con ConfirmacionService.
 *
 * Los muestra ToastHost, montado una sola vez en App.
 */
@Injectable({ providedIn: 'root' })
export class ToastService {
  readonly toasts = signal<Toast[]>([]);
  private siguienteId = 1;

  exito(texto: string): void {
    this.mostrar('exito', texto);
  }

  error(texto: string): void {
    this.mostrar('error', texto);
  }

  info(texto: string): void {
    this.mostrar('info', texto);
  }

  cerrar(id: number): void {
    this.toasts.update((lista) => lista.filter((t) => t.id !== id));
  }

  private mostrar(tipo: TipoToast, texto: string): void {
    // El mismo aviso dos veces seguidas (doble toque) no se apila.
    if (this.toasts().some((t) => t.texto === texto && t.tipo === tipo)) return;
    const toast: Toast = { id: this.siguienteId++, tipo, texto };
    this.toasts.update((lista) => [...lista, toast].slice(-MAXIMO));
    setTimeout(() => this.cerrar(toast.id), DURACION_MS[tipo]);
  }
}
