import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export type TipoMovimientoCaja = 'RETIRO' | 'APORTE';

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
  fecha: string;
}

export interface OperacionCaja {
  tipo: 'VENTA' | 'RETIRO' | 'APORTE' | 'INGRESO_ENTRADAS';
  fecha: string;
  /** Null en los ingresos de entradas físicas: no mueven plata. */
  monto: number | null;
  formaPago: string | null;
  detalle: string;
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

  registrarRetiro(monto: number, motivo: string, tipo: TipoMovimientoCaja): Observable<Caja> {
    return this.http.post<Caja>(`${this.cajaUrl}/retiros`, { monto, motivo, tipo });
  }

  /** Igual que registrarRetiro, pero un ADMIN cargándolo en la caja de OTRO usuario (ej. mientras la está cerrando). */
  registrarRetiroComoAdmin(cajaId: number, monto: number, motivo: string, tipo: TipoMovimientoCaja): Observable<Caja> {
    return this.http.post<Caja>(`${this.cajaUrl}/${cajaId}/retiros`, { monto, motivo, tipo });
  }

  registrarIngresoEntradas(cantidad: number): Observable<Caja> {
    return this.http.post<Caja>(`${this.cajaUrl}/ingresos-entradas`, { cantidad });
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
