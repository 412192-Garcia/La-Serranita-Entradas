/** Una línea de artículo en el carrito del POS, ya sea de catálogo o libre. */
export interface FilaArticuloCarrito {
  articuloVarioId: number | null;
  descripcionLibre: string | null;
  nombre: string;
  precioUnitario: number;
  cantidad: number;
}

/**
 * Línea de tipo de entrada que viene fija de una reserva cargada en el POS y no se puede
 * editar desde el carrito: los extras (Menú Almuerzo, etc.) y cualquier tipo de entrada de la
 * reserva que no esté en el catálogo actual del POS. Se muestra, suma al total y se manda en
 * el cobro, pero sin +/- ni ✕.
 */
export interface LineaEntradaFija {
  tipoEntradaId: number;
  nombre: string;
  precioUnitario: number;
  cantidad: number;
}
