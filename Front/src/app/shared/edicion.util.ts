import { signal, WritableSignal } from '@angular/core';

export interface EstadoEdicion<T> {
  editandoId: WritableSignal<T | null>;
  editar(id: T): void;
  cancelarEdicion(): void;
}

/** El signal de "qué id se está editando" + sus dos métodos de toggle, repetidos igual en cada
 * card admin de tipo crear/editar (usuarios, promociones, artículos, tipos de entrada). El
 * llenado/vaciado de los campos del formulario de cada entidad queda en el propio componente:
 * eso sí difiere entidad por entidad. */
export function crearEstadoEdicion<T = number>(): EstadoEdicion<T> {
  const editandoId = signal<T | null>(null);

  function editar(id: T): void {
    editandoId.set(id);
  }

  function cancelarEdicion(): void {
    editandoId.set(null);
  }

  return { editandoId, editar, cancelarEdicion };
}
