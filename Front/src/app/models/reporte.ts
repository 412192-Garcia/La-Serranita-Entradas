import { FormaPagoType } from './compra';

export interface AfluenciaDiaria {
  fecha: string;
  /** Demanda: pases reservados para venir ese día (por fecha de visita), estén validados o no. */
  pasesVendidosAnticipada: number;
  /** Ingreso real de anticipadas y regalos ese día (por fecha de validación — puede no coincidir
   * con el día reservado si el cliente vino otro día). */
  pasesValidadosAnticipada: number;
  /** Vendido directamente en la puerta (POS) ese día: ingresa en el momento. */
  pasesVendidosBoleteria: number;
}

export interface DesgloseTipoEntrada {
  tipoEntradaId: number;
  nombre: string;
  cantidadAnticipada: number;
  montoAnticipada: number;
  cantidadBoleteria: number;
  montoBoleteria: number;
}

export interface RecaudacionPorFormaPago {
  formaPago: FormaPagoType;
  etiqueta: string;
  cantidad: number;
  monto: number;
}

export interface ComprasPorEstado {
  estado: 'PENDIENTE_PAGO' | 'RESERVADO_EFECTIVO' | 'APROBADO' | 'USADO' | 'CANCELADO' | 'VENDIDO_EN_PUERTA' | 'REEMBOLSADA';
  cantidad: number;
}

export interface VentasPorHora {
  hora: number;
  cantidadComprasAnticipada: number;
  cantidadPasesAnticipada: number;
  cantidadComprasBoleteria: number;
  cantidadPasesBoleteria: number;
}

/** BOLETERIA = venta de puerta (POS); ANTICIPADA = pagada online o reservada de antemano. */
export interface VentasPorOrigen {
  origen: 'BOLETERIA' | 'ANTICIPADA';
  etiqueta: string;
  cantidad: number;
  monto: number;
}

/** Un turno de caja ya cerrado, tal como lo ve el admin en Reportes. */
export interface CajaResumen {
  id: number;
  usuarioNombre: string;
  fechaApertura: string;
  fechaCierre: string;
  montoInicial: number;
  totalRetiros: number;
  montoEsperado: number;
  montoContado: number;
  /** montoContado − montoEsperado: positivo es sobrante, negativo es faltante. Sólo efectivo. */
  diferencia: number;
  /** Tarjeta + QR combinados: lo cerrado en el/los posnet − lo vendido con tarjeta y QR. 0 si no hubo posnet. */
  diferenciaPosnet: number;
}

/** articuloVarioId null = líneas sin catálogo (descripción libre tipeada en el POS). */
export interface VentaArticuloVario {
  articuloVarioId: number | null;
  nombre: string;
  cantidad: number;
  monto: number;
}

/** Sólo cuenta ventas hechas después de que se empezó a guardar la promo usada en la compra. */
export interface UsoPromocion {
  promocionId: number;
  nombre: string;
  cantidadVentas: number;
  totalDescontado: number;
}

export interface VentasDolares {
  cantidadVentas: number;
  totalDolaresRecibidos: number;
  /** Suma de montoTotal (en pesos) de esas ventas. */
  totalEquivalenteArs: number;
  /** Promedio ponderado por dólares recibidos. Null si no hubo ventas en dólares en el rango. */
  cotizacionPromedio: number | null;
}

export interface ReporteResumen {
  desde: string;
  hasta: string;
  recaudacionTotal: number;
  cantidadCompras: number;
  /** Gente que realmente cruzó la puerta: venta en puerta + reservas anticipadas ya validadas por DNI. */
  personasIngresadas: number;
  afluenciaDiaria: AfluenciaDiaria[];
  desglosePorTipo: DesgloseTipoEntrada[];
  recaudacionPorFormaPago: RecaudacionPorFormaPago[];
  comprasPorEstado: ComprasPorEstado[];
  desgloseExtras: DesgloseTipoEntrada[];
  ventasPorHora: VentasPorHora[];
  ventasPorOrigen: VentasPorOrigen[];
  /** Suma de descuentoAplicado (cupones) de las compras cobradas en el rango. */
  totalDescuentos: number;
  cantidadComprasConDescuento: number;
  cajas: CajaResumen[];
  totalRetirosCajas: number;
  totalFaltantesCajas: number;
  totalSobrantesCajas: number;
  ventasArticulosVarios: VentaArticuloVario[];
  usoPromociones: UsoPromocion[];
  ventasDolares: VentasDolares;
}
