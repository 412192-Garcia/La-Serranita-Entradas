export type DestinoFactura = 'IMPRIMIR' | 'MAIL';

export type EstadoFactura = 'PENDIENTE' | 'EMITIDA' | 'ERROR';

export type EstadoImpresion = 'PENDIENTE' | 'ENVIADO' | 'IMPRESO' | 'ERROR';

/** Ticketera anunciada por el agente de impresión de la PC de la entrada. */
export interface ImpresoraConectada {
  nombre: string;
  agente: string;
}

/**
 * Lo que eligió el boletero en el POS: "Imprimir factura" o "Enviar por mail" (+ email).
 * MAIL con email vacío = no se factura (es lo que queda por defecto al cobrar en efectivo).
 */
export interface FacturacionPos {
  destino: DestinoFactura;
  email: string | null;
  /** Ticketera elegida en esta tablet (sólo IMPRIMIR). Null = la única conectada. */
  impresora?: string | null;
}

export interface Factura {
  id: number;
  compraId: number;
  estado: EstadoFactura;
  destino: DestinoFactura;
  email: string | null;
  puntoVenta: number;
  tipoComprobante: number;
  numero: number | null;
  cae: string | null;
  caeVencimiento: string | null;
  fechaEmision: string | null;
  importeTotal: number;
  importeNeto: number;
  importeIva: number;
  intentos: number;
  ultimoError: string | null;
  qrUrl: string | null;
  mailEnviadoEn: string | null;
  /** Último intento de imprimir el ticket; null si nunca se mandó a imprimir. */
  impresionEstado: EstadoImpresion | null;
  impresionError: string | null;
}

/** "0037-00000019", como se imprime en el comprobante. */
export function numeroComprobante(f: Pick<Factura, 'puntoVenta' | 'numero'>): string {
  return `${String(f.puntoVenta).padStart(4, '0')}-${String(f.numero ?? 0).padStart(8, '0')}`;
}
