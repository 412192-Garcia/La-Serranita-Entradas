export type FormaPagoType = 'MERCADO_PAGO' | 'EFECTIVO_BOLETERIA';

export interface ClienteData {
  nombre: string;
  apellido: string;
  dni: string;
  email: string;
  telefono: string;
  edad?: number | null;
  localidad?: string | null;
}

export interface ReceptorRegaloData {
  nombre: string;
  email: string;
  dni: string;
  /** Opcional. */
  telefono?: string | null;
}

export interface EntradaSeleccionada {
  id?: number;
  tipoEntradaId?: number;
  nombre: string;
  cantidad: number;
  precioUnitario: number;
  subtotal?: number;
}

export interface ResumenCompraData {
  fechaVisita: Date | null;
  esRegalo: boolean;
  entradas: EntradaSeleccionada[];
  cuponCodigo?: string | null;
  descuentoMonto: number;
  subtotal: number;
  subtotalLista: number;
  descuentoGrupo: number;
  total: number;
  formaPago: FormaPagoType;
  cliente: ClienteData | null;
  /** Sólo cuando esRegalo es true: a quién avisarle por mail. */
  receptor?: ReceptorRegaloData | null;
}
