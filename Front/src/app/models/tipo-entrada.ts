export interface TipoEntrada {
  id: number;
  nombre: string;
  descripcion: string;
  precio: number;
  activo: boolean;
  obligatorio: boolean;
  tipo: 'ENTRADA' | 'EXTRA';
  /** Cupo máximo vendible por día para este tipo. Null = sin límite. */
  maximoPorDia: number | null;
  /** Si es true, vender este tipo consume talonario físico (ej: Pase General adulto). */
  entregaEntrada: boolean;
  /** Posición manual en los listados de venta (POS, compra online). Null = todavía sin asignar, va al final. */
  orden: number | null;
}
