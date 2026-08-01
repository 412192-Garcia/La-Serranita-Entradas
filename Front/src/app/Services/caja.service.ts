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

/**
 * Mientras la caja está ABIERTA, el backend manda totalVentasEfectivo y efectivoEsperado
 * en null a propósito: si el boletero pudiera verlos antes de cerrar, el cierre dejaría
 * de ser un control real. Recién llegan completos en la respuesta del cierre.
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
  retiros: RetiroCaja[];
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

  abrir(montoInicial: number): Observable<Caja> {
    return this.http.post<Caja>(`${this.cajaUrl}/abrir`, { montoInicial });
  }

  registrarRetiro(monto: number, motivo: string): Observable<Caja> {
    return this.http.post<Caja>(`${this.cajaUrl}/retiros`, { monto, motivo });
  }

  cerrar(montoContado: number): Observable<Caja> {
    return this.http.post<Caja>(`${this.cajaUrl}/cerrar`, { montoContado });
  }
}
