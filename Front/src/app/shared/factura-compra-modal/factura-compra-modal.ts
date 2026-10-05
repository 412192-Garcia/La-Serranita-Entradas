import { Component, DestroyRef, OnInit, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, Subscription, timer } from 'rxjs';
import {
  LucideCircleAlert,
  LucideFileText,
  LucideLoaderCircle,
  LucideMail,
  LucideRefreshCw,
  LucideX,
} from '@lucide/angular';
import { Modal } from '../modal/modal';
import { PesosPipe } from '../pesos.pipe';
import { Factura, numeroComprobante } from '../../models/factura';
import { FacturaService } from '../../services/factura.service';
import { Reserva } from '../../services/boleteria.service';

const INTERVALO_REFRESCO_MS = 2500;
const MAX_REFRESCOS = 24;

/**
 * La factura de una compra, desde el menú de cada fila de Control de accesos (sólo admin): sirve
 * igual para las anticipadas pagadas online (punto de venta online, se facturan solas) que para
 * las vendidas en puerta (POS).
 *
 * Ver PDF, mandarla por mail, reintentar si falló, anularla (nota de crédito; la venta queda igual,
 * sin factura) y facturar una compra cobrada que no tiene factura (o cuya factura se anuló).
 */
@Component({
  selector: 'app-factura-compra-modal',
  imports: [Modal, FormsModule, PesosPipe, LucideX, LucideFileText, LucideMail, LucideRefreshCw, LucideCircleAlert,
    LucideLoaderCircle],
  templateUrl: './factura-compra-modal.html',
  styleUrl: './factura-compra-modal.css',
})
export class FacturaCompraModal implements OnInit {
  private facturaService = inject(FacturaService);
  private destroyRef = inject(DestroyRef);

  reserva = input.required<Reserva>();
  cerrar = output<void>();

  readonly numeroComprobante = numeroComprobante;

  factura = signal<Factura | null>(null);
  cargando = signal(true);
  error = signal<string | null>(null);

  accion = signal<'mail' | 'anular' | 'facturar' | null>(null);
  email = signal('');
  enviando = signal(false);
  aviso = signal<{ texto: string; error: boolean } | null>(null);

  private refresco: Subscription | null = null;
  private refrescosHechos = 0;

  ngOnInit(): void {
    this.email.set(this.reserva().contactEmail ?? '');
    this.cargar();
  }

  cargar(conSpinner = true): void {
    if (conSpinner) this.cargando.set(true);
    this.error.set(null);
    this.facturaService
      .porCompra(this.reserva().id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (f) => {
          this.factura.set(f);
          this.cargando.set(false);
          this.programarRefresco();
        },
        error: (err) => {
          this.cargando.set(false);
          // 404 = la compra no tiene factura: no es un error.
          if ((err as { status?: number })?.status === 404) {
            this.factura.set(null);
          } else if (conSpinner) {
            this.error.set(this.mensaje(err, 'No se pudo consultar la factura.'));
          }
          this.programarRefresco();
        },
      });
  }

  /** Pagada online con Mercado Pago: se factura sola, por el punto de venta online y por mail. */
  esOnline(): boolean {
    return this.reserva().formaPago === 'MERCADO_PAGO';
  }

  vigente(f: Factura | null): boolean {
    return f !== null && f.estado === 'EMITIDA' && !f.anulacionPedida;
  }

  anulada(f: Factura | null): boolean {
    return f !== null && (f.estado === 'ANULADA' || f.anulacionPedida);
  }

  /** Cobrada y sin factura vigente: se puede facturar. Las reservas sin cobrar, no. */
  facturable(): boolean {
    const r = this.reserva();
    const f = this.factura();
    if (r.montoTotal <= 0 || (f !== null && !this.anulada(f))) return false;
    if (this.esOnline()) return r.estado === 'APROBADO' || r.estado === 'USADO';
    return r.estado === 'VENDIDO_EN_PUERTA' || r.estado === 'USADO';
  }

  private enCurso(f: Factura | null): boolean {
    if (f === null) return false;
    return f.estado === 'PENDIENTE'
      || (f.estado === 'EMITIDA' && f.destino === 'MAIL' && !f.mailEnviadoEn && !f.anulacionPedida);
  }

  private programarRefresco(): void {
    this.refresco?.unsubscribe();
    if (this.refrescosHechos >= MAX_REFRESCOS || !this.enCurso(this.factura())) return;
    this.refrescosHechos++;
    this.refresco = timer(INTERVALO_REFRESCO_MS)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.cargar(false));
  }

  abrir(accion: 'mail' | 'anular' | 'facturar'): void {
    this.accion.set(this.accion() === accion ? null : accion);
    this.aviso.set(null);
  }

  mandarPorMail(): void {
    const f = this.factura();
    const email = this.email().trim();
    if (!f || !this.emailValido(email)) return;
    this.pedir(this.facturaService.enviarPorMail(f.id, email), `Factura enviada a ${email}.`);
  }

  reintentar(): void {
    const f = this.factura();
    if (f) this.pedir(this.facturaService.reintentar(f.id), 'Se vuelve a intentar: en unos segundos se actualiza.');
  }

  anular(): void {
    const f = this.factura();
    if (f) this.pedir(this.facturaService.anularFactura(f.id), 'Anulada: se emite la nota de crédito. La venta queda igual, sin factura.');
  }

  facturar(): void {
    const id = this.reserva().id;
    if (this.esOnline()) {
      this.pedir(this.facturaService.facturarOnline(id), 'Factura pedida: en unos segundos se emite y se manda por mail.');
      return;
    }
    const email = this.email().trim();
    if (!this.emailValido(email)) return;
    this.pedir(this.facturaService.facturarCompra(id, { destino: 'MAIL', email }),
      `Factura pedida: se manda a ${email} en unos segundos.`);
  }

  verPdf(): void {
    const f = this.factura();
    if (!f) return;
    // La pestaña se abre ya, con el clic: si se abre cuando llega el PDF, el navegador la bloquea.
    const pestana = window.open('', '_blank');
    this.facturaService.pdf(f.id).subscribe({
      next: (pdf) => {
        const url = URL.createObjectURL(pdf);
        if (pestana) pestana.location.href = url;
        else window.location.href = url;
        setTimeout(() => URL.revokeObjectURL(url), 60_000);
      },
      error: (err) => {
        pestana?.close();
        this.aviso.set({ texto: this.mensaje(err, 'No se pudo abrir el PDF.'), error: true });
      },
    });
  }

  private pedir(pedido: Observable<unknown>, ok: string): void {
    this.enviando.set(true);
    this.aviso.set(null);
    pedido.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.enviando.set(false);
        this.accion.set(null);
        this.aviso.set({ texto: ok, error: false });
        this.refrescosHechos = 0;
        this.cargar(false);
      },
      error: (err) => {
        this.enviando.set(false);
        this.aviso.set({ texto: this.mensaje(err, 'No se pudo completar.'), error: true });
      },
    });
  }

  emailValido(email: string): boolean {
    return /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email.trim());
  }

  private mensaje(err: unknown, porDefecto: string): string {
    const e = err as { error?: unknown; status?: number };
    if (typeof e?.error === 'string' && e.error.trim()) return e.error;
    if (e?.status === 0) return 'No se pudo contactar con el servidor.';
    return porDefecto;
  }
}
