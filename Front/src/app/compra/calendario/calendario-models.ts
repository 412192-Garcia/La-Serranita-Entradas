export interface DiaCalendario {
  numero: number | null;
  fecha: Date;
  esHoy: boolean;
  esPasado: boolean;
  abierto: boolean;
  /**
   * Sólo "hoy": el parque abre hoy pero ya pasó el límite de compra online. Se pinta como un día que
   * estuvo abierto (verde pálido, igual que un pasado abierto) y no se puede elegir.
   */
  compraCerrada?: boolean;
  seleccionado: boolean;
}
