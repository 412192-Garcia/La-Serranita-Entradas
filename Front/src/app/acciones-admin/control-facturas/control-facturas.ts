import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  LucideCircleAlert,
  LucideCircleCheck,
  LucideDownload,
  LucideFileText,
  LucideRefreshCw,
  LucideShieldCheck,
} from '@lucide/angular';
import { ControlFacturacion, TotalesFacturacion, numeroComprobante } from '../../models/factura';
import { FacturaService } from '../../services/factura.service';
import { PesosPipe } from '../../shared/pesos.pipe';
import { Spinner } from '../../shared/spinner/spinner';
import { aFechaISO } from '../../shared/fecha.util';

const DIAS_AVISO_CERTIFICADO = 30;

/**
 * Acciones > Control de facturas: lo que hay que mirar de la facturación sin entrar a la base.
 * - Estado: vencimiento del certificado de ARCA, control nocturno de numeración y facturas con
 *   problema (los tres prenden el aviso de la campanita).
 * - Facturas con problema: en ERROR (se reintentan desde acá) o trabadas en reintentos.
 * - Listado para el contador: totales del período y el CSV con todos los comprobantes.
 */
@Component({
  selector: 'app-control-facturas',
  imports: [FormsModule, DatePipe, PesosPipe, Spinner, LucideCircleAlert, LucideCircleCheck, LucideDownload,
    LucideFileText, LucideRefreshCw, LucideShieldCheck],
  templateUrl: './control-facturas.html',
  styleUrl: './control-facturas.css',
})
export class ControlFacturas implements OnInit {
  private facturaService = inject(FacturaService);
  private destroyRef = inject(DestroyRef);

  readonly numeroComprobante = numeroComprobante;
  readonly diasAviso = DIAS_AVISO_CERTIFICADO;

  control = signal<ControlFacturacion | null>(null);
  cargando = signal(true);
  error = signal<string | null>(null);

  controlando = signal(false);
  reintentandoId = signal<number | null>(null);
  reintentandoTodas = signal(false);
  aviso = signal<{ texto: string; error: boolean } | null>(null);

  desde = signal(aFechaISO(new Date(new Date().getFullYear(), new Date().getMonth(), 1)));
  hasta = signal(aFechaISO(new Date()));
  totales = signal<TotalesFacturacion[] | null>(null);
  cargandoTotales = signal(false);
  descargando = signal(false);
  errorListado = signal<string | null>(null);

  readonly hayEnError = computed(() => (this.control()?.problemas ?? []).some((p) => p.factura.estado === 'ERROR'));

  /** Neto del período: facturas menos notas de crédito. */
  readonly netoPeriodo = computed(() => {
    const t = this.totales() ?? [];
    return t.reduce((suma, x) => suma + (x.tipoComprobante === 8 ? -x.total : x.total), 0);
  });

  ngOnInit(): void {
    this.cargar();
    this.verTotales();
  }

  cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.facturaService
      .control()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (c) => {
          this.control.set(c);
          this.cargando.set(false);
        },
        error: (err) => {
          this.cargando.set(false);
          this.error.set(this.mensaje(err, 'No se pudo consultar el estado de la facturación.'));
        },
      });
  }

  certificadoEnRiesgo(c: ControlFacturacion): boolean {
    return c.diasParaVencer !== null && c.diasParaVencer <= DIAS_AVISO_CERTIFICADO;
  }

  tipoTexto(tipo: number): string {
    return tipo === 8 ? 'Nota de Crédito B' : 'Factura B';
  }

  controlarAhora(): void {
    this.controlando.set(true);
    this.aviso.set(null);
    this.facturaService.controlarNumeracion().subscribe({
      next: (desfases) => {
        this.controlando.set(false);
        this.aviso.set(desfases.length
          ? { texto: 'La numeración no coincide con ARCA: revisá los puntos de venta marcados.', error: true }
          : { texto: 'Numeración controlada: coincide con ARCA.', error: false });
        this.cargar();
      },
      error: (err) => {
        this.controlando.set(false);
        this.aviso.set({ texto: this.mensaje(err, 'No se pudo controlar la numeración.'), error: true });
      },
    });
  }

  reintentar(facturaId: number): void {
    this.reintentandoId.set(facturaId);
    this.aviso.set(null);
    this.facturaService.reintentar(facturaId).subscribe({
      next: () => {
        this.reintentandoId.set(null);
        this.aviso.set({ texto: 'Se vuelve a intentar: en unos segundos se actualiza.', error: false });
        setTimeout(() => this.cargar(), 3000);
      },
      error: (err) => {
        this.reintentandoId.set(null);
        this.aviso.set({ texto: this.mensaje(err, 'No se pudo reintentar.'), error: true });
      },
    });
  }

  /** Reintenta de a una todas las que están en ERROR (las trabadas en PENDIENTE se reintentan solas). */
  reintentarTodas(): void {
    const ids = (this.control()?.problemas ?? []).filter((p) => p.factura.estado === 'ERROR').map((p) => p.factura.id);
    if (!ids.length) return;
    this.reintentandoTodas.set(true);
    this.aviso.set(null);
    let pendientes = ids.length;
    let fallidas = 0;
    for (const id of ids) {
      this.facturaService.reintentar(id).subscribe({
        next: () => this.terminarUna(--pendientes, fallidas, ids.length),
        error: () => this.terminarUna(--pendientes, ++fallidas, ids.length),
      });
    }
  }

  private terminarUna(pendientes: number, fallidas: number, total: number): void {
    if (pendientes > 0) return;
    this.reintentandoTodas.set(false);
    this.aviso.set(fallidas
      ? { texto: `Se reintentaron ${total - fallidas} de ${total}; ${fallidas} no se pudieron reintentar.`, error: true }
      : { texto: `Se reintentaron ${total}: en unos segundos se actualiza.`, error: false });
    setTimeout(() => this.cargar(), 3000);
  }

  periodoValido(): boolean {
    return !!this.desde() && !!this.hasta() && this.desde() <= this.hasta();
  }

  verTotales(): void {
    if (!this.periodoValido()) return;
    this.cargandoTotales.set(true);
    this.errorListado.set(null);
    this.facturaService.totales(this.desde(), this.hasta()).subscribe({
      next: (t) => {
        this.totales.set(t);
        this.cargandoTotales.set(false);
      },
      error: (err) => {
        this.cargandoTotales.set(false);
        this.errorListado.set(this.mensaje(err, 'No se pudieron calcular los totales.'));
      },
    });
  }

  descargarCsv(): void {
    if (!this.periodoValido()) return;
    this.descargando.set(true);
    this.errorListado.set(null);
    this.facturaService.exportar(this.desde(), this.hasta()).subscribe({
      next: (csv) => {
        this.descargando.set(false);
        const url = URL.createObjectURL(csv);
        const enlace = document.createElement('a');
        enlace.href = url;
        enlace.download = `facturas-${this.desde()}-al-${this.hasta()}.csv`;
        enlace.click();
        setTimeout(() => URL.revokeObjectURL(url), 10_000);
      },
      error: (err) => {
        this.descargando.set(false);
        this.errorListado.set(this.mensaje(err, 'No se pudo generar el archivo.'));
      },
    });
  }

  private mensaje(err: unknown, porDefecto: string): string {
    const e = err as { error?: unknown; status?: number };
    if (typeof e?.error === 'string' && e.error.trim()) return e.error;
    if (e?.status === 0) return 'No se pudo contactar con el servidor.';
    return porDefecto;
  }
}
