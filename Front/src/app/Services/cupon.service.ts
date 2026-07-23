import { Injectable } from '@angular/core';
import {Observable} from 'rxjs';
import {Cupon} from '../models/cupon';
import {HttpClient} from '@angular/common/http';

@Injectable({
  providedIn: 'root',
})
export class CuponService {
  constructor(private http: HttpClient) { }
  private apiUrl = 'http://localhost:8080/api/cupones/codigo';

  validarCupon(codigo: string): Observable<Cupon> {
    return this.http.get<Cupon>(`${this.apiUrl}/${codigo}`);
  }
}
