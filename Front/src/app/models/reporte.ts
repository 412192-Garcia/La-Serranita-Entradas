import { FormaPagoType } from './compra';

export interface AfluenciaDiaria {
  fecha: string;
  /** Reserva anticipada (pagada online o a cobrar en caja), sin contar venta en puerta. */
  pasesVendidosAnticipada: number;
  /** De esas reservas anticipadas, cuántas ya se validaron por DNI al llegar. */
  pasesValidadosAnticipada: number;
  /** Vendido directamente en la puerta (POS): siempre ingresa en el momento. */
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
  /** montoContado − montoEsperado: positivo es sobrante, negativo es faltante. */
  diferencia: number;
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
}
