export type DestinoFactura = 'IMPRIMIR' | 'MAIL' | 'NINGUNO';

export type EstadoFactura = 'PENDIENTE' | 'EMITIDA' | 'ERROR' | 'ANULADA';

export type EstadoImpresion = 'PENDIENTE' | 'ENVIADO' | 'IMPRESO' | 'ERROR';

/** Ticketera anunciada por el agente de impresión de la PC de la entrada. */
export interface ImpresoraConectada {
  nombre: string;
  agente: string;
  /** El agente la ve prendida (responde por red, o Windows la da por lista). */
  disponible: boolean;
  /** Qué le pasa cuando no está disponible: "apagada o desconectada", "sin papel"... */
  detalle: string | null;
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
  /** La venta se canceló o cambió de monto: la factura se anula o ya tiene nota de crédito. */
  anulacionPedida: boolean;
  /** Último intento de imprimir el ticket; null si nunca se mandó a imprimir. */
  impresionEstado: EstadoImpresion | null;
  impresionError: string | null;
  /** Ítems facturados: "cantidad<TAB>descripción<TAB>subtotal" por línea. */
  detalle?: string | null;
  /** Sólo en una Factura A (o su nota de crédito). */
  receptorCuit?: string | null;
  receptorRazonSocial?: string | null;
}

/** Acciones > Control de facturas. */
export interface ControlFacturacion {
  problemas: { factura: Factura; codigoCompra: string | null; creada: string | null }[];
  certificadoVence: string | null;
  /** Negativo = ya venció. */
  diasParaVencer: number | null;
  /** Sólo en producción. */
  numeracionControlada: boolean;
  numeracionControladaEn: string | null;
  desfases: DesfaseNumeracion[];
}

export interface DesfaseNumeracion {
  puntoVenta: number;
  tipoComprobante: number;
  ultimoBase: number;
  ultimoArca: number;
}

export interface TotalesFacturacion {
  puntoVenta: number;
  tipoComprobante: number;
  cantidad: number;
  total: number;
  neto: number;
  iva: number;
}

/** Renglón de una factura manual (Acciones > Facturación manual). */
export interface FacturaManualItem {
  /** ENTRADA / ARTICULO si salió del catálogo, LIBRE si se escribió a mano (decide el concepto de ARCA). */
  tipo: 'ENTRADA' | 'ARTICULO' | 'LIBRE';
  cantidad: number;
  descripcion: string;
  subtotal: number;
}

/** Receptor de una Factura A manual. */
export interface ReceptorFacturaA {
  /** 11 dígitos, sin guiones. */
  cuit: string;
  razonSocial: string;
  /** Domicilio comercial: va impreso en la Factura A. */
  domicilio: string;
  /** Código ARCA (ver CONDICIONES_IVA_FACTURA_A). */
  condicionIva: number;
}

/**
 * Lo que dice el padrón de ARCA de un CUIT. NO_EXISTE: no está en el padrón; CON_ERROR: ARCA no da
 * la constancia (cancelada, bloqueada...); NO_DISPONIBLE: no se pudo consultar (se carga a mano).
 */
export interface PadronContribuyente {
  cuit: string;
  estado: 'ENCONTRADO' | 'NO_EXISTE' | 'CON_ERROR' | 'NO_DISPONIBLE';
  razonSocial: string | null;
  domicilio: string | null;
  condicionIva: number | null;
  condicionIvaTexto: string | null;
  admiteFacturaA: boolean;
  mensaje: string | null;
}

/** NINGUNO = sólo queda el PDF para descargar. Con receptor = Factura A; sin = Factura B. */
export interface FacturaManualPedido {
  items: FacturaManualItem[];
  destino: DestinoFactura;
  email: string | null;
  impresora: string | null;
  receptor?: ReceptorFacturaA | null;
}

/** Condiciones frente al IVA del receptor que admiten Factura A (códigos de ARCA). */
export const CONDICIONES_IVA_FACTURA_A: { codigo: number; nombre: string }[] = [
  { codigo: 1, nombre: 'IVA Responsable Inscripto' },
  { codigo: 6, nombre: 'Responsable Monotributo' },
  { codigo: 13, nombre: 'Monotributista Social' },
  { codigo: 16, nombre: 'Monotributo Trabajador Independiente Promovido' },
];

/** Códigos de comprobante de ARCA que usa el sistema (igual que WsfeService.CBTE_TIPO_* en el backend). */
export const CBTE_FACTURA_A = 1;
export const CBTE_NOTA_CREDITO_A = 3;
export const CBTE_FACTURA_B = 6;
export const CBTE_NOTA_CREDITO_B = 8;

/** "A" o "B". */
export function letraComprobante(tipo: number): 'A' | 'B' {
  return tipo === CBTE_FACTURA_A || tipo === CBTE_NOTA_CREDITO_A ? 'A' : 'B';
}

/** Nota de crédito (A o B): resta en los totales. */
export function esNotaDeCredito(tipo: number): boolean {
  return tipo === CBTE_NOTA_CREDITO_A || tipo === CBTE_NOTA_CREDITO_B;
}

/** "Factura A" / "Nota de Crédito B" a partir del código de ARCA (1, 3, 6, 8). */
export function nombreComprobante(tipo: number): string {
  return `${esNotaDeCredito(tipo) ? 'Nota de Crédito' : 'Factura'} ${letraComprobante(tipo)}`;
}

/** CUIT de 11 dígitos con el dígito verificador bien. */
export function cuitValido(cuit: string): boolean {
  if (!/^\d{11}$/.test(cuit)) return false;
  const pesos = [5, 4, 3, 2, 7, 6, 5, 4, 3, 2];
  const suma = pesos.reduce((s, p, i) => s + p * Number(cuit[i]), 0);
  let verificador = 11 - (suma % 11);
  if (verificador === 11) verificador = 0;
  return verificador !== 10 && verificador === Number(cuit[10]);
}

/** "30-71701573-4". */
export function formatearCuit(cuit: string): string {
  return cuit.length === 11 ? `${cuit.slice(0, 2)}-${cuit.slice(2, 10)}-${cuit.slice(10)}` : cuit;
}

/** Una venta de un turno con su factura, para la ventana "Ventas y facturas". */
export interface VentaFactura {
  compraId: number;
  codigoReserva: string;
  fecha: string | null;
  montoTotal: number;
  formaPago: string;
  /** "2x General, 1x Souvenir". */
  detalle: string;
  /** Null = la venta no se facturó. */
  factura: Factura | null;
}

/** "0037-00000019", como se imprime en el comprobante. */
export function numeroComprobante(f: Pick<Factura, 'puntoVenta' | 'numero'>): string {
  return `${String(f.puntoVenta).padStart(4, '0')}-${String(f.numero ?? 0).padStart(8, '0')}`;
}
