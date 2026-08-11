import { signal, WritableSignal } from '@angular/core';

export type DireccionOrden = 'ASC' | 'DESC';
export type EstadoOrden = 'sinOrden' | 'asc' | 'desc';

export interface Ordenable<T extends string> {
  ordenarPor: WritableSignal<T>;
  direccionOrden: WritableSignal<DireccionOrden>;
  /** Si ya se estaba ordenando por esta columna, invierte la dirección; si no, pasa a ordenar por ella (ASC). */
  ordenarColumna(campo: T): void;
  estadoOrden(campo: T): EstadoOrden;
}

/** Estado + lógica de "click en columna ordena / vuelve a hacer click invierte", repetido igual en cada tabla admin (boletería, usuarios, tipos de entrada, artículos, promociones). */
export function crearOrdenable<T extends string>(campoInicial: T): Ordenable<T> {
  const ordenarPor = signal<T>(campoInicial);
  const direccionOrden = signal<DireccionOrden>('ASC');

  function ordenarColumna(campo: T): void {
    if (ordenarPor() === campo) {
      direccionOrden.update((d) => (d === 'ASC' ? 'DESC' : 'ASC'));
    } else {
      ordenarPor.set(campo);
      direccionOrden.set('ASC');
    }
  }

  function estadoOrden(campo: T): EstadoOrden {
    if (ordenarPor() !== campo) return 'sinOrden';
    return direccionOrden() === 'ASC' ? 'asc' : 'desc';
  }

  return { ordenarPor, direccionOrden, ordenarColumna, estadoOrden };
}
