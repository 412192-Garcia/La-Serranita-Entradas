export interface Cupon {
  id: number;
  codigo: string;
  porcentajeDescuento: number | null;
  montoDescuento: number | null;
  fechaExpiracion: string;
  usosMaximos: number;
  usosActuales: number;
  activo: boolean;
}
