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
}
