import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { etiquetaFormaPago } from '../models/forma-pago';
import { Pagina } from './boleteria.service';

export type TipoMovimientoCaja = 'RETIRO' | 'APORTE';

export type TipoMovimientoEntradas = 'INGRESO' | 'RETIRO';

export interface RetiroCaja {
  id: number;
  monto: number;
  motivo: string;
  tipo: TipoMovimientoCaja;
  fecha: string;
}

export interface ConteoDenominacion {
  denominacion: number;
  cantidad: number;
}

export type FormaPagoPosnet = 'TARJETA' | 'MERCADO_PAGO_QR';

export interface CierrePosnet {
  id: number;
  /** Null = cargado combinado (Tarjeta+QR juntos, sin distinguir). */
  formaPago: FormaPagoPosnet | null;
  monto: number;
  nota: string | null;
  fecha: string;
}

export interface IngresoEntradas {
  id: number;
  cantidad: number;
  motivo: string | null;
  tipo: TipoMovimientoEntradas;
  fecha: string;
}

export interface OperacionCaja {
  tipo: 'VENTA' | 'RETIRO' | 'APORTE' | 'INGRESO_ENTRADAS' | 'RETIRO_ENTRADAS';
  fecha: string;
  /** Null en los ingresos de entradas físicas: no mueven plata. */
  monto: number | null;
  formaPago: string | null;
  detalle: string;
  /** Sólo en ventas: id de la Compra, para poder cancelarla o editarla. Null en retiros/ingresos. */
  compraId: number | null;
}

/** Nombre corto para mostrar en el detalle de caja: la forma de pago cruda del backend es un enum, no algo para mostrar tal cual. */
export function etiquetaTipoOperacion(op: OperacionCaja): string {
  if (op.tipo === 'VENTA') return etiquetaFormaPago(op.formaPago);
  if (op.tipo === 'INGRESO_ENTRADAS') return 'Ingreso entradas';
  if (op.tipo === 'RETIRO_ENTRADAS') return 'Retiro entradas';
  if (op.tipo === 'APORTE') return 'Aporte';
  return 'Retiro';
}

/**
 * Mientras la caja está ABIERTA, el backend manda todos los totales "esperados" (efectivo,
 * tarjeta, QR, entradas físicas) y el detalle de operaciones en null a propósito: si el
 * boletero pudiera verlos antes de cerrar, el cierre dejaría de ser un control real. Recién
 * llegan completos en la respuesta del cierre.
 */
export interface Caja {
  id: number;
  estado: 'ABIERTA' | 'CERRADA';
  fechaApertura: string;
  montoInicial: number;
  fechaCierre: string | null;
  totalVentasEfectivo: number | null;
  totalRetiros: number;
  efectivoEsperado: number | null;
  montoContado: number | null;
  diferencia: number | null;
  /** Total en billetes chicos cargado de una vez; ya está incluido en montoContado. Null hasta el cierre. */
  cambioContado: number | null;
  retiros: RetiroCaja[];

  conteoEfectivo: ConteoDenominacion[];

  totalVentasTarjeta: number | null;
  totalVentasQr: number | null;
  totalCerradoTarjeta: number | null;
  totalCerradoQr: number | null;
  diferenciaTarjeta: number | null;
  diferenciaQr: number | null;
  /** Si el cierre se cargó combinado (Tarjeta+QR juntos), estos tres reemplazan a los de arriba; si no, quedan null. */
  totalVentasPosnet: number | null;
  totalCerradoPosnet: number | null;
  diferenciaPosnet: number | null;
  cierresPosnet: CierrePosnet[];

  entradasFisicasInicial: number | null;
  entradasFisicasCortadas: number | null;
  entradasFisicasEsperadas: number | null;
  diferenciaEntradas: number | null;
  totalIngresosEntradas: number;
  ingresosEntradas: IngresoEntradas[];

  operaciones: OperacionCaja[] | null;

  /** Unidades de entrada vendidas (no extras ni artículos), sin importar la forma de pago. Null hasta el cierre. */
  totalEntradasVendidas: number | null;
  entradasVendidasPorTipo: EntradasPorTipo[] | null;

  /**
   * Si hubo o no alguna venta pagada en dólares (sigue siendo un cobro en efectivo, sólo
   * cambia la moneda física). A diferencia de los totales, esto se expone aunque la caja
   * siga ABIERTA (es sólo un booleano, no un monto).
   */
  huboVentaDolares: boolean;
  /** Dólares esperados según lo que entró de cada venta en dólares. Null hasta el cierre. */
  dolaresEsperado: number | null;
  /** Dólares que el boletero contó al cerrar. Null si esta caja no tuvo ventas en dólares. */
  dolaresContado: number | null;
  diferenciaDolares: number | null;
}

export interface EntradasPorTipo {
  nombreTipo: string;
  cantidad: number;
}

export interface CierrePosnetInput {
  /** Null = combinado (Tarjeta+QR juntos, sin distinguir). */
  formaPago: FormaPagoPosnet | null;
  monto: number;
  nota?: string | null;
}

/** Una caja abierta ahora mismo, para el dashboard de hoy del admin. */
export interface CajaAbierta {
  id: number;
  usuarioNombre: string;
  fechaApertura: string;
  montoInicial: number;
  /** Vendido hasta el momento (efectivo + tarjeta + QR), en vivo. */
  totalVendido: number;
  totalEntradasVendidas: number;
}

/** Detalle de una caja todavía abierta (ADMIN) — ver obtenerOperaciones. */
export interface CajaDetalleAbierta {
  operaciones: OperacionCaja[];
  totalVentasEfectivo: number;
  totalVentasTarjeta: number;
  totalVentasQr: number;
  totalEntradasVendidas: number;
  entradasVendidasPorTipo: EntradasPorTipo[];
  huboVentaDolares: boolean;
  /** Inicial + ingresos − retiros − ya cortadas vendiendo: cuántas le quedan al boletero en el talonario. Null si esta caja no tiene un inicial cargado. */
  entradasFisicasRestantes: number | null;
}

/** Una caja ya cerrada, para el listado paginado de "Cajas cerradas" (ver obtenerCajasCerradas). */
export interface CajaCerrada {
  id: number;
  usuarioNombre: string;
  fechaApertura: string;
  fechaCierre: string;
  montoInicial: number;
  totalRetiros: number;
  montoEsperado: number;
  montoContado: number;
  diferencia: number;
}

/** Página de "Cajas cerradas": mismas propiedades que Pagina<T>, más los totales de retiros/
 * faltantes/sobrantes de TODO lo que matchea el filtro (no sólo la página actual). */
export interface CajasCerradasResponse extends Pagina<CajaCerrada> {
  totalRetiros: number;
  totalFaltantes: number;
  totalSobrantes: number;
}

@Injectable({
  providedIn: 'root',
})
export class CajaService {
  private http = inject(HttpClient);
  private cajaUrl = `${environment.apiBase}/interno/caja`;

  /** La caja abierta del boletero autenticado, o null si no tiene ninguna en curso. */
  getActual(): Observable<Caja | null> {
    return this.http.get<Caja | null>(`${this.cajaUrl}/actual`);
  }

  abrir(montoInicial: number, entradasFisicasInicial: number): Observable<Caja> {
    return this.http.post<Caja>(`${this.cajaUrl}/abrir`, { montoInicial, entradasFisicasInicial });
  }

  /** idempotencyKey/fechaOriginal/esReintentoEncolado sólo los manda la cola offline del POS
   * (ver OperacionesPendientesService). cajaId es la caja propia del boletero en ese momento:
   * no se usa para resolver dónde aplicar el movimiento (eso lo sigue derivando el backend del
   * usuario autenticado), sólo queda guardado en el rechazo si esto se rechaza, para saber
   * exactamente qué caja reabrir y reintentar. */
  registrarRetiro(
    monto: number,
    motivo: string,
    tipo: TipoMovimientoCaja,
    idempotencyKey?: string,
    fechaOriginal?: string,
    esReintentoEncolado?: boolean,
    cajaId?: number
  ): Observable<Caja> {
    return this.http.post<Caja>(`${this.cajaUrl}/retiros`, { monto, motivo, tipo, idempotencyKey, fechaOriginal, esReintentoEncolado, cajaId });
  }

  /** Igual que registrarRetiro, pero un ADMIN cargándolo en la caja de OTRO usuario (ej. mientras la está cerrando). */
  registrarRetiroComoAdmin(cajaId: number, monto: number, motivo: string, tipo: TipoMovimientoCaja): Observable<Caja> {
    return this.http.post<Caja>(`${this.cajaUrl}/${cajaId}/retiros`, { monto, motivo, tipo });
  }

  registrarIngresoEntradas(
    cantidad: number,
    tipo: TipoMovimientoEntradas,
    motivo?: string,
    idempotencyKey?: string,
    fechaOriginal?: string,
    esReintentoEncolado?: boolean,
    cajaId?: number
  ): Observable<Caja> {
    return this.http.post<Caja>(`${this.cajaUrl}/ingresos-entradas`, { cantidad, tipo, motivo, idempotencyKey, fechaOriginal, esReintentoEncolado, cajaId });
  }

  /** Igual que registrarIngresoEntradas, pero un ADMIN cargándolo en la caja de OTRO usuario por id. */
  registrarIngresoEntradasComoAdmin(cajaId: number, cantidad: number, motivo: string | undefined, tipo: TipoMovimientoEntradas): Observable<Caja> {
    return this.http.post<Caja>(`${this.cajaUrl}/${cajaId}/ingresos-entradas`, { cantidad, motivo, tipo });
  }

  /** Cerrar caja es ADMIN-only (ya no self-service): cierra la caja de cualquier usuario por id. */
  cerrarComoAdmin(
    cajaId: number,
    conteoEfectivo: ConteoDenominacion[],
    cierresPosnet: CierrePosnetInput[],
    entradasFisicasCortadas: number,
    cambioContado: number | null,
    dolaresContado: number | null
  ): Observable<Caja> {
    return this.http.post<Caja>(`${this.cajaUrl}/${cajaId}/cerrar`, {
      conteoEfectivo,
      cierresPosnet,
      entradasFisicasCortadas,
      cambioContado,
      dolaresContado,
    });
  }

  /** Detalle completo de cualquier caja (ADMIN), sin importar quién la abrió. */
  obtenerDetalle(cajaId: number): Observable<Caja> {
    return this.http.get<Caja>(`${this.cajaUrl}/${cajaId}/detalle`);
  }

  /** Todas las cajas abiertas ahora mismo (ADMIN), para el dashboard de hoy. */
  obtenerCajasAbiertas(): Observable<CajaAbierta[]> {
    return this.http.get<CajaAbierta[]>(`${this.cajaUrl}/abiertas`);
  }

  /** Ventas, retiros/aportes e ingresos de entradas de una caja, más un resumen de lo vendido
   * (ADMIN) — a diferencia de obtenerDetalle, funciona con la caja todavía abierta. */
  obtenerOperaciones(cajaId: number): Observable<CajaDetalleAbierta> {
    return this.http.get<CajaDetalleAbierta>(`${this.cajaUrl}/${cajaId}/operaciones`);
  }

  /** Cajas cerradas dentro del rango, paginadas y opcionalmente filtradas por boletero (ADMIN):
   * soporta boleteros con meses de turnos sin traerlos todos de una. ordenarPor admite cualquier
   * campo de CajaCerrada salvo "totalRetiros" (no es una columna propia de Caja). */
  obtenerCajasCerradas(
    desde: string,
    hasta: string,
    usuarioNombre: string | null,
    ordenarPor: string,
    direccion: 'ASC' | 'DESC',
    page: number,
    size: number
  ): Observable<CajasCerradasResponse> {
    let params = new HttpParams()
      .set('desde', desde)
      .set('hasta', hasta)
      .set('ordenarPor', ordenarPor)
      .set('direccion', direccion)
      .set('page', page)
      .set('size', size);
    if (usuarioNombre) params = params.set('usuarioNombre', usuarioNombre);
    return this.http.get<CajasCerradasResponse>(`${this.cajaUrl}/cerradas`, { params });
  }

  /** Corrige un cierre ya hecho (ej. un billete mal contado). ADMIN-only, cualquier caja cerrada. */
  corregirCierre(
    cajaId: number,
    conteoEfectivo: ConteoDenominacion[],
    cierresPosnet: CierrePosnetInput[],
    entradasFisicasCortadas: number,
    cambioContado: number | null,
    dolaresContado: number | null
  ): Observable<Caja> {
    return this.http.put<Caja>(`${this.cajaUrl}/${cajaId}/cierre`, {
      conteoEfectivo,
      cierresPosnet,
      entradasFisicasCortadas,
      cambioContado,
      dolaresContado,
    });
  }
}
