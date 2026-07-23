export interface DiaCalendario {
  numero: number | null;
  fecha: Date;
  esHoy: boolean;
  esPasado: boolean;
  abierto: boolean;
  seleccionado: boolean;
}
