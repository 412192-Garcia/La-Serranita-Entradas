import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface RetiroCaja {
  id: number;
  monto: number;
  motivo: string;
  fecha: string;
}

export interface ConteoDenominacion {
  denominacion: number;
  cantidad: number;
}

export type FormaPagoPosnet = 'TARJETA' | 'MERCADO_PAGO_QR';

export interface CierrePosnet {
  id: number;
  formaPago: FormaPagoPosnet;
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
  tipo: 'VENTA' | 'RETIRO' | 'INGRESO_ENTRADAS';
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
  cierresPosnet: CierrePosnet[];

  entradasFisicasInicial: number | null;
  entradasFisicasFinal: number | null;
  entradasFisicasEsperadas: number | null;
  diferenciaEntradas: number | null;
  totalIngresosEntradas: number;
  ingresosEntradas: IngresoEntradas[];

  operaciones: OperacionCaja[] | null;

  /** Unidades de entrada vendidas (no extras ni artículos), sin importar la forma de pago. Null hasta el cierre. */
  totalEntradasVendidas: number | null;
  entradasVendidasPorTipo: EntradasPorTipo[] | null;
}

export interface EntradasPorTipo {
  nombreTipo: string;
  cantidad: number;
}

export interface CierrePosnetInput {
  formaPago: FormaPagoPosnet;
  monto: number;
  nota?: string | null;
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

  registrarRetiro(monto: number, motivo: string): Observable<Caja> {
    return this.http.post<Caja>(`${this.cajaUrl}/retiros`, { monto, motivo });
  }

  registrarIngresoEntradas(cantidad: number): Observable<Caja> {
    return this.http.post<Caja>(`${this.cajaUrl}/ingresos-entradas`, { cantidad });
  }

  cerrar(
    conteoEfectivo: ConteoDenominacion[],
    cierresPosnet: CierrePosnetInput[],
    entradasFisicasFinal: number,
    cambioContado: number | null
  ): Observable<Caja> {
    return this.http.post<Caja>(`${this.cajaUrl}/cerrar`, { conteoEfectivo, cierresPosnet, entradasFisicasFinal, cambioContado });
  }

  /** Detalle completo de cualquier caja (ADMIN), sin importar quién la abrió. */
  obtenerDetalle(cajaId: number): Observable<Caja> {
    return this.http.get<Caja>(`${this.cajaUrl}/${cajaId}/detalle`);
  }

  /** Corrige un cierre ya hecho (ej. un billete mal contado). Sólo el boletero dueño de esa caja puede corregirla. */
  corregirCierre(
    cajaId: number,
    conteoEfectivo: ConteoDenominacion[],
    cierresPosnet: CierrePosnetInput[],
    entradasFisicasFinal: number,
    cambioContado: number | null
  ): Observable<Caja> {
    return this.http.put<Caja>(`${this.cajaUrl}/${cajaId}/cierre`, { conteoEfectivo, cierresPosnet, entradasFisicasFinal, cambioContado });
  }
}
