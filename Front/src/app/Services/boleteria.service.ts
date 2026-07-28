import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { TipoEntrada } from '../models/tipo-entrada';
import { FormaPagoType } from '../models/compra';

export type EstadoCompra =
  | 'PENDIENTE_PAGO'
  | 'RESERVADO_EFECTIVO'
  | 'APROBADO'
  | 'USADO'
  | 'CANCELADO';

export interface DetalleReserva {
  id: number;
  cantidad: number;
  /** Subconjunto de TipoEntrada que devuelve el backend en el detalle de la compra. */
  tipoEntrada: Pick<TipoEntrada, 'id' | 'nombre' | 'precio' | 'tipo'> | null;
}

export interface Reserva {
  id: number;
  codigoReserva: string;
  cliente: { id: number; dni: string; nombre: string; apellido: string } | null;
  contactEmail: string | null;
  contactPhone: string | null;
  /** Null cuando la compra es un regalo: quien lo recibe puede usarlo el día que prefiera. */
  fechaVisita: string | null;
  montoTotal: number;
  descuentoAplicado: number;
  estado: EstadoCompra;
  formaPago: FormaPagoType | null;
  detalles: DetalleReserva[] | null;
  /** Momento exacto del check-in en boletería (null si todavía no se validó). */
  fechaValidacion: string | null;
  /** Usuario de boletería que validó el ingreso. */
  usuarioValidador: string | null;
}

export interface EditarContactoRequest {
  nombre?: string;
  apellido?: string;
  email?: string;
  telefono?: string;
}

@Injectable({
  providedIn: 'root',
})
export class BoleteriaService {
  private http = inject(HttpClient);

  private comprasUrl = `${environment.apiBase}/compras`;
  private internoUrl = `${environment.apiBase}/interno/compras`;

  /** Busca las compras asociadas al DNI del titular. */
  buscarPorDni(dni: string): Observable<Reserva[]> {
    return this.http.get<Reserva[]>(`${this.comprasUrl}/dni/${dni}`);
  }

  /** Todas las reservas para un día de visita puntual (yyyy-MM-dd), ordenadas por código. */
  buscarPorFecha(fecha: string): Observable<Reserva[]> {
    return this.http.get<Reserva[]>(`${this.comprasUrl}/fecha/${fecha}`);
  }

  /**
   * Todas las reservas, sin filtrar por fecha (el parque es laxo: una entrada
   * comprada para otro día igual se deja usar), ordenadas por fecha de visita.
   */
  buscarTodas(): Observable<Reserva[]> {
    return this.http.get<Reserva[]>(this.comprasUrl);
  }

  // Quién valida o cobra lo resuelve el backend a partir del JWT, así que estas
  // dos operaciones no mandan el id del operador en el cuerpo.

  /** Compra ya pagada online: habilita el ingreso del grupo (pasa a USADO). */
  validarIngreso(compraId: number): Observable<Reserva> {
    return this.http.put<Reserva>(`${this.comprasUrl}/${compraId}/validar`, {});
  }

  /** Reserva con pago en efectivo: cobra en caja y habilita el ingreso (pasa a USADO). */
  cobrarEfectivoYValidar(compraId: number): Observable<Reserva> {
    return this.http.post<Reserva>(`${this.internoUrl}/${compraId}/confirmar-pago-efectivo`, {});
  }

  /** Corrige nombre/apellido del titular y su contacto. No toca fecha, entradas ni montos. */
  editarContacto(compraId: number, datos: EditarContactoRequest): Observable<Reserva> {
    return this.http.put<Reserva>(`${this.internoUrl}/${compraId}/contacto`, datos);
  }

  /** Reenvía el comprobante ya enviado (y el aviso al receptor, si es un regalo). */
  reenviarMail(compraId: number): Observable<void> {
    return this.http.post<void>(`${this.internoUrl}/${compraId}/reenviar-mail`, {});
  }
}
