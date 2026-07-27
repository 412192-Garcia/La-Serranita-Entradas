import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Cupon } from '../models/cupon';

// El cupón tiene una única definición en models/cupon.ts; se reexporta acá para
// que los componentes de Configuración sigan importándolo desde este servicio.
export type { Cupon };

export interface DiaApertura {
  id: number | null;
  fecha: string;
  abierto: boolean;
  horaApertura: string | null;
  horaCierre: string | null;
}

export interface HorarioGeneral {
  id: number;
  horaApertura: string;
  horaCierre: string;
}

export interface FamiliaCupon {
  id: number;
  nombre: string;
  prefijo: string;
  descripcion: string | null;
  cupones: Cupon[];
}

export interface CrearCuponRequest {
  codigo?: string | null;
  usosMaximos?: number | null;
  fechaExpiracion?: string | null;
  porcentajeDescuento?: number | null;
  montoDescuento?: number | null;
}

export interface CrearFamiliaCuponRequest {
  nombre: string;
  prefijo: string;
  descripcion?: string | null;
  cantidad: number;
  usosMaximos?: number | null;
  fechaExpiracion?: string | null;
  porcentajeDescuento?: number | null;
  montoDescuento?: number | null;
}

export interface DescuentoEfectivo {
  id: number;
  tipoEntradaId: number;
  tipoEntradaNombre: string;
  cantidadPases: number;
  precioPromocionalTotal: number;
}

export interface CrearDescuentoEfectivoRequest {
  tipoEntradaId: number;
  cantidadPases: number;
  precioPromocionalTotal: number;
}

@Injectable({
  providedIn: 'root',
})
export class ConfiguracionService {
  private http = inject(HttpClient);

  private diasUrl = `${environment.apiBase}/dias-apertura`;
  private configUrl = `${environment.apiBase}/configuracion`;
  private cuponesUrl = `${environment.apiBase}/cupones`;
  private descuentosUrl = `${environment.apiBase}/descuentos-efectivo`;

  // ---------- Días de apertura y horarios ----------

  getMes(year: number, month: number): Observable<DiaApertura[]> {
    return this.http.get<DiaApertura[]>(`${this.diasUrl}/mes`, { params: { year, month } });
  }

  setAbierto(fecha: string, abierto: boolean): Observable<DiaApertura> {
    return this.http.put<DiaApertura>(`${this.diasUrl}/fecha/${fecha}`, null, {
      params: { abierto },
    });
  }

  setHorarioEspecial(
    fecha: string,
    horaApertura: string | null,
    horaCierre: string | null
  ): Observable<DiaApertura> {
    return this.http.put<DiaApertura>(`${this.diasUrl}/fecha/${fecha}/horario`, {
      horaApertura,
      horaCierre,
    });
  }

  getHorarioGeneral(): Observable<HorarioGeneral> {
    return this.http.get<HorarioGeneral>(`${this.configUrl}/horario`);
  }

  actualizarHorarioGeneral(horaApertura: string, horaCierre: string): Observable<HorarioGeneral> {
    return this.http.put<HorarioGeneral>(`${this.configUrl}/horario`, { horaApertura, horaCierre });
  }

  // ---------- Cupones y familias ----------

  crearCupon(request: CrearCuponRequest): Observable<Cupon> {
    return this.http.post<Cupon>(this.cuponesUrl, request);
  }

  generarFamilia(request: CrearFamiliaCuponRequest): Observable<FamiliaCupon> {
    return this.http.post<FamiliaCupon>(`${this.cuponesUrl}/familias/generar`, request);
  }

  /** Solo cupones creados de forma individual (no incluye los generados como parte de un lote). */
  getCupones(): Observable<Cupon[]> {
    return this.http.get<Cupon[]>(`${this.cuponesUrl}/individuales`);
  }

  getFamilias(): Observable<FamiliaCupon[]> {
    return this.http.get<FamiliaCupon[]>(`${this.cuponesUrl}/familias`);
  }

  // ---------- Precios por grupo (efectivo) ----------

  getDescuentosEfectivo(): Observable<DescuentoEfectivo[]> {
    return this.http.get<DescuentoEfectivo[]>(this.descuentosUrl);
  }

  crearDescuentoEfectivo(request: CrearDescuentoEfectivoRequest): Observable<DescuentoEfectivo> {
    return this.http.post<DescuentoEfectivo>(this.descuentosUrl, request);
  }

  actualizarDescuentoEfectivo(id: number, request: CrearDescuentoEfectivoRequest): Observable<DescuentoEfectivo> {
    return this.http.put<DescuentoEfectivo>(`${this.descuentosUrl}/${id}`, request);
  }

  eliminarDescuentoEfectivo(id: number): Observable<void> {
    return this.http.delete<void>(`${this.descuentosUrl}/${id}`);
  }
}
