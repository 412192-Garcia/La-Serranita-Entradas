/** MERCADO_PAGO y EFECTIVO_BOLETERIA salen de la compra online; TARJETA y
 *  MERCADO_PAGO_QR sólo existen en la venta presencial del POS de boletería;
 *  RESERVA_ADMIN sólo la crea un ADMIN a mano, sin cobrar nada por acá (invitados,
 *  ventas por agencia con el cobro resuelto por fuera, etc); SIN_COBRO es una venta de
 *  puerta cuyo total quedó en $0 (todo bonificado), sin nada que cobrar. Pagar en dólares
 *  NO es una forma de pago aparte: sigue siendo EFECTIVO_BOLETERIA, sólo cambia la moneda
 *  física (ver el checkbox "Pagó en dólares" dentro del botón Efectivo del POS). */
export type FormaPagoType = 'MERCADO_PAGO' | 'EFECTIVO_BOLETERIA' | 'TARJETA' | 'MERCADO_PAGO_QR' | 'RESERVA_ADMIN' | 'SIN_COBRO';

/** Las formas de pago que el boletero elige para cobrar en el POS. */
export type FormaPagoPos = 'EFECTIVO_BOLETERIA' | 'TARJETA' | 'MERCADO_PAGO_QR';

/** Lo que puede llevar una venta de puerta: una forma de cobro, o SIN_COBRO cuando el total
 *  quedó en $0 (todo bonificado) y no hay nada que cobrar. */
export type FormaPagoVentaPos = FormaPagoPos | 'SIN_COBRO';

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
