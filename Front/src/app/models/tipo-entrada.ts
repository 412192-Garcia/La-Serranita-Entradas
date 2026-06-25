export interface TipoEntrada {
  id: number;
  nombre: string;
  descripcion: string;
  precio: number;
  activo: boolean;
  tipo: 'ENTRADA' | 'EXTRA';
}
