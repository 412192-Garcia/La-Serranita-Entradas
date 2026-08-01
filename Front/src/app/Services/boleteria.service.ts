import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { TipoEntrada } from '../models/tipo-entrada';
import { FormaPagoPos, FormaPagoType } from '../models/compra';

export type EstadoCompra =
  | 'PENDIENTE_PAGO'
  | 'RESERVADO_EFECTIVO'
  | 'APROBADO'
  | 'USADO'
  | 'CANCELADO'
  /** Venta cerrada en la puerta: no es una reserva anticipada, cae en el filtro "Boletería". */
  | 'VENDIDO_EN_PUERTA'
  /** Pagada online y devuelta antes de que el visitante entrara. */
  | 'REEMBOLSADA';

/** Cómo se llegó a la compra: en el filtro de boletería, o de antemano (online/efectivo). */
export type TipoListadoCompra = 'BOLETERIA' | 'ANTICIPADA';

/** Página tal como la devuelve Spring Data — sólo los campos que usamos. */
export interface Pagina<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/** Claves de orden que soporta el backend para /compras/buscar. */
export type CampoOrdenCompras = 'fechaVisita' | 'codigoReserva' | 'estado' | 'monto' | 'titular';

export interface FiltroBusquedaCompras {
  texto?: string;
  fecha?: string;
  tipo?: TipoListadoCompra;
  estados?: EstadoCompra[];
  formaPago?: FormaPagoType;
  ordenarPor?: CampoOrdenCompras;
  direccion?: 'ASC' | 'DESC';
  page?: number;
  size?: number;
}

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

/** Una línea del carrito del POS, tal como la espera el backend. */
export interface LineaVentaPos {
  tipoEntradaId: number;
  cantidad: number;
}

export interface VentaPosRequest {
  formaPago: FormaPagoPos;
  entradas: LineaVentaPos[];
}

export interface CotizacionResponse {
  subtotal: number;
  /** Cuánto se ahorra respecto del precio de lista (sólo hay promo pagando en efectivo). */
  ahorro: number;
}

@Injectable({
  providedIn: 'root',
})
export class BoleteriaService {
  private http = inject(HttpClient);

  private comprasUrl = `${environment.apiBase}/compras`;
  private internoUrl = `${environment.apiBase}/interno/compras`;

  /**
   * Búsqueda paginada de boletería: filtra y pagina en el backend (un día con miles de
   * visitantes no puede traerse entero a memoria del navegador para filtrarlo ahí).
   */
  buscar(filtro: FiltroBusquedaCompras): Observable<Pagina<Reserva>> {
    let params = new HttpParams();
    if (filtro.texto) params = params.set('texto', filtro.texto);
    if (filtro.fecha) params = params.set('fecha', filtro.fecha);
    if (filtro.tipo) params = params.set('tipo', filtro.tipo);
    for (const estado of filtro.estados ?? []) params = params.append('estados', estado);
    if (filtro.formaPago) params = params.set('formaPago', filtro.formaPago);
    if (filtro.ordenarPor) params = params.set('ordenarPor', filtro.ordenarPor);
    if (filtro.direccion) params = params.set('direccion', filtro.direccion);
    params = params.set('page', filtro.page ?? 0).set('size', filtro.size ?? 50);
    return this.http.get<Pagina<Reserva>>(`${this.comprasUrl}/buscar`, { params });
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

  /**
   * Reembolsa una compra pagada online que todavía no fue utilizada: dispara el
   * reembolso real en Mercado Pago y, si funciona, la pasa a REEMBOLSADA.
   */
  reembolsar(compraId: number): Observable<Reserva> {
    return this.http.post<Reserva>(`${this.internoUrl}/${compraId}/reembolsar`, {});
  }

  /**
   * Precio del carrito según la forma de pago, sin registrar nada. Hace falta porque
   * el precio promocional por grupo sólo existe pagando en efectivo: el total cambia
   * según qué botón de cobro elija el boletero.
   */
  cotizar(formaPago: FormaPagoPos, entradas: LineaVentaPos[]): Observable<CotizacionResponse> {
    return this.http.post<CotizacionResponse>(`${this.comprasUrl}/cotizar`, { formaPago, entradas });
  }

  /** Venta presencial: cobra y habilita el ingreso en un solo paso (queda USADO). */
  registrarVentaPos(venta: VentaPosRequest): Observable<Reserva> {
    return this.http.post<Reserva>(`${this.internoUrl}/venta-pos`, venta);
  }
}
