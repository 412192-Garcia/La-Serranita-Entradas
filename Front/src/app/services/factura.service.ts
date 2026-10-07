import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { ControlFacturacion, DesfaseNumeracion, Factura, FacturaManualPedido, FacturacionPos, TotalesFacturacion, ImpresoraConectada, VentaFactura } from '../models/factura';

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

  /** Ticketeras con su agente conectado ahora mismo. */
  impresoras(): Observable<ImpresoraConectada[]> {
    return this.http.get<ImpresoraConectada[]>(`${environment.apiBase}/interno/impresion/impresoras`);
  }

  /** Manda (o vuelve a mandar) el ticket de una factura emitida a la ticketera. */
  imprimir(facturaId: number, impresora: string | null): Observable<unknown> {
    const params = impresora ? `?impresora=${encodeURIComponent(impresora)}` : '';
    return this.http.post(`${environment.apiBase}/interno/impresion/facturas/${facturaId}${params}`, null);
  }

  /** 404 si la compra no se facturó. */
  porCompra(compraId: number): Observable<Factura> {
    return this.http.get<Factura>(`${this.url}/compra/${compraId}`);
  }

  /** Ventas cobradas de un turno con su factura (un boletero, sólo las de su caja). */
  ventasDeCaja(cajaId: number): Observable<VentaFactura[]> {
    return this.http.get<VentaFactura[]>(`${this.url}/caja/${cajaId}/ventas`);
  }

  /** Factura una venta que se cobró sin factura (ej. efectivo con el mail vacío). */
  facturarCompra(compraId: number, pedido: FacturacionPos): Observable<Factura> {
    return this.http.post<Factura>(`${this.url}/compra/${compraId}`, pedido);
  }

  /** Manda una factura ya emitida al email que dé el cliente. */
  enviarPorMail(facturaId: number, email: string): Observable<void> {
    return this.http.post<void>(`${this.url}/${facturaId}/enviar-mail`, { email });
  }

  /** El PDF de la factura (pasa por el interceptor del token, por eso no es un link directo). */
  pdf(facturaId: number): Observable<Blob> {
    return this.http.get(`${this.url}/${facturaId}/pdf`, { responseType: 'blob' });
  }

  /** Factura B sin venta, con los ítems e importes que cargó el admin. */
  emitirManual(pedido: FacturaManualPedido): Observable<Factura> {
    return this.http.post<Factura>(`${this.url}/manual`, pedido);
  }

  /** Las últimas 50 facturas manuales. */
  manuales(): Observable<Factura[]> {
    return this.http.get<Factura[]>(`${this.url}/manuales`);
  }

  /** Anula una factura (manual o de una venta) con su nota de crédito. La venta queda igual, sin factura. */
  anularFactura(facturaId: number): Observable<void> {
    return this.http.post<void>(`${this.url}/${facturaId}/anular`, null);
  }

  /** Estado del servicio: facturación activa y si también se facturan las compras online. */
  estadoServicio(): Observable<{ habilitada: boolean; online: boolean }> {
    return this.http.get<{ habilitada: boolean; online: boolean }>(`${this.url}/estado-servicio`);
  }

  /** Factura una compra online paga que quedó sin factura. */
  facturarOnline(compraId: number): Observable<Factura> {
    return this.http.post<Factura>(`${this.url}/compra/${compraId}/online`, null);
  }

  /** Vuelve a intentar una factura que quedó en ERROR (ADMIN). */
  reintentar(facturaId: number): Observable<Factura> {
    return this.http.post<Factura>(`${this.url}/${facturaId}/reintentar`, null);
  }

  /** Facturas con problema, vencimiento del certificado y último control de numeración (ADMIN). */
  control(): Observable<ControlFacturacion> {
    return this.http.get<ControlFacturacion>(`${this.url}/control`);
  }

  /** Controla ahora la numeración contra ARCA (sólo en producción). */
  controlarNumeracion(): Observable<DesfaseNumeracion[]> {
    return this.http.post<DesfaseNumeracion[]>(`${this.url}/control/numeracion`, null);
  }

  /** Totales por punto de venta y tipo de comprobante (fechas "AAAA-MM-DD"). */
  totales(desde: string, hasta: string): Observable<TotalesFacturacion[]> {
    return this.http.get<TotalesFacturacion[]>(`${this.url}/totales?desde=${desde}&hasta=${hasta}`);
  }

  /** CSV de los comprobantes del período, para el contador. */
  exportar(desde: string, hasta: string): Observable<Blob> {
    return this.http.get(`${this.url}/exportar?desde=${desde}&hasta=${hasta}`, { responseType: 'blob' });
  }

  private leerHabilitadaGuardada(): boolean {
    try {
      return localStorage.getItem(CLAVE_HABILITADA) === 'true';
    } catch {
      return false;
    }
  }
}
