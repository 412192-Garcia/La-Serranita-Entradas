import { Component, input, output, signal } from '@angular/core';
import { TipoEntrada } from '../../models/tipo-entrada';
import { PesosPipe } from '../../shared/pesos.pipe';

/** Números de un toque para las entradas obligatorias (pagas). Más que eso, se escribe a mano. */
const NUMEROS_RAPIDOS = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];

@Component({
  selector: 'app-catalogo-entradas',
  imports: [PesosPipe],
  templateUrl: './catalogo-entradas.html',
  styleUrl: './catalogo-entradas.css',
})
export class CatalogoEntradas {
  entradas = input<TipoEntrada[]>([]);
  cantidades = input<Record<number, number>>({});

  cantidadesChange = output<Record<number, number>>();

  readonly numerosRapidos = NUMEROS_RAPIDOS;

  /** Tipos (obligatorios) cuyo campo de cantidad manual (10+) está abierto. */
  private customAbierto = signal<ReadonlySet<number>>(new Set());

  getCantidad(id: number): number {
    return this.cantidades()[id] ?? 0;
  }

  private emitCantidad(id: number, cantidad: number): void {
    const valor = Math.max(0, Math.floor(cantidad) || 0);
    this.cantidadesChange.emit({ ...this.cantidades(), [id]: valor });
  }

  cambiarCantidad(id: number, delta: number): void {
    this.emitCantidad(id, this.getCantidad(id) + delta);
  }

  mostrarCustom(id: number): boolean {
    return this.customAbierto().has(id);
  }

  /** Toca un número: lo fija como cantidad; tocar el mismo de nuevo la vacía. */
  elegirCantidad(id: number, n: number): void {
    this.emitCantidad(id, this.getCantidad(id) === n ? 0 : n);
    this.customAbierto.update((s) => {
      if (!s.has(id)) return s;
      const copia = new Set(s);
      copia.delete(id);
      return copia;
    });
  }

  /** Abre/cierra el paso a cantidad manual (11+). Al abrir, arranca en 11 para no pisar la grilla fija. */
  toggleCustom(id: number): void {
    const estabaAbierto = this.customAbierto().has(id);
    this.customAbierto.update((s) => {
      const copia = new Set(s);
      if (!copia.delete(id)) copia.add(id);
      return copia;
    });
    if (!estabaAbierto && this.getCantidad(id) < 11) {
      this.emitCantidad(id, 11);
    }
  }

  incrementarCustom(id: number): void {
    this.emitCantidad(id, Math.max(11, this.getCantidad(id) + 1));
  }

  /** Por debajo de 11 ya no tiene sentido seguir en modo manual: vuelve a la grilla fija en 10. */
  decrementarCustom(id: number): void {
    const actual = this.getCantidad(id);
    if (actual <= 11) {
      this.emitCantidad(id, 10);
      this.customAbierto.update((s) => {
        const copia = new Set(s);
        copia.delete(id);
        return copia;
      });
    } else {
      this.emitCantidad(id, actual - 1);
    }
  }
}
