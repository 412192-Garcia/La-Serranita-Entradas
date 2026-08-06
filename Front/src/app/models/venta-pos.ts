/** Una línea de artículo en el carrito del POS, ya sea de catálogo o libre. */
export interface FilaArticuloCarrito {
  articuloVarioId: number | null;
  descripcionLibre: string | null;
  nombre: string;
  precioUnitario: number;
  cantidad: number;
}
