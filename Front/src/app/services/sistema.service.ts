import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { EstadoSistema, Incidente, Pagina, RegistroAuditoria, Rendimiento } from '../models/sistema';

export interface FiltroAuditoria {
  desde?: string;
  hasta?: string;
  usuario?: string;
  accion?: string;
  texto?: string;
  pagina?: number;
}

/** Sistema (SUPERADMIN): estado de cada área, historial de acciones, errores y rendimiento. */
@Injectable({ providedIn: 'root' })
export class SistemaService {
  private http = inject(HttpClient);
  private url = `${environment.apiBase}/interno/sistema`;

  estado(): Observable<EstadoSistema> {
    return this.http.get<EstadoSistema>(`${this.url}/estado`);
  }

  auditoria(filtro: FiltroAuditoria): Observable<Pagina<RegistroAuditoria>> {
    let params = new HttpParams().set('pagina', filtro.pagina ?? 0).set('tamanio', 50);
    for (const [clave, valor] of Object.entries(filtro)) {
      if (clave !== 'pagina' && valor !== undefined && valor !== null && String(valor).trim() !== '') {
        params = params.set(clave, String(valor).trim());
      }
    }
    return this.http.get<Pagina<RegistroAuditoria>>(`${this.url}/auditoria`, { params });
  }

  filtrosAuditoria(): Observable<{ acciones: string[]; usuarios: string[] }> {
    return this.http.get<{ acciones: string[]; usuarios: string[] }>(`${this.url}/auditoria/filtros`);
  }

  incidentes(filtro: 'pendientes' | 'resueltos' | 'todos', area: string | null, pagina: number): Observable<Pagina<Incidente>> {
    let params = new HttpParams().set('filtro', filtro).set('pagina', pagina).set('tamanio', 30);
    if (area) params = params.set('area', area);
    return this.http.get<Pagina<Incidente>>(`${this.url}/incidentes`, { params });
  }

  rendimiento(): Observable<Rendimiento> {
    return this.http.get<Rendimiento>(`${this.url}/rendimiento`);
  }

  resolverIncidente(id: number): Observable<Incidente> {
    return this.http.post<Incidente>(`${this.url}/incidentes/${id}/resolver`, null);
  }
}
