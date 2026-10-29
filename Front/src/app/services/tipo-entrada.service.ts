import { Injectable } from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable, catchError, shareReplay, tap, throwError} from 'rxjs';
import {TipoEntrada} from '../models/tipo-entrada';
import {environment} from '../../environments/environment';

@Injectable({
  providedIn: 'root',
})
export class TipoEntradaService {
  constructor(private http: HttpClient) { }
  private apiUrl = `${environment.apiBase}/tipos-entrada`;

  /** Se pide una vez; se vuelve a pedir si se edita un tipo desde acá. */
  private online$: Observable<TipoEntrada[]> | null = null;

  getTiposEntrada(): Observable<TipoEntrada[]> {
    return this.http.get<TipoEntrada[]>(this.apiUrl);
  }

  /** Los que se venden en la compra online: activos y no exclusivos del POS. */
  getTiposEntradaOnline(): Observable<TipoEntrada[]> {
    this.online$ ??= this.http.get<TipoEntrada[]>(`${this.apiUrl}/online`).pipe(
      catchError((err) => {
        this.online$ = null;
        return throwError(() => err);
      }),
      shareReplay(1)
    );
    return this.online$;
  }

  crear(tipo: Partial<TipoEntrada>): Observable<TipoEntrada> {
    return this.http.post<TipoEntrada>(this.apiUrl, tipo).pipe(tap(() => this.online$ = null));
  }

  actualizar(id: number, tipo: Partial<TipoEntrada>): Observable<TipoEntrada> {
    return this.http.put<TipoEntrada>(`${this.apiUrl}/${id}`, tipo).pipe(tap(() => this.online$ = null));
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`).pipe(tap(() => this.online$ = null));
  }

  reordenar(idsEnOrden: number[]): Observable<TipoEntrada[]> {
    return this.http.put<TipoEntrada[]>(`${this.apiUrl}/reordenar`, idsEnOrden).pipe(tap(() => this.online$ = null));
  }
}
