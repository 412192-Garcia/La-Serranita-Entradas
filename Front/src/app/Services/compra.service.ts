import { Injectable } from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../environments/environment';

export interface EstadoCompraResponse {
  estado: string;
}

export interface CompraResponseDTO {
  id: number;
  codigoReserva: string;
  preferenceId?: string | null;
  formaPago: 'MERCADO_PAGO' | 'EFECTIVO_BOLETERIA';
  initPoint?: string | null;
  estado: string;
  montoTotal: number;
  mensaje?: string;
  cliente?: { dni: string } | null;
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
  private apiUrl = `${environment.apiBase}/compras`;

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
   * Trae la compra completa (usado para resolver el código de reserva a partir
   * del id numérico que Mercado Pago devuelve en external_reference).
   */
  obtenerCompra(compraId: number): Observable<CompraResponseDTO> {
    return this.http.get<CompraResponseDTO>(`${this.apiUrl}/${compraId}`);
  }

  /**
   * Cotiza el subtotal aplicando los descuentos por grupo/forma de pago, sin persistir nada.
   */
  cotizar(cotizacionRequest: any): Observable<CotizacionResponseDTO> {
    return this.http.post<CotizacionResponseDTO>(`${this.apiUrl}/cotizar`, cotizacionRequest);
  }

  /**
   * Fuerza una reconciliación directa contra la API de Mercado Pago (no depende del
   * webhook). Se llama al volver del pago y como respaldo si el monitoreo del popup
   * no logra confirmar nada por su cuenta.
   */
  verificarPago(compraId: number): Observable<EstadoCompraResponse> {
    return this.http.post<EstadoCompraResponse>(`${this.apiUrl}/${compraId}/verificar-pago`, {});
  }
}
