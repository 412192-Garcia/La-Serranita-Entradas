import { Injectable } from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {TipoEntrada} from '../models/tipo-entrada';
import {environment} from '../../environments/environment';

@Injectable({
  providedIn: 'root',
})
export class TipoEntradaService {
  constructor(private http: HttpClient) { }
  private apiUrl = `${environment.apiBase}/tipos-entrada`;

  getTiposEntrada(): Observable<TipoEntrada[]> {
    return this.http.get<TipoEntrada[]>(this.apiUrl);
  }

  crear(tipo: Partial<TipoEntrada>): Observable<TipoEntrada> {
    return this.http.post<TipoEntrada>(this.apiUrl, tipo);
  }

  actualizar(id: number, tipo: Partial<TipoEntrada>): Observable<TipoEntrada> {
    return this.http.put<TipoEntrada>(`${this.apiUrl}/${id}`, tipo);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
