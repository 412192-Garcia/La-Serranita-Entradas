import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CajaService, Caja, CierrePosnetInput, FormaPagoPosnet } from '../../Services/caja.service';
import { MoneyInputDirective } from '../../shared/money-input/money-input.directive';

/** Billetes de curso legal actual en Argentina (sin monedas). */
const DENOMINACIONES = [100, 200, 500, 1000, 2000, 10000, 20000];

/** Una fila de cierre de posnet dentro de su columna (Tarjeta o QR ya está implícito en cuál signal la contiene). */
interface FilaMontoNota {
  monto: number | null;
  nota: string;
}

@Component({
  selector: 'app-cierre-caja-modal',
  imports: [CurrencyPipe, FormsModule, MoneyInputDirective],
  templateUrl: './cierre-caja-modal.html',
  styleUrl: './cierre-caja-modal.css',
})
export class CierreCajaModal implements OnInit {
  private cajaService = inject(CajaService);

  /** Si viene seteada, el modal arranca precargado con sus datos y "Confirmar" corrige esa caja ya cerrada en vez de cerrar la abierta. */
  cajaParaCorregir = input<Caja | null>(null);
  /** La caja abierta que se está por cerrar (null cuando se está corrigiendo un cierre ya hecho). Sólo se usa para saber si hubo ventas en dólares. */
  cajaAbierta = input<Caja | null>(null);

  cajaCerrada = output<Caja>();
  cerrar = output<void>();

  /** Sea que se esté cerrando o corrigiendo, de cuál de las dos cajas sacamos el dato. */
  huboVentaDolares = computed(() => this.cajaParaCorregir()?.huboVentaDolares ?? this.cajaAbierta()?.huboVentaDolares ?? false);

  readonly denominaciones = DENOMINACIONES;
  /** Cuántos billetes de cada denominación contó el boletero al cerrar. */
  conteoEfectivo = signal<Record<number, number>>({});
  /** Total en billetes chicos (50, 20, etc.), cargado de una vez en vez de billete por billete. */
  cambioContado = signal<number | null>(null);
  /** Arrancan con una fila vacía cada una: lo normal es tener al menos un cierre de cada posnet. */
  cierresTarjeta = signal<FilaMontoNota[]>([{ monto: null, nota: '' }]);
  cierresQr = signal<FilaMontoNota[]>([{ monto: null, nota: '' }]);
  entradasFisicasFinal = signal<number | null>(null);
  /** Dólares que el boletero contó al cerrar. Sólo se pide si huboVentaDolares(). */
  dolaresContado = signal<number | null>(null);
  cerrandoCaja = signal(false);
  errorCierre = signal<string | null>(null);

  /** Total contado en efectivo, calculado en vivo a partir del conteo por denominación más el cambio. */
  montoContadoCalculado = computed(() =>
    this.denominaciones.reduce((acc, d) => acc + d * (this.conteoEfectivo()[d] ?? 0), 0) + (this.cambioContado() ?? 0)
  );

  ngOnInit(): void {
    const caja = this.cajaParaCorregir();
    if (!caja) return;

    const conteo: Record<number, number> = {};
    for (const c of caja.conteoEfectivo) conteo[c.denominacion] = c.cantidad;
    this.conteoEfectivo.set(conteo);
    this.cambioContado.set(caja.cambioContado);

    const tarjeta = caja.cierresPosnet.filter((c) => c.formaPago === 'TARJETA').map((c) => ({ monto: c.monto, nota: c.nota ?? '' }));
    const qr = caja.cierresPosnet.filter((c) => c.formaPago === 'MERCADO_PAGO_QR').map((c) => ({ monto: c.monto, nota: c.nota ?? '' }));
    this.cierresTarjeta.set(tarjeta.length ? tarjeta : [{ monto: null, nota: '' }]);
    this.cierresQr.set(qr.length ? qr : [{ monto: null, nota: '' }]);

    this.entradasFisicasFinal.set(caja.entradasFisicasFinal);
    this.dolaresContado.set(caja.dolaresContado);
  }

  setConteoEfectivo(denominacion: number, cantidad: number): void {
    const valor = Math.max(0, Math.floor(cantidad) || 0);
    this.conteoEfectivo.update((c) => ({ ...c, [denominacion]: valor }));
  }

  private filasCierre(tipo: FormaPagoPosnet) {
    return tipo === 'TARJETA' ? this.cierresTarjeta : this.cierresQr;
  }

  agregarCierre(tipo: FormaPagoPosnet): void {
    this.filasCierre(tipo).update((c) => [...c, { monto: null, nota: '' }]);
  }

  quitarCierre(tipo: FormaPagoPosnet, index: number): void {
    this.filasCierre(tipo).update((c) => c.filter((_, i) => i !== index));
  }

  actualizarCierre(tipo: FormaPagoPosnet, index: number, cambios: Partial<FilaMontoNota>): void {
    this.filasCierre(tipo).update((c) => c.map((fila, i) => (i === index ? { ...fila, ...cambios } : fila)));
  }

  confirmarCierre(): void {
    const entradasFisicasFinal = this.entradasFisicasFinal();
    if (entradasFisicasFinal === null || entradasFisicasFinal < 0) {
      this.errorCierre.set('Indicá con cuántas entradas termina el turno.');
      return;
    }

    const dolaresContado = this.dolaresContado();
    if (this.huboVentaDolares() && (dolaresContado === null || dolaresContado < 0)) {
      this.errorCierre.set('Esta caja tuvo ventas en dólares: indicá cuántos dólares contaste.');
      return;
    }

    const conteo = this.denominaciones
      .map((denominacion) => ({ denominacion, cantidad: this.conteoEfectivo()[denominacion] ?? 0 }))
      .filter((c) => c.cantidad > 0);

    // Las filas sin monto cargado se ignoran (es normal no tener ningún cierre de un tipo
    // si esa caja no tuvo ventas con esa forma de pago): no hace falta "quitarlas" a mano.
    const cierres: CierrePosnetInput[] = [
      ...this.cierresTarjeta()
        .filter((f) => f.monto !== null && f.monto > 0)
        .map((f) => ({ formaPago: 'TARJETA' as const, monto: f.monto!, nota: f.nota.trim() || null })),
      ...this.cierresQr()
        .filter((f) => f.monto !== null && f.monto > 0)
        .map((f) => ({ formaPago: 'MERCADO_PAGO_QR' as const, monto: f.monto!, nota: f.nota.trim() || null })),
    ];

    this.cerrandoCaja.set(true);
    this.errorCierre.set(null);

    const dolaresContadoEnviado = this.huboVentaDolares() ? dolaresContado : null;
    const cajaParaCorregir = this.cajaParaCorregir();
    const request = cajaParaCorregir
      ? this.cajaService.corregirCierre(
          cajaParaCorregir.id,
          conteo,
          cierres,
          entradasFisicasFinal,
          this.cambioContado(),
          dolaresContadoEnviado
        )
      : this.cajaService.cerrar(conteo, cierres, entradasFisicasFinal, this.cambioContado(), dolaresContadoEnviado);

    request.subscribe({
      next: (c) => {
        this.cerrandoCaja.set(false);
        this.cajaCerrada.emit(c);
      },
      error: (err) => {
        this.errorCierre.set(typeof err?.error === 'string' ? err.error : 'No se pudo guardar el cierre. Reintentá.');
        this.cerrandoCaja.set(false);
      },
    });
  }
}
