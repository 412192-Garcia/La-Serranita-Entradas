import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ArticuloVario } from '../models/articulo-vario';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root',
})
export class ArticuloVarioService {
  constructor(private http: HttpClient) {}
  private apiUrl = `${environment.apiBase}/articulos-varios`;

  getArticulos(): Observable<ArticuloVario[]> {
    return this.http.get<ArticuloVario[]>(this.apiUrl);
  }

  crear(articulo: Partial<ArticuloVario>): Observable<ArticuloVario> {
    return this.http.post<ArticuloVario>(this.apiUrl, articulo);
  }

  actualizar(id: number, articulo: Partial<ArticuloVario>): Observable<ArticuloVario> {
    return this.http.put<ArticuloVario>(`${this.apiUrl}/${id}`, articulo);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
