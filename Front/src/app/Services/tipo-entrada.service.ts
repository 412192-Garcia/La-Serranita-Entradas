import { Injectable } from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {TipoEntrada} from '../models/tipo-entrada';

@Injectable({
  providedIn: 'root',
})
export class TipoEntradaService {
  constructor(private http: HttpClient) { }
  private apiUrl = 'http://localhost:8080/api/tipos-entrada';

  getTiposEntrada(): Observable<TipoEntrada[]> {
    return this.http.get<TipoEntrada[]>(this.apiUrl);
  }
}
