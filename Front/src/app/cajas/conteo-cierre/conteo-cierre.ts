import { Component, computed, effect, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Caja, CierrePosnetInput, ConteoDenominacion, FormaPagoPosnet } from '../../services/caja.service';
import { MoneyInputDirective } from '../../shared/money-input/money-input.directive';
import { PesosPipe } from '../../shared/pesos.pipe';
import { ResumenCajaPoint } from '../../services/point.service';

/** Billetes de curso legal actual en Argentina (sin monedas). */
const DENOMINACIONES = [100, 200, 500, 1000, 2000, 10000, 20000];

interface FilaMontoNota {
  monto: number | null;
  nota: string;
}

type ModoPosnet = 'SEPARADO' | 'COMBINADO';
type TipoFilaPosnet = FormaPagoPosnet | 'COMBINADO';

/** Lo que el boletero/admin cargó al recontar: efectivo por denominación, cierres de posnet,
 *  entradas que quedan en el talonario y dólares. Lo comparten el modal de cierre y "Corregir caja". */
export interface ValorConteoCierre {
  conteoEfectivo: ConteoDenominacion[];
  cierresPosnet: CierrePosnetInput[];
  entradasFisicasRestantes: number | null;
  cambioContado: number | null;
  dolaresContado: number | null;
}

@Component({
  selector: 'app-conteo-cierre',
  imports: [PesosPipe, FormsModule, MoneyInputDirective],
  templateUrl: './conteo-cierre.html',
  styleUrl: './conteo-cierre.css',
  host: { '[class.conteo-dos-columnas]': 'dosColumnas()' },
})
export class ConteoCierre {
  /** Si viene, el formulario arranca precargado con lo que ya tiene esa caja (corrección). Null = todo en blanco (cierre nuevo). */
  precarga = input<Caja | null>(null);
  /** Identidad de la caja cuando NO hay precarga (cierre nuevo): al cambiar, limpia el formulario. */
  cajaId = input<number | null>(null);
  huboVentaDolares = input(false);
  /** true = efectivo a la izquierda, resto a la derecha (modal ancho). false (default) = todo apilado (columna angosta de "Corregir caja"). */
  dosColumnas = input(false);
  /** Lo que cobró el Point integrado en esta caja (sólo en un cierre nuevo): precarga el posnet. */
  cobrosPoint = input<ResumenCajaPoint | null>(null);
  /** Cuánto se precargó desde el Point, para avisarlo arriba de las filas. */
  precargadoPoint = signal<{ tarjeta: number; qr: number } | null>(null);

  readonly denominaciones = DENOMINACIONES;
  conteoEfectivo = signal<Record<number, number>>({});
  cambioContado = signal<number | null>(null);
  modoPosnet = signal<ModoPosnet>('COMBINADO');
  cierresTarjeta = signal<FilaMontoNota[]>([{ monto: null, nota: '' }]);
  cierresQr = signal<FilaMontoNota[]>([{ monto: null, nota: '' }]);
  cierresCombinados = signal<FilaMontoNota[]>([{ monto: null, nota: '' }]);
  entradasFisicasRestantes = signal<number | null>(null);
  dolaresContado = signal<number | null>(null);

  montoContadoCalculado = computed(() =>
    this.denominaciones.reduce((acc, d) => acc + d * (this.conteoEfectivo()[d] ?? 0), 0) + (this.cambioContado() ?? 0)
  );

  private ultimaCajaId: number | null | 'ninguna' = 'ninguna';
  private resumenAplicado: ResumenCajaPoint | null = null;

  constructor() {
    effect(() => {
      const caja = this.precarga();
      const idObjetivo = caja?.id ?? this.cajaId() ?? null;
      if (idObjetivo === this.ultimaCajaId) return;
      this.ultimaCajaId = idObjetivo;
      if (caja) this.precargarDesdeCaja(caja);
      else this.resetearEstado();
    });

    // Cierre nuevo: lo que cobró el Point integrado ya lo sabe el sistema, así que se precarga por
    // separado (tarjeta y QR). El admin suma los cierres de otros posnet o del Point en modo manual.
    // Sólo si el posnet todavía está vacío: nunca pisa lo que ya se tipeó.
    effect(() => {
      const resumen = this.cobrosPoint();
      // Una sola vez por resumen: si después el admin borra las filas, no se vuelven a llenar.
      if (!resumen || resumen === this.resumenAplicado || this.precarga() || (resumen.tarjeta <= 0 && resumen.qr <= 0)) return;
      this.resumenAplicado = resumen;
      const vacio = untracked(() => [...this.cierresTarjeta(), ...this.cierresQr(), ...this.cierresCombinados()]).every((f) => f.monto === null);
      if (!vacio) return;
      const fila = (monto: number) => [{ monto: monto > 0 ? monto : null, nota: monto > 0 ? 'Point integrado' : '' }];
      this.modoPosnet.set('SEPARADO');
      this.cierresTarjeta.set(fila(resumen.tarjeta));
      this.cierresQr.set(fila(resumen.qr));
      this.precargadoPoint.set({ tarjeta: resumen.tarjeta, qr: resumen.qr });
    });
  }

  /**
   * Vuelve a cargar el formulario desde la caja precargada (o lo limpia). Para "Cancelar" o
   * tras guardar. Deja `ultimaCajaId` en 'ninguna' para que reabrir la MISMA caja vuelva a
   * arrancar limpio (si no, el effect no re-dispara por id igual).
   */
  reset(): void {
    this.ultimaCajaId = 'ninguna';
    const caja = this.precarga();
    if (caja) this.precargarDesdeCaja(caja);
    else this.resetearEstado();
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

    this.entradasFisicasRestantes.set(caja.entradasFisicasRestantes);
    this.dolaresContado.set(caja.dolaresContado);
  }

  private resetearEstado(): void {
    this.precargadoPoint.set(null);
    this.conteoEfectivo.set({});
    this.cambioContado.set(null);
    this.modoPosnet.set('COMBINADO');
    this.cierresTarjeta.set([{ monto: null, nota: '' }]);
    this.cierresQr.set([{ monto: null, nota: '' }]);
    this.cierresCombinados.set([{ monto: null, nota: '' }]);
    this.entradasFisicasRestantes.set(null);
    this.dolaresContado.set(null);
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

  /** El payload listo para mandar al backend. Computed para que el padre pueda derivarlo en vivo. */
  valor = computed<ValorConteoCierre>(() => {
    const conteoEfectivo = this.denominaciones
      .map((denominacion) => ({ denominacion, cantidad: this.conteoEfectivo()[denominacion] ?? 0 }))
      .filter((c) => c.cantidad > 0);

    // Las filas sin monto se ignoran (es normal no tener ningún cierre de un tipo).
    const cierresPosnet: CierrePosnetInput[] =
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

    return {
      conteoEfectivo,
      cierresPosnet,
      entradasFisicasRestantes: this.entradasFisicasRestantes(),
      cambioContado: this.cambioContado(),
      dolaresContado: this.huboVentaDolares() ? this.dolaresContado() : null,
    };
  });

  /** Mensaje de error si algo falta, o null si está listo para guardar. */
  validar(): string | null {
    const restantes = this.entradasFisicasRestantes();
    if (restantes === null || restantes < 0) {
      return 'Indicá cuántas entradas quedan en el talonario.';
    }
    if (this.huboVentaDolares()) {
      const d = this.dolaresContado();
      if (d === null || d < 0) return 'Esta caja tuvo ventas en dólares: indicá cuántos dólares contaste.';
    }
    return null;
  }
}
