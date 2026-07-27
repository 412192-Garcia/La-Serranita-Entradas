import { Injectable } from '@angular/core';
import {Observable} from 'rxjs';
import {Cupon} from '../models/cupon';
import {HttpClient} from '@angular/common/http';
import {environment} from '../../environments/environment';

@Injectable({
  providedIn: 'root',
})
export class CuponService {
  constructor(private http: HttpClient) { }
  private apiUrl = `${environment.apiBase}/cupones/codigo`;

  validarCupon(codigo: string): Observable<Cupon> {
    return this.http.get<Cupon>(`${this.apiUrl}/${codigo}`);
  }
}
