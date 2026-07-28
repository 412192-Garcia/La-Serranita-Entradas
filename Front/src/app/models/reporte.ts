export interface AfluenciaDiaria {
  fecha: string;
  pasesVendidos: number;
  pasesValidados: number;
}

export interface DesgloseTipoEntrada {
  tipoEntradaId: number;
  nombre: string;
  cantidad: number;
  montoRecaudado: number;
}

export interface RecaudacionPorFormaPago {
  formaPago: 'MERCADO_PAGO' | 'EFECTIVO_BOLETERIA';
  etiqueta: string;
  cantidad: number;
  monto: number;
}

export interface ComprasPorEstado {
  estado: 'PENDIENTE_PAGO' | 'RESERVADO_EFECTIVO' | 'APROBADO' | 'USADO' | 'CANCELADO';
  cantidad: number;
}

export interface VentasPorHora {
  hora: number;
  cantidadCompras: number;
  cantidadPases: number;
}

export interface ReporteResumen {
  desde: string;
  hasta: string;
  recaudacionTotal: number;
  cantidadCompras: number;
  afluenciaDiaria: AfluenciaDiaria[];
  desglosePorTipo: DesgloseTipoEntrada[];
  recaudacionPorFormaPago: RecaudacionPorFormaPago[];
  comprasPorEstado: ComprasPorEstado[];
  desgloseExtras: DesgloseTipoEntrada[];
  ventasPorHora: VentasPorHora[];
}
