import { FormaPagoPos } from '../models/compra';

export interface OpcionPago {
  valor: FormaPagoPos;
  etiqueta: string;
}

/** El orden es el de uso real en una boletería: el efectivo es el caso más frecuente. */
export const FORMAS_PAGO: OpcionPago[] = [
  { valor: 'EFECTIVO_BOLETERIA', etiqueta: 'Efectivo' },
  { valor: 'TARJETA', etiqueta: 'Tarjeta' },
  { valor: 'MERCADO_PAGO_QR', etiqueta: 'QR' },
];

/** Nombre corto para mostrar: la forma de pago cruda del backend es un enum, no algo para mostrar tal cual. */
export function etiquetaFormaPago(formaPago: string | null): string {
  return FORMAS_PAGO.find((f) => f.valor === formaPago)?.etiqueta ?? formaPago ?? 'Venta';
}
