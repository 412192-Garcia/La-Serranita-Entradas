import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LucidePrinter } from '@lucide/angular';
import { TipoEntrada } from '../../models/tipo-entrada';
import { TipoEntradaService } from '../../services/tipo-entrada.service';
import { ImpresorasService } from '../../services/impresoras.service';
import { TipoVoucher, VoucherManualService } from '../../services/voucher-manual.service';
import { ConfirmacionService } from '../../shared/confirmacion/confirmacion.service';
import { ToastService } from '../../shared/toast/toast.service';
import { SelectorTicketera } from '../../shared/selector-ticketera/selector-ticketera';

const MAX_CANTIDAD = 99;

/** Acciones > Vouchers: un voucher suelto para la cantina, sin reserva (artículos o un monto). */
@Component({
  selector: 'app-vouchers-manuales',
  imports: [FormsModule, SelectorTicketera, LucidePrinter],
  templateUrl: './vouchers-manuales.html',
  styleUrl: './vouchers-manuales.css',
})
export class VouchersManuales implements OnInit {
  private voucherService = inject(VoucherManualService);
  private tipoEntradaService = inject(TipoEntradaService);
  private confirmacion = inject(ConfirmacionService);
  private toast = inject(ToastService);
  private destroyRef = inject(DestroyRef);
  readonly impresoras = inject(ImpresorasService);
  readonly maxCantidad = MAX_CANTIDAD;

  /** Los extras del catálogo, como sugerencias para el artículo. */
  extras = signal<TipoEntrada[]>([]);

  /** EXTRA = artículos; CANTINA = un monto. */
  tipo = signal<TipoVoucher>('EXTRA');
  descripcion = signal('');
  cantidad = signal<number | null>(1);
  monto = signal<number | null>(null);
  motivo = signal('');
  titular = signal('');
  emitiendo = signal(false);

  /** Lo que va a decir el voucher, para la confirmación. */
  valor = computed(() => {
    if (this.tipo() === 'CANTINA') return `$ ${Math.round(this.monto() ?? 0).toLocaleString('es-AR')}`;
    return `${this.cantidad()} x ${this.descripcion().trim()}`;
  });

  valido = computed(() => {
    if (!this.impresoras.lista()) return false;
    if (this.tipo() === 'CANTINA') return (this.monto() ?? 0) >= 1;
    const c = this.cantidad() ?? 0;
    return this.descripcion().trim() !== '' && Number.isInteger(c) && c >= 1 && c <= MAX_CANTIDAD;
  });

  ngOnInit(): void {
    this.impresoras.seguir(this.destroyRef, true);
    this.tipoEntradaService.getTiposEntrada().subscribe({
      next: (t) => this.extras.set(t.filter((x) => x.activo && x.tipo === 'EXTRA')),
      error: () => {},
    });
  }

  async emitir(): Promise<void> {
    if (!this.valido() || this.emitiendo()) return;
    const titular = this.titular().trim();
    const ok = await this.confirmacion.pedir({
      titulo: 'Imprimir voucher',
      mensaje: `Sale un voucher para la cantina de ${this.valor()}${titular ? `, a nombre de ${titular}` : ''}. Vale sólo por hoy.`,
      aceptar: 'Imprimir',
    });
    if (!ok) return;
    this.emitiendo.set(true);
    const monto = this.tipo() === 'CANTINA';
    this.voucherService
      .emitir({
        tipo: this.tipo(),
        descripcion: monto ? this.motivo().trim() || null : this.descripcion().trim(),
        cantidad: monto ? null : this.cantidad(),
        monto: monto ? this.monto() : null,
        titular: titular || null,
        impresora: this.impresoras.efectiva()?.nombre ?? null,
      })
      .subscribe({
        next: () => {
          this.emitiendo.set(false);
          this.toast.exito('Voucher enviado a la ticketera');
          this.descripcion.set('');
          this.cantidad.set(1);
          this.monto.set(null);
          this.motivo.set('');
          this.titular.set('');
        },
        error: (err) => {
          this.emitiendo.set(false);
          this.toast.error(this.mensaje(err, 'No se pudo imprimir el voucher'));
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
