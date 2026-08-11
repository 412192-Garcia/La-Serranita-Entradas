import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Promocion } from '../models/promocion';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root',
})
export class PromocionService {
  constructor(private http: HttpClient) {}
  private apiUrl = `${environment.apiBase}/promociones`;

  getPromociones(): Observable<Promocion[]> {
    return this.http.get<Promocion[]>(this.apiUrl);
  }

  crear(promocion: Partial<Promocion>): Observable<Promocion> {
    return this.http.post<Promocion>(this.apiUrl, promocion);
  }

  actualizar(id: number, promocion: Partial<Promocion>): Observable<Promocion> {
    return this.http.put<Promocion>(`${this.apiUrl}/${id}`, promocion);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
