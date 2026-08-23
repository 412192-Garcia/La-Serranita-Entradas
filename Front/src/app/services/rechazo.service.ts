import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export type TipoOperacionRechazada = 'VENTA' | 'RETIRO_APORTE' | 'INGRESO_ENTRADAS' | 'COMPROBANTE_EMAIL';

export interface OperacionRechazada {
  id: number;
  tipoOperacion: TipoOperacionRechazada;
  /** JSON crudo de lo que se intentó mandar — se parsea en el componente para mostrarlo. */
  payload: string;
  motivo: string;
  usuario: string;
  fecha: string;
  resuelto: boolean;
  notaResolucion: string | null;
  resueltoPor: string | null;
  fechaResolucion: string | null;
}

@Injectable({
  providedIn: 'root',
})
export class RechazoService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiBase}/interno/rechazos`;

  /** Sin filtro trae todas; true/false sólo pendientes o sólo resueltas. */
  listar(resuelto?: boolean): Observable<OperacionRechazada[]> {
    const params: Record<string, string> = {};
    if (resuelto !== undefined) params['resuelto'] = String(resuelto);
    return this.http.get<OperacionRechazada[]>(this.apiUrl, { params });
  }

  resolver(id: number, nota?: string): Observable<OperacionRechazada> {
    return this.http.put<OperacionRechazada>(`${this.apiUrl}/${id}/resolver`, { nota });
  }

  /** Sólo para RETIRO_APORTE/INGRESO_ENTRADAS rechazados por caja cerrada, todos de la MISMA
   * caja: los reabre a todos juntos en un solo ciclo de reabrir/cerrar (si se encolaron varias
   * operaciones sin conexión, cada una quedó como un rechazo separado — sin esto, resolverlas de
   * a una reabriría y volvería a cerrar la misma caja una vez por cada una). */
  reabrirYReintentarLote(ids: number[]): Observable<OperacionRechazada[]> {
    return this.http.post<OperacionRechazada[]>(`${this.apiUrl}/reabrir-y-reintentar-lote`, ids);
  }
}
