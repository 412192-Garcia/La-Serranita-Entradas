import { Injectable } from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';

export interface EstadoCompraResponse {
  estado: string;
}

export interface CompraResponseDTO {
  id: number;
  preferenceId?: string | null;
  formaPago: 'MERCADO_PAGO' | 'EFECTIVO_BOLETERIA';
  initPoint?: string | null;
  estado: string;
  montoTotal: number;
  mensaje?: string;
}

export interface CotizacionResponseDTO {
  subtotal: number;
  ahorro: number;
}

@Injectable({
  providedIn: 'root',
})
export class CompraService {
  constructor(private http: HttpClient) { }
  private apiUrl = 'http://localhost:8080/api/compras';

  /**
   * Inicia la compra en el backend (crea la compra PENDIENTE + preferencia MP)
   */
  iniciarCompraConPago(compraRequest: any): Observable<CompraResponseDTO> {
    return this.http.post<CompraResponseDTO>(`${this.apiUrl}/iniciar-pago`, compraRequest);
  }

  /**
   * Consulta en el backend el estado actual de la compra guardada en DB.
   */
  obtenerEstadoCompra(compraId: number): Observable<EstadoCompraResponse> {
    return this.http.get<EstadoCompraResponse>(`${this.apiUrl}/${compraId}/estado`);
  }

  /**
   * Cotiza el subtotal aplicando los descuentos por grupo/forma de pago, sin persistir nada.
   */
  cotizar(cotizacionRequest: any): Observable<CotizacionResponseDTO> {
    return this.http.post<CotizacionResponseDTO>(`${this.apiUrl}/cotizar`, cotizacionRequest);
  }
}
