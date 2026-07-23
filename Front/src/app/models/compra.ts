export type FormaPagoType = 'MERCADO_PAGO' | 'EFECTIVO_BOLETERIA';

export interface ClienteData {
  nombre: string;
  apellido: string;
  dni: string;
  email: string;
  telefono: string;
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
}
