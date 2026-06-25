import { Injectable } from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class DiaAperturaService {
  private apiUrl = 'http://localhost:8080/api/dias-apertura/abiertos';

  constructor(private http: HttpClient) { }

  getDiasApertura(mes: number, anio: number): Observable<string[]> {

    const mesBackend = mes + 1;

    const params = new HttpParams()
      .set('month', mesBackend.toString())
      .set('year', anio.toString());

    return this.http.get<string[]>(this.apiUrl, { params });
  }
}
