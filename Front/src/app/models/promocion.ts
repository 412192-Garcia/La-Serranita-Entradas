export interface Promocion {
  id: number;
  nombre: string;
  porcentajeDescuento: number | null;
  montoDescuento: number | null;
  activo: boolean;
}
