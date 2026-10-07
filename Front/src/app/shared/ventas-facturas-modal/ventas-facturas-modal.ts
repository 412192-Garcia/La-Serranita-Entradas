import { Component, DestroyRef, OnInit, inject, input, output, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, Subscription, timer } from 'rxjs';
import {
  LucideCircleAlert,
  LucideFileText,
  LucideLoaderCircle,
  LucideMail,
  LucidePrinter,
  LucideRefreshCw,
  LucideX,
} from '@lucide/angular';
import { Modal } from '../modal/modal';
import { PesosPipe } from '../pesos.pipe';
import { Factura, VentaFactura, numeroComprobante } from '../../models/factura';
import { etiquetaFormaPago } from '../../models/forma-pago';
import { FacturaService } from '../../services/factura.service';
import { ImpresorasService } from '../../services/impresoras.service';
import { ConectividadService } from '../../services/conectividad.service';

/** Mientras haya una factura emitiéndose o un ticket imprimiéndose, la lista se refresca sola
 * cada 2,5 s, hasta ~1 minuto (después ya quedó en la cola de reintentos del servidor). */
const INTERVALO_REFRESCO_MS = 2500;
const MAX_REFRESCOS = 24;

/** Lo que se está haciendo en una fila: cargar el email para mandarla, o elegir cómo facturar. */
type Accion = { compraId: number; tipo: 'mail' | 'facturar' };

/**
 * "Ventas y facturas" de un turno de caja: para recuperar la factura de una venta anterior (el
 * ticket no salió, el cliente vuelve a pedirla o la quiere por mail) y para facturar una venta que
 * se cobró sin factura (efectivo con el mail vacío, que es lo que queda por defecto).
 *
 * Lo abre el boletero desde el menú "Caja" del POS (sólo su caja) y el admin desde el detalle de
 * cada caja en "Cajas".
 */
@Component({
  selector: 'app-ventas-facturas-modal',
  imports: [Modal, PesosPipe, DatePipe, FormsModule, LucideX, LucideFileText, LucidePrinter, LucideMail,
    LucideCircleAlert, LucideLoaderCircle, LucideRefreshCw],
  templateUrl: './ventas-facturas-modal.html',
  styleUrl: './ventas-facturas-modal.css',
})
export class VentasFacturasModal implements OnInit {
  private facturaService = inject(FacturaService);
  private destroyRef = inject(DestroyRef);
  readonly impresoras = inject(ImpresorasService);
  readonly enLinea = inject(ConectividadService).enLinea;

  cajaId = input.required<number>();
  cerrar = output<void>();

  readonly numeroComprobante = numeroComprobante;
  readonly etiquetaFormaPago = etiquetaFormaPago;

  ventas = signal<VentaFactura[]>([]);
  cargando = signal(true);
  errorCarga = signal<string | null>(null);

  accion = signal<Accion | null>(null);
  email = signal('');
  /** compraId de la fila con un pedido en curso (reimprimir, mandar, facturar...). */
  enviando = signal<number | null>(null);
  /** Resultado del último pedido, debajo de su fila. */
  aviso = signal<{ compraId: number; texto: string; error: boolean } | null>(null);

  private refresco: Subscription | null = null;
  private refrescosHechos = 0;

  ngOnInit(): void {
    this.cargar();
    // Desde "Cajas" (admin) nadie está siguiendo las ticketeras: se pregunta una vez para saber
    // si "Imprimir" está disponible.
    this.impresoras.actualizar();
  }

  cargar(): void {
    this.cargando.set(true);
    this.errorCarga.set(null);
    this.aviso.set(null);
    this.refrescosHechos = 0;
    this.facturaService
      .ventasDeCaja(this.cajaId())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (ventas) => {
          this.ventas.set(ventas);
          this.cargando.set(false);
          this.programarRefresco();
        },
        error: (err) => {
          this.cargando.set(false);
          this.errorCarga.set(this.mensaje(err, 'No se pudieron cargar las ventas.'));
        },
      });
  }

  /** Una caja puede quedar abierta de un día para otro: a las ventas de otro día se les muestra la fecha. */
  esDeHoy(fecha: string | null): boolean {
    return fecha !== null && new Date(fecha).toDateString() === new Date().toDateString();
  }

  /** Factura que se puede reimprimir o mandar: emitida y sin anulación de por medio. */
  vigente(f: Factura | null): boolean {
    return f !== null && f.estado === 'EMITIDA' && !f.anulacionPedida;
  }

  /** Se puede facturar: sin factura (o con una anulada) y por un monto mayor a cero. */
  facturable(v: VentaFactura): boolean {
    return v.montoTotal > 0 && (v.factura === null || v.factura.estado === 'ANULADA' || v.factura.anulacionPedida);
  }

  abrir(v: VentaFactura, tipo: Accion['tipo']): void {
    const actual = this.accion();
    if (actual?.compraId === v.compraId && actual.tipo === tipo) {
      this.accion.set(null);
      return;
    }
    this.accion.set({ compraId: v.compraId, tipo });
    this.email.set(v.factura?.email ?? '');
    this.aviso.set(null);
  }

  abierta(v: VentaFactura, tipo: Accion['tipo']): boolean {
    const a = this.accion();
    return a !== null && a.compraId === v.compraId && a.tipo === tipo;
  }

  emailValido(): boolean {
    return /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(this.email().trim());
  }

  reimprimir(v: VentaFactura): void {
    if (!this.vigente(v.factura)) return;
    this.pedir(v, this.facturaService.imprimir(v.factura!.id, this.impresoras.efectiva()?.nombre ?? null),
      'Se mandó a la ticketera.');
  }

  mandarPorMail(v: VentaFactura): void {
    if (!this.vigente(v.factura) || !this.emailValido()) return;
    const email = this.email().trim();
    this.pedir(v, this.facturaService.enviarPorMail(v.factura!.id, email), `Factura enviada a ${email}.`);
  }

  facturar(v: VentaFactura, destino: 'IMPRIMIR' | 'MAIL'): void {
    if (destino === 'MAIL' && !this.emailValido()) return;
    const pedido = destino === 'IMPRIMIR'
      ? { destino, email: null, impresora: this.impresoras.efectiva()?.nombre ?? null }
      : { destino, email: this.email().trim() };
    this.pedir(v, this.facturaService.facturarCompra(v.compraId, pedido),
      destino === 'IMPRIMIR' ? 'Factura pedida: sale por la ticketera en unos segundos.'
        : `Factura pedida: se envía a ${pedido.email} en unos segundos.`);
  }

  /** Abre el PDF en otra pestaña. La pestaña se abre ya, con el toque: si se abre recién cuando
   * llega el PDF, el navegador de la tablet la toma como ventana emergente y la bloquea. */
  verPdf(v: VentaFactura): void {
    if (!this.vigente(v.factura)) return;
    const pestana = window.open('', '_blank');
    this.facturaService.pdf(v.factura!.id).subscribe({
      next: (pdf) => {
        const url = URL.createObjectURL(pdf);
        if (pestana) pestana.location.href = url;
        else window.location.href = url;
        setTimeout(() => URL.revokeObjectURL(url), 60_000);
      },
      error: (err) => {
        pestana?.close();
        this.aviso.set({ compraId: v.compraId, texto: this.mensaje(err, 'No se pudo abrir el PDF.'), error: true });
      },
    });
  }

  private pedir(v: VentaFactura, pedido: Observable<unknown>, ok: string): void {
    this.enviando.set(v.compraId);
    this.aviso.set(null);
    pedido.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.enviando.set(null);
        this.accion.set(null);
        this.aviso.set({ compraId: v.compraId, texto: ok, error: false });
        this.refrescosHechos = 0;
        this.recargarSilencioso();
      },
      error: (err) => {
        this.enviando.set(null);
        this.aviso.set({ compraId: v.compraId, texto: this.mensaje(err, 'No se pudo completar.'), error: true });
      },
    });
  }

  /** Refresca los estados sin el spinner (que borraría el aviso de la fila). */
  private recargarSilencioso(): void {
    this.facturaService
      .ventasDeCaja(this.cajaId())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (ventas) => {
          this.ventas.set(ventas);
          this.programarRefresco();
        },
        // Un corte breve no tiene que dejar filas en "Emitiendo…" para siempre: se reintenta,
        // dentro del mismo tope de refrescos.
        error: () => this.programarRefresco(),
      });
  }

  private enCurso(f: Factura | null): boolean {
    if (f === null) return false;
    return f.estado === 'PENDIENTE' || f.impresionEstado === 'PENDIENTE' || f.impresionEstado === 'ENVIADO';
  }

  private programarRefresco(): void {
    this.refresco?.unsubscribe();
    if (this.refrescosHechos >= MAX_REFRESCOS || !this.ventas().some((v) => this.enCurso(v.factura))) return;
    this.refrescosHechos++;
    this.refresco = timer(INTERVALO_REFRESCO_MS)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.recargarSilencioso());
  }

  private mensaje(err: unknown, porDefecto: string): string {
    const e = err as { error?: unknown; status?: number };
    if (typeof e?.error === 'string' && e.error.trim()) return e.error;
    if (e?.status === 0) return 'No se pudo contactar con el servidor.';
    return porDefecto;
  }
}
