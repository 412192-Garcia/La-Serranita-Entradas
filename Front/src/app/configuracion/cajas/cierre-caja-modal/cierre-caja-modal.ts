import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CajaService, Caja, CierrePosnetInput, FormaPagoPosnet } from '../../../Services/caja.service';
import { MoneyInputDirective } from '../../../shared/money-input/money-input.directive';
import { RetiroEfectivoModal } from '../../../shared/retiro-efectivo-modal/retiro-efectivo-modal';

/** Billetes de curso legal actual en Argentina (sin monedas). */
const DENOMINACIONES = [100, 200, 500, 1000, 2000, 10000, 20000];

/** Una fila de cierre de posnet dentro de su columna (Tarjeta o QR ya está implícito en cuál signal la contiene). */
interface FilaMontoNota {
  monto: number | null;
  nota: string;
}

type ModoPosnet = 'SEPARADO' | 'COMBINADO';
type TipoFilaPosnet = FormaPagoPosnet | 'COMBINADO';

/**
 * Vive en Cajas (admin), no en POS: cerrar caja dejó de ser self-service — un ADMIN es quien
 * la cierra, sin importar de qué boletero, por eso todo acá se resuelve por id explícito
 * (cajaId) en vez de "mi propia caja abierta".
 */
@Component({
  selector: 'app-cierre-caja-modal',
  imports: [CurrencyPipe, FormsModule, MoneyInputDirective, RetiroEfectivoModal],
  templateUrl: './cierre-caja-modal.html',
  styleUrl: './cierre-caja-modal.css',
})
export class CierreCajaModal {
  private cajaService = inject(CajaService);

  /** Si viene seteada, el modal arranca precargado con sus datos y "Confirmar" corrige esa caja ya cerrada en vez de cerrar una abierta. */
  cajaParaCorregir = input<Caja | null>(null);
  /** Id de la caja abierta que se está por cerrar (null cuando se está corrigiendo un cierre ya hecho, ahí se usa el id de cajaParaCorregir). */
  cajaId = input<number | null>(null);
  /** Detalle de esa misma caja abierta (via obtenerDetalle): sólo se usa para saber si hubo ventas en dólares y qué movimientos ya tiene cargados. */
  cajaAbierta = input<Caja | null>(null);

  cajaCerrada = output<Caja>();
  cerrar = output<void>();
  /** Se emite cuando se agrega un retiro/aporte sin llegar a cerrar: el padre tiene que refrescar cajaAbierta para que el listado de movimientos y el total se vean al día. */
  cajaActualizada = output<Caja>();

  /** Sea que se esté cerrando o corrigiendo, de cuál de las dos cajas sacamos el dato. */
  huboVentaDolares = computed(() => this.cajaParaCorregir()?.huboVentaDolares ?? this.cajaAbierta()?.huboVentaDolares ?? false);
  /** Movimientos (retiros/aportes) ya registrados en la caja que se está cerrando o corrigiendo. */
  retirosMostrados = computed(() => this.cajaParaCorregir()?.retiros ?? this.cajaAbierta()?.retiros ?? []);
  mostrarMovimiento = signal(false);

  readonly denominaciones = DENOMINACIONES;
  /** Cuántos billetes de cada denominación contó el boletero al cerrar. */
  conteoEfectivo = signal<Record<number, number>>({});
  /** Total en billetes chicos (50, 20, etc.), cargado de una vez en vez de billete por billete. */
  cambioContado = signal<number | null>(null);
  /** Por separado (default) o juntos: sólo cambia cómo se cargan los cierres de posnet, no lo esperado (que sigue viniendo de las ventas reales). */
  modoPosnet = signal<ModoPosnet>('SEPARADO');
  /** Arrancan con una fila vacía cada una: lo normal es tener al menos un cierre de cada posnet. */
  cierresTarjeta = signal<FilaMontoNota[]>([{ monto: null, nota: '' }]);
  cierresQr = signal<FilaMontoNota[]>([{ monto: null, nota: '' }]);
  cierresCombinados = signal<FilaMontoNota[]>([{ monto: null, nota: '' }]);
  entradasFisicasCortadas = signal<number | null>(null);
  /** Dólares que el boletero contó al cerrar. Sólo se pide si huboVentaDolares(). */
  dolaresContado = signal<number | null>(null);
  cerrandoCaja = signal(false);
  errorCierre = signal<string | null>(null);

  /** Total contado en efectivo, calculado en vivo a partir del conteo por denominación más el cambio. */
  montoContadoCalculado = computed(() =>
    this.denominaciones.reduce((acc, d) => acc + d * (this.conteoEfectivo()[d] ?? 0), 0) + (this.cambioContado() ?? 0)
  );

  /**
   * Identidad de la última caja para la que se precargó/reseteó el formulario (cajaParaCorregir
   * si se está corrigiendo, si no cajaId). El modal no se destruye al ocultarse (ver
   * [class.oculto] en el padre): reabrir la MISMA caja tiene que dejar todo lo cargado tal
   * cual quedó, así que sólo se toca el estado cuando esto cambia de verdad (pasar a
   * cerrar/corregir una caja distinta), no en cada show/hide.
   */
  private ultimaCajaId: number | null | 'ninguna' = 'ninguna';

  constructor() {
    effect(() => {
      const paraCorregir = this.cajaParaCorregir();
      const idObjetivo = paraCorregir?.id ?? this.cajaId() ?? null;
      if (idObjetivo === this.ultimaCajaId) return;
      this.ultimaCajaId = idObjetivo;
      if (paraCorregir) {
        this.precargarDesdeCaja(paraCorregir);
      } else {
        this.resetearEstado();
      }
    });
  }

  private precargarDesdeCaja(caja: Caja): void {
    const conteo: Record<number, number> = {};
    for (const c of caja.conteoEfectivo) conteo[c.denominacion] = c.cantidad;
    this.conteoEfectivo.set(conteo);
    this.cambioContado.set(caja.cambioContado);

    const combinados = caja.cierresPosnet.filter((c) => c.formaPago === null).map((c) => ({ monto: c.monto, nota: c.nota ?? '' }));
    if (combinados.length) {
      this.modoPosnet.set('COMBINADO');
      this.cierresCombinados.set(combinados);
      this.cierresTarjeta.set([{ monto: null, nota: '' }]);
      this.cierresQr.set([{ monto: null, nota: '' }]);
    } else {
      const tarjeta = caja.cierresPosnet.filter((c) => c.formaPago === 'TARJETA').map((c) => ({ monto: c.monto, nota: c.nota ?? '' }));
      const qr = caja.cierresPosnet.filter((c) => c.formaPago === 'MERCADO_PAGO_QR').map((c) => ({ monto: c.monto, nota: c.nota ?? '' }));
      this.modoPosnet.set('SEPARADO');
      this.cierresTarjeta.set(tarjeta.length ? tarjeta : [{ monto: null, nota: '' }]);
      this.cierresQr.set(qr.length ? qr : [{ monto: null, nota: '' }]);
      this.cierresCombinados.set([{ monto: null, nota: '' }]);
    }

    this.entradasFisicasCortadas.set(caja.entradasFisicasCortadas);
    this.dolaresContado.set(caja.dolaresContado);
    this.errorCierre.set(null);
  }

  private resetearEstado(): void {
    this.conteoEfectivo.set({});
    this.cambioContado.set(null);
    this.modoPosnet.set('SEPARADO');
    this.cierresTarjeta.set([{ monto: null, nota: '' }]);
    this.cierresQr.set([{ monto: null, nota: '' }]);
    this.cierresCombinados.set([{ monto: null, nota: '' }]);
    this.entradasFisicasCortadas.set(null);
    this.dolaresContado.set(null);
    this.errorCierre.set(null);
  }

  /** El monto/conteo/posnet/entradas que ya estaban cargados en el formulario no se tocan: sólo se refresca la lista de movimientos y el total de retiros vía cajaActualizada. */
  onMovimientoRegistrado(caja: Caja): void {
    this.mostrarMovimiento.set(false);
    this.cajaActualizada.emit(caja);
  }

  setConteoEfectivo(denominacion: number, cantidad: number): void {
    const valor = Math.max(0, Math.floor(cantidad) || 0);
    this.conteoEfectivo.update((c) => ({ ...c, [denominacion]: valor }));
  }

  private filasCierre(tipo: TipoFilaPosnet) {
    if (tipo === 'TARJETA') return this.cierresTarjeta;
    if (tipo === 'MERCADO_PAGO_QR') return this.cierresQr;
    return this.cierresCombinados;
  }

  agregarCierre(tipo: TipoFilaPosnet): void {
    this.filasCierre(tipo).update((c) => [...c, { monto: null, nota: '' }]);
  }

  quitarCierre(tipo: TipoFilaPosnet, index: number): void {
    this.filasCierre(tipo).update((c) => c.filter((_, i) => i !== index));
  }

  actualizarCierre(tipo: TipoFilaPosnet, index: number, cambios: Partial<FilaMontoNota>): void {
    this.filasCierre(tipo).update((c) => c.map((fila, i) => (i === index ? { ...fila, ...cambios } : fila)));
  }

  confirmarCierre(): void {
    const entradasFisicasCortadas = this.entradasFisicasCortadas();
    if (entradasFisicasCortadas === null || entradasFisicasCortadas < 0) {
      this.errorCierre.set('Indicá cuántas entradas cortaste del talonario.');
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
    const cierres: CierrePosnetInput[] =
      this.modoPosnet() === 'COMBINADO'
        ? this.cierresCombinados()
            .filter((f) => f.monto !== null && f.monto > 0)
            .map((f) => ({ formaPago: null, monto: f.monto!, nota: f.nota.trim() || null }))
        : [
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
          entradasFisicasCortadas,
          this.cambioContado(),
          dolaresContadoEnviado
        )
      : this.cajaService.cerrarComoAdmin(
          this.cajaId()!,
          conteo,
          cierres,
          entradasFisicasCortadas,
          this.cambioContado(),
          dolaresContadoEnviado
        );

    request.subscribe({
      next: (c) => {
        this.cerrandoCaja.set(false);
        // Ya se guardó: si más adelante se cierra/corrige OTRA caja con el mismo modal
        // (nunca se destruye entre usos), no tiene que arrancar con estos datos puestos.
        this.resetearEstado();
        this.ultimaCajaId = 'ninguna';
        this.cajaCerrada.emit(c);
      },
      error: (err) => {
        this.errorCierre.set(typeof err?.error === 'string' ? err.error : 'No se pudo guardar el cierre. Reintentá.');
        this.cerrandoCaja.set(false);
      },
    });
  }
}
