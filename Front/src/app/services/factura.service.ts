import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Factura } from '../models/factura';

const CLAVE_HABILITADA = 'facturacion-habilitada';

@Injectable({ providedIn: 'root' })
export class FacturaService {
  private http = inject(HttpClient);
  private url = `${environment.apiBase}/interno/facturas`;

  /**
   * Si el backend tiene la facturación configurada (token de AfipSDK). Sin eso el POS no
   * muestra las opciones de factura. Se recuerda el último valor conocido para que, si la
   * tablet arranca sin señal, el selector siga apareciendo igual que antes del corte.
   */
  readonly habilitada = signal<boolean>(this.leerHabilitadaGuardada());

  actualizarEstadoServicio(): void {
    this.http.get<{ habilitada: boolean }>(`${this.url}/estado-servicio`).subscribe({
      next: ({ habilitada }) => {
        this.habilitada.set(habilitada);
        try {
          localStorage.setItem(CLAVE_HABILITADA, String(habilitada));
        } catch {
          // Sin storage (modo privado, etc.): sólo se pierde el recuerdo entre sesiones.
        }
      },
      // Sin conexión se queda con el último valor conocido.
      error: () => {},
    });
  }

  /** Reenvía la factura por mail (ADMIN): para cuando el envío automático falló. */
  reenviarMail(facturaId: number): Observable<void> {
    return this.http.post<void>(`${this.url}/${facturaId}/reenviar-mail`, null);
  }

  /** 404 si la compra no se facturó. */
  porCompra(compraId: number): Observable<Factura> {
    return this.http.get<Factura>(`${this.url}/compra/${compraId}`);
  }

  private leerHabilitadaGuardada(): boolean {
    try {
      return localStorage.getItem(CLAVE_HABILITADA) === 'true';
    } catch {
      return false;
    }
  }
}
