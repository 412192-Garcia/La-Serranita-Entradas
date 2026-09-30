export interface Cupon {
  id: number;
  codigo: string;
  porcentajeDescuento: number | null;
  montoDescuento: number | null;
  fechaExpiracion: string | null;
  usosMaximos: number | null;
  usosActuales: number;
  activo: boolean;
  /** Primer día de compra en que vale; null = desde siempre. */
  fechaDesde: string | null;
  /** Con monto fijo: se resta una vez por compra o a cada entrada. */
  aplicaPor: 'COMPRA' | 'ENTRADA' | 'PRECIO_ENTRADA';
  /** Vacío = alcanza a todos los tipos de entrada. */
  tiposEntradaIds: number[];
  minEntradas: number | null;
  maxEntradasAfectadas: number | null;
  topeDescuento: number | null;
}
