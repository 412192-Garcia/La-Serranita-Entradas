import { Injectable } from '@angular/core';

/**
 * Último snapshot conocido de los datos que el POS necesita para poder seguir vendiendo sin
 * conexión (catálogo, precios de grupo, promos, estado de la caja abierta).
 *
 * Va a localStorage y no al service worker a propósito: `ngsw-config.json` no cachea llamadas
 * a la API justamente para que nadie vea datos viejos sin darse cuenta. Acá el dato viejo es
 * deseable, pero sólo para este puñado de cosas y sólo como último recurso — por eso se maneja
 * explícito y acotado en vez de habilitar cacheo de API para todo.
 */
@Injectable({
  providedIn: 'root',
})
export class PosCacheService {
  private clave(nombre: string): string {
    return `serranita.pos.cache.${nombre}`;
  }

  guardar(nombre: string, valor: unknown): void {
    try {
      localStorage.setItem(this.clave(nombre), JSON.stringify(valor));
    } catch {
      // Sin espacio o storage bloqueado: seguir sin cache es peor pero no puede romper la venta.
    }
  }

  leer<T>(nombre: string): T | null {
    try {
      const crudo = localStorage.getItem(this.clave(nombre));
      return crudo ? (JSON.parse(crudo) as T) : null;
    } catch {
      return null;
    }
  }
}
