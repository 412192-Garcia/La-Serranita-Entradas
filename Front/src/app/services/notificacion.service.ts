import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

/** Mismos valores que el enum TipoNotificacion del backend. */
export type TipoNotificacion = 'CAJA_ATRASADA' | 'RECHAZO_OPERACION';

/** Qué tipos tienen algo pendiente de avisarle al usuario logueado ahora mismo. */
export type ResumenNotificaciones = Partial<Record<TipoNotificacion, boolean>>;

@Injectable({
  providedIn: 'root',
})
export class NotificacionService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiBase}/interno/notificaciones`;

  /** Qué tipos de aviso tienen pendientes ahora mismo, para el usuario logueado. ADMIN-only. */
  obtenerResumen(): Observable<ResumenNotificaciones> {
    return this.http.get<ResumenNotificaciones>(`${this.apiUrl}/resumen`);
  }

  /** Sólo tiene efecto real en tipos con desapareceAlVerse=true (ej. CAJA_ATRASADA): en los
   * demás (ej. RECHAZO_OPERACION) el aviso sólo se apaga resolviendo la entidad referida.
   * Devuelve los ids que estaban SIN ver hasta ahora (los que prendían el aviso), para señalarlos
   * en pantalla: una vez marcados, el aviso se apaga y ya no hay otra forma de saber cuáles eran. */
  marcarVistas(tipo: TipoNotificacion, refIds: number[]): Observable<number[]> {
    return this.http.post<number[]>(`${this.apiUrl}/${tipo}/marcar-vistas`, { refIds });
  }
}
