import { Component, DestroyRef, OnInit, computed, inject, input, signal } from '@angular/core';
import { ToastService } from '../../shared/toast/toast.service';
import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, Subscription, timer } from 'rxjs';
import {
  LucideBan,
  LucideBanknote,
  LucidePencil,
  LucideCircleAlert,
  LucideCreditCard,
  LucideEye,
  LucideFileText,
  LucideLoaderCircle,
  LucideMail,
  LucidePrinter,
  LucideQrCode,
  LucideRefreshCw,
  LucideSearch,
  LucideTag,
  LucideUser,
} from '@lucide/angular';
import { PesosPipe } from '../pesos.pipe';
import { Factura, VentaFactura, numeroComprobante } from '../../models/factura';
import { etiquetaFormaPago } from '../../models/forma-pago';
import { FacturaService } from '../../services/factura.service';
import { ImpresorasService } from '../../services/impresoras.service';
import { ConectividadService } from '../../services/conectividad.service';
import { descripcionTarjeta } from '../tarjeta-point.util';
import { FichaCompraModal } from '../../reservas/ficha-compra-modal/ficha-compra-modal';
import { CorregirVenta } from '../corregir-venta/corregir-venta';
import { BoleteriaService } from '../../services/boleteria.service';

/** Mientras haya una factura emitiéndose o un ticket imprimiéndose, la lista se refresca sola
 * cada 2,5 s, hasta ~1 minuto (después ya quedó en la cola de reintentos del servidor). */
const INTERVALO_REFRESCO_MS = 2500;
const MAX_REFRESCOS = 24;

/** Lo que se está haciendo en una fila: cargar el email para mandarla, o elegir cómo facturar. */
type Accion = { compraId: number; tipo: 'mail' | 'facturar' };

type Filtro = 'todas' | 'sin-factura';

/** Cómo se muestra el estado de la factura de una venta. */
type EstadoVisible = 'ok' | 'curso' | 'error' | 'sin' | 'no-aplica';

/** El orden de los totales por forma de pago del resumen. */
const ORDEN_FORMAS = ['EFECTIVO_BOLETERIA', 'TARJETA', 'MERCADO_PAGO_QR', 'SIN_COBRO'];

/**
 * "Ventas y facturas" de un turno de caja: el detalle de cada venta y, con la facturación prendida,
 * recuperar la factura de una venta anterior (el ticket no salió, el cliente vuelve a pedirla o la
 * quiere por mail) o facturar una venta que se cobró sin factura.
 *
 * Es un panel, no un modal: en el POS reemplaza al catálogo (como Anticipadas).
 */
@Component({
  selector: 'app-ventas-turno',
  imports: [PesosPipe, DatePipe, NgTemplateOutlet, FormsModule, FichaCompraModal, CorregirVenta, LucidePencil, LucideBan, LucideFileText, LucidePrinter,
    LucideMail, LucideCircleAlert, LucideLoaderCircle, LucideRefreshCw, LucideBanknote, LucideCreditCard, LucideQrCode,
    LucideSearch, LucideEye, LucideTag, LucideUser],
  templateUrl: './ventas-turno.html',
  styleUrl: './ventas-turno.css',
})
export class VentasTurno implements OnInit {
  private facturaService = inject(FacturaService);
  private toast = inject(ToastService);
  private destroyRef = inject(DestroyRef);
  readonly impresoras = inject(ImpresorasService);
  readonly enLinea = inject(ConectividadService).enLinea;
  /** Apagada: se ve el historial igual, sin las opciones de factura. */
  readonly facturacion = this.facturaService.habilitada;

  cajaId = input.required<number>();
  /** En el POS el título ya está en la cabecera. */
  conTitulo = input(true);


  readonly numeroComprobante = numeroComprobante;
  readonly etiquetaFormaPago = etiquetaFormaPago;

  ventas = signal<VentaFactura[]>([]);
  busqueda = signal('');
  filtro = signal<Filtro>('todas');
  /** Venta abierta en "Ver detalle". */
  fichaDe = signal<number | null>(null);

  // ---------- Correcciones del boletero ----------
  private boleteriaService = inject(BoleteriaService);
  /** Se refresca cada 30 s para que los botones de corregir desaparezcan solos al vencer el plazo. */
  private ahora = signal(Date.now());
  corrigiendo = signal<VentaFactura | null>(null);
  cancelandoVenta = signal<VentaFactura | null>(null);
  motivoCancelacion = signal('');
  errorCancelacion = signal<string | null>(null);
  cancelando = signal(false);

  /** Corrigiendo o cancelando esta venta: sus botones se ocultan. */
  enEdicion(v: VentaFactura): boolean {
    return this.corrigiendo()?.compraId === v.compraId || this.cancelandoVenta()?.compraId === v.compraId;
  }

  abrirCorreccion(v: VentaFactura): void {
    this.cancelandoVenta.set(null);
    this.accion.set(null);
    this.corrigiendo.set(v);
  }

  puedeCorregir(v: VentaFactura): boolean {
    return v.corregibleHasta !== null && new Date(v.corregibleHasta).getTime() > this.ahora();
  }

  alCorregir(): void {
    this.corrigiendo.set(null);
    this.toast.exito('Venta corregida.');
    this.recargarSilencioso();
  }

  abrirCancelacion(v: VentaFactura): void {
    this.motivoCancelacion.set('');
    this.errorCancelacion.set(null);
    this.corrigiendo.set(null);
    this.accion.set(null);
    this.cancelandoVenta.set(v);
  }

  confirmarCancelacion(): void {
    const v = this.cancelandoVenta();
    const motivo = this.motivoCancelacion().trim();
    if (!v) return;
    if (!motivo) {
      this.errorCancelacion.set('Escribí por qué la cancelás.');
      return;
    }
    this.cancelando.set(true);
    this.boleteriaService.cancelarVenta(v.compraId, motivo).subscribe({
      next: () => {
        this.cancelando.set(false);
        this.cancelandoVenta.set(null);
        this.toast.exito(`Venta #${v.codigoReserva} cancelada.`);
            this.recargarSilencioso();
      },
      error: (err) => {
        this.cancelando.set(false);
        this.errorCancelacion.set(this.mensaje(err, 'No se pudo cancelar la venta.'));
      },
    });
  }
  cargando = signal(true);
  errorCarga = signal<string | null>(null);

  accion = signal<Accion | null>(null);
  email = signal('');
  /** compraId de la fila con un pedido en curso (reimprimir, mandar, facturar...). */
  enviando = signal<number | null>(null);
  /** Resultado del último pedido, debajo de su fila. */

  private refresco: Subscription | null = null;
  private refrescosHechos = 0;

  readonly descripcionTarjeta = descripcionTarjeta;

  /** Totales del turno, por forma de pago (una venta con dos pagos suma en las dos). */
  resumen = computed(() => {
    const ventas = this.ventas();
    const porForma = new Map<string, number>();
    for (const v of ventas) {
      porForma.set(v.formaPago, (porForma.get(v.formaPago) ?? 0) + this.montoPrincipal(v));
      if (v.formaPagoSecundaria && v.montoFormaPagoSecundaria) {
        porForma.set(v.formaPagoSecundaria, (porForma.get(v.formaPagoSecundaria) ?? 0) + v.montoFormaPagoSecundaria);
      }
    }
    const orden = (f: string) => { const i = ORDEN_FORMAS.indexOf(f); return i === -1 ? 99 : i; };
    return {
      cantidad: ventas.length,
      total: ventas.reduce((a, v) => a + v.montoTotal, 0),
      sinFactura: ventas.filter((v) => this.facturable(v)).length,
      formas: [...porForma.entries()]
        .filter(([, monto]) => monto > 0)
        .sort(([a], [b]) => orden(a) - orden(b))
        .map(([forma, monto]) => ({ forma, monto })),
    };
  });

  /** Busca por código, titular o lo que se vendió. */
  visibles = computed(() => {
    const texto = this.busqueda().trim().toLowerCase();
    const soloSinFactura = this.facturacion() && this.filtro() === 'sin-factura';
    return this.ventas().filter((v) => {
      if (soloSinFactura && !this.facturable(v)) return false;
      if (!texto) return true;
      return v.codigoReserva.toLowerCase().includes(texto)
        || (v.titular ?? '').toLowerCase().includes(texto)
        || v.lineas.some((l) => l.nombre.toLowerCase().includes(texto));
    });
  });

  /** Lo cobrado con la forma principal (con dos pagos, el total menos la segunda). */
  montoPrincipal(v: VentaFactura): number {
    return v.formaPagoSecundaria && v.montoFormaPagoSecundaria ? v.montoTotal - v.montoFormaPagoSecundaria : v.montoTotal;
  }

  estadoVisible(v: VentaFactura): EstadoVisible {
    const f = v.factura;
    if (f === null) return v.montoTotal > 0 ? 'sin' : 'no-aplica';
    if (this.vigente(f)) return f.impresionEstado === 'ERROR' ? 'error' : 'ok';
    if (f.estado === 'PENDIENTE') return 'curso';
    if (f.estado === 'ERROR') return 'error';
    return 'sin';
  }

  ngOnInit(): void {
    this.cargar();
    timer(30_000, 30_000).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.ahora.set(Date.now()));
    // Desde "Cajas" (admin) nadie está siguiendo las ticketeras: se pregunta una vez para saber
    // si "Imprimir" está disponible.
    if (this.facturacion()) this.impresoras.actualizar();
  }

  cargar(): void {
    this.cargando.set(true);
    this.errorCarga.set(null);
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
        this.toast.error(this.mensaje(err, 'No se pudo abrir el PDF.'));
      },
    });
  }

  private pedir(v: VentaFactura, pedido: Observable<unknown>, ok: string): void {
    this.enviando.set(v.compraId);
    pedido.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.enviando.set(null);
        this.accion.set(null);
        this.toast.exito(ok);
        this.refrescosHechos = 0;
        this.recargarSilencioso();
      },
      error: (err) => {
        this.enviando.set(null);
        this.toast.error(this.mensaje(err, 'No se pudo completar.'));
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
