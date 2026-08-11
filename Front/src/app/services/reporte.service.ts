import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ReporteResumen } from '../models/reporte';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root',
})
export class ReporteService {
  constructor(private http: HttpClient) {}
  private apiUrl = `${environment.apiBase}/reportes`;

  getResumen(desde: string, hasta: string): Observable<ReporteResumen> {
    return this.http.get<ReporteResumen>(`${this.apiUrl}/resumen`, { params: { desde, hasta } });
  }
}
