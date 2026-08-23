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
  /** Rango (alternativa a `fecha`, usada por la vista agrupada por día): uno solo alcanza para un rango abierto. */
  fechaDesde?: string;
  fechaHasta?: string;
  /** Sólo los regalos (fechaVisita null). Mutuamente excluyente con fecha/fechaDesde/fechaHasta. */
  sinFecha?: boolean;
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
  /** Sólo en líneas de artículo (de catálogo): tipoEntrada viene null en ese caso. */
  articuloVario: { id: number; nombre: string; precioSugerido: number | null; activo: boolean } | null;
  /** Sólo en líneas de artículo libre (sin catálogo): tipoEntrada y articuloVario vienen null. */
  descripcionLibre: string | null;
  /** Precio cargado al vender, congelado en la compra — sólo para líneas de artículo (de catálogo o libres). Null en líneas de entrada (ésas derivan el precio de tipoEntrada). */
  precioUnitario: number | null;
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
  /** Datos de quien recibe el regalo: sólo vienen cargados cuando fechaVisita es null. */
  receptorNombre: string | null;
  receptorEmail: string | null;
  receptorDni: string | null;
  receptorTelefono: string | null;
}

export interface EditarContactoRequest {
  nombre?: string;
  apellido?: string;
  /** DNI del titular; no mandarlo (o vacío) deja el actual sin tocar. */
  dni?: string;
  email?: string;
  telefono?: string;
  /** Sólo aplica si la compra es un regalo (fechaVisita null). */
  receptorDni?: string;
}

/** Una línea del carrito del POS, tal como la espera el backend. */
export interface LineaVentaPos {
  tipoEntradaId: number;
  cantidad: number;
}

/** Un artículo vario del carrito: de catálogo (articuloVarioId) o libre (descripcionLibre), no ambos. */
export interface LineaArticuloPos {
  articuloVarioId?: number | null;
  descripcionLibre?: string | null;
  precioUnitario: number;
  cantidad: number;
}

/** Promo con nombre o descuento manual ad-hoc — mutuamente excluyentes, ambos opcionales. */
export interface DescuentoPos {
  promocionId?: number | null;
  descuentoManualPorcentaje?: number | null;
  descuentoManualMonto?: number | null;
}

export interface VentaPosRequest extends DescuentoPos {
  formaPago: FormaPagoPos;
  entradas: LineaVentaPos[];
  articulos?: LineaArticuloPos[];
  /** Cotización usada (ARS por USD). Sólo si el boletero cobró en dólares (sigue siendo EFECTIVO_BOLETERIA). */
  cotizacionDolar?: number | null;
  /** Dólares que entregó el cliente. Obligatorio si se manda cotizacionDolar. */
  dolaresRecibidos?: number | null;
  /** Clave contra duplicados al reintentar una venta encolada (ver OperacionesPendientesService). */
  idempotencyKey?: string;
  /** Cuándo se cobró de verdad, si la venta estuvo encolada sin conexión (ISO). */
  fechaOriginal?: string;
  /** True sólo en un reintento en segundo plano de la cola offline (ver OperacionesPendientesService). */
  esReintentoEncolado?: boolean;
  /** Caja del boletero al momento de cobrar: si esto se rechaza porque esa caja ya no está
   * abierta, queda guardado para que un admin sepa cuál reabrir y reintentar. */
  cajaId?: number;
}

/** Corrección de una venta de puerta (ADMIN): reemplaza entradas, artículos y forma de pago por completo (mandar la lista final, no un diff). */
export interface EditarVentaRequest {
  entradas: LineaVentaPos[];
  articulos: LineaArticuloPos[];
  formaPago: FormaPagoPos;
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
    if (filtro.fechaDesde) params = params.set('fechaDesde', filtro.fechaDesde);
    if (filtro.fechaHasta) params = params.set('fechaHasta', filtro.fechaHasta);
    if (filtro.sinFecha) params = params.set('sinFecha', 'true');
    if (filtro.tipo) params = params.set('tipo', filtro.tipo);
    for (const estado of filtro.estados ?? []) params = params.append('estados', estado);
    if (filtro.formaPago) params = params.set('formaPago', filtro.formaPago);
    if (filtro.ordenarPor) params = params.set('ordenarPor', filtro.ordenarPor);
    if (filtro.direccion) params = params.set('direccion', filtro.direccion);
    params = params.set('page', filtro.page ?? 0).set('size', filtro.size ?? 50);
    return this.http.get<Pagina<Reserva>>(`${this.comprasUrl}/buscar`, { params });
  }

  /**
   * Mismos filtros que `buscar` (sin orden, que siempre es por fecha ascendente), pero
   * devuelve los días distintos (fechaVisita) con al menos una compra, paginados por
   * cantidad de días en vez de por cantidad de compras. Lo usa la vista agrupada por día
   * para no partir un día a la mitad entre dos páginas: primero se pide la página de días
   * acá, y con el primer/último día de esa página se vuelve a llamar a `buscar` (con
   * fechaDesde/fechaHasta) para traer todas las compras de esos días completos. Los regalos
   * (fechaVisita null) quedan afuera de esta enumeración: tienen su propio bloque en
   * Boletería (filtro `sinFecha` de `buscar`), independiente de esta paginación por día.
   */
  buscarFechasDistintas(
    filtro: Omit<FiltroBusquedaCompras, 'fecha' | 'sinFecha' | 'ordenarPor' | 'direccion'>
  ): Observable<Pagina<string>> {
    let params = new HttpParams();
    if (filtro.texto) params = params.set('texto', filtro.texto);
    if (filtro.fechaDesde) params = params.set('fechaDesde', filtro.fechaDesde);
    if (filtro.fechaHasta) params = params.set('fechaHasta', filtro.fechaHasta);
    if (filtro.tipo) params = params.set('tipo', filtro.tipo);
    for (const estado of filtro.estados ?? []) params = params.append('estados', estado);
    if (filtro.formaPago) params = params.set('formaPago', filtro.formaPago);
    params = params.set('page', filtro.page ?? 0).set('size', filtro.size ?? 20);
    return this.http.get<Pagina<string>>(`${this.comprasUrl}/fechas-visita`, { params });
  }

  /** Una compra puntual, con sus líneas de detalle — lo usa el admin para precargar el
   * formulario de "Editar venta" con lo que esa compra tiene cargado hoy. */
  obtenerPorId(compraId: number): Observable<Reserva> {
    return this.http.get<Reserva>(`${this.comprasUrl}/${compraId}`);
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

  /** Deshace una validación reciente: vuelve a APROBADO o RESERVADO_EFECTIVO. Sólo funciona dentro de una ventana corta desde que se validó. */
  deshacerValidacion(compraId: number): Observable<Reserva> {
    return this.http.put<Reserva>(`${this.comprasUrl}/${compraId}/deshacer-validacion`, {});
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
  cotizar(
    formaPago: FormaPagoPos,
    entradas: LineaVentaPos[],
    descuento?: DescuentoPos,
    articulos?: LineaArticuloPos[]
  ): Observable<CotizacionResponse> {
    return this.http.post<CotizacionResponse>(`${this.comprasUrl}/cotizar`, { formaPago, entradas, articulos, ...descuento });
  }

  /** Venta presencial: cobra y habilita el ingreso en un solo paso (queda USADO). */
  registrarVentaPos(venta: VentaPosRequest): Observable<Reserva> {
    return this.http.post<Reserva>(`${this.internoUrl}/venta-pos`, venta);
  }

  /** Igual que registrarVentaPos, pero un ADMIN cargándola en la caja de OTRO usuario por id
   * (ej. desde el detalle de una caja abierta, cuando falta registrar una venta). */
  registrarVentaPosComoAdmin(cajaId: number, venta: VentaPosRequest): Observable<Reserva> {
    return this.http.post<Reserva>(`${this.internoUrl}/caja/${cajaId}/venta-pos`, venta);
  }

  /** Cancela una venta de puerta mal cargada (ADMIN, desde el detalle de una caja). La marca
   * CANCELADO: deja de contar para cupo diario, totales de caja y reportes. */
  cancelarVenta(compraId: number): Observable<Reserva> {
    return this.http.put<Reserva>(`${this.internoUrl}/${compraId}/cancelar-venta`, {});
  }

  /** Corrige las entradas y/o la forma de pago de una venta de puerta (ADMIN). No toca
   * artículos varios ni el descuento ya aplicado. */
  editarVenta(compraId: number, datos: EditarVentaRequest): Observable<Reserva> {
    return this.http.put<Reserva>(`${this.internoUrl}/${compraId}/editar-venta`, datos);
  }
}
