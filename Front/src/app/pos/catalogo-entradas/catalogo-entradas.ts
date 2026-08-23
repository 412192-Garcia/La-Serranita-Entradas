import { Component, effect, input, output, signal, untracked } from '@angular/core';
import { TipoEntrada } from '../../models/tipo-entrada';
import { DescuentoEfectivo } from '../../services/configuracion.service';
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
  /** Para decidir qué tipos muestran la grilla de accesos rápidos: ver tienePreciosPorGrupo. */
  descuentosEfectivo = input<DescuentoEfectivo[]>([]);

  cantidadesChange = output<Record<number, number>>();

  readonly numerosRapidos = NUMEROS_RAPIDOS;

  /** Tipos con al menos un escalón de precio por grupo cargado (ver Configuración > Descuentos
   * en efectivo): elegir la cantidad exacta de una vez tiene sentido ahí (se compra en grupo,
   * "familia de 4"), a diferencia de un tipo sin escalones donde un +/- simple alcanza. Antes
   * se usaba `obligatorio` para esto, pero ese campo significa otra cosa (si hace falta al
   * menos un pase de ese tipo para poder entrar) — coincidía en los datos de siempre, no por
   * diseño. */
  tienePreciosPorGrupo(tipoId: number): boolean {
    return this.descuentosEfectivo().some((d) => d.tipoEntradaId === tipoId);
  }

  /** Tipos (con accesos rápidos) cuyo campo de cantidad manual (10+) está abierto. */
  private customAbierto = signal<ReadonlySet<number>>(new Set());

  constructor() {
    // El carrito vuelve a {} tanto al vaciar como al cerrar el comprobante de una venta ya
    // cobrada ("Nueva venta") — en ambos casos la pantalla queda lista para el próximo cliente,
    // así que el catálogo también debe volver a mostrar los accesos rápidos en vez de quedarse
    // trabado en el campo manual de una venta que ya terminó.
    effect(() => {
      const sinCantidades = Object.keys(this.cantidades()).length === 0;
      if (sinCantidades) untracked(() => this.customAbierto.set(new Set()));
    });
  }

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

  /** El panel manual se queda abierto pase lo que pase con el número — cerrarlo automáticamente
   * al tocar 10 hacía que la fila de abajo ("+ Agregar artículo") se corriera hacia arriba justo
   * debajo del mouse durante un decremento rápido, y un segundo click terminaba abriendo ese
   * panel por error. Ahora sólo se cierra si el usuario elige explícitamente salir: tocando
   * "‹ Volver" o un número de la grilla fija (ver elegirCantidad y toggleCustom). */
  incrementarCustom(id: number): void {
    this.cambiarCantidad(id, 1);
  }

  decrementarCustom(id: number): void {
    this.cambiarCantidad(id, -1);
  }
}
