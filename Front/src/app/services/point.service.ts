import { DestroyRef, Injectable, computed, effect, inject, signal, untracked } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, interval } from 'rxjs';
import { environment } from '../../environments/environment';
import { ConectividadService } from './conectividad.service';
import { ModulosService } from './modulos.service';

export interface TerminalPoint {
  /** "MODELO__SERIE", el id de Mercado Pago. */
  id: string;
  nombre: string;
  /** Lo que dice la etiqueta de atrás del Point. */
  serie: string;
  /** PDV = integrado; STANDALONE = se cobra a mano en el Point. */
  modo: string | null;
  integrado: boolean;
}

export type EstadoCobroPoint =
  | 'CREADO'
  | 'EN_TERMINAL'
  | 'REVISAR_EN_POINT'
  | 'APROBADO'
  | 'RECHAZADO'
  | 'CANCELADO'
  | 'EXPIRADO'
  | 'DEVUELTO'
  | 'ERROR';

/** Las dos formas que cobra el Point: tarjeta, o QR en su pantalla. */
export type FormaPagoPoint = 'TARJETA' | 'MERCADO_PAGO_QR';

export interface CobroPoint {
  id: number;
  ordenId: string | null;
  terminalId: string;
  monto: number;
  formaPago: FormaPagoPoint;
  estado: EstadoCobroPoint;
  /** Todavía puede cambiar solo: hay que seguir esperando al Point. */
  enCurso: boolean;
  detalle: string | null;
  /** Lo que se le muestra al boletero ("El banco rechazó la tarjeta"). */
  motivo: string | null;
  tipoMedio: string | null;
  medioPago: string | null;
  cuotas: number | null;
  compraId: number | null;
  fecha: string | null;
}

/** Para el cierre de caja (ADMIN): lo cobrado con el Point en ventas, y lo cobrado sin venta. */
export interface ResumenCajaPoint {
  tarjeta: number;
  qr: number;
  cantidad: number;
  sinVenta: CobroPoint[];
}

/** Point elegido en ESTA tablet (cada tablet tiene el suyo al lado, como la ticketera). */
const CLAVE_POINT = 'pos-point';
/** Elegido a propósito "Sin Point": la tarjeta se cobra en el posnet y se anota a mano. */
export const SIN_POINT = 'NINGUNO';
/** Cobros mandados al Point que todavía no terminaron en una venta registrada (sobreviven a recargar la página). */
const CLAVE_COBROS = 'pos-cobros-point';
/** Los Points casi no cambian: alcanza con revisar cada tanto. */
const INTERVALO_MS = 60_000;

/**
 * Points de Mercado Pago para la pantalla de venta: cuál usa esta tablet (se elige en el botón
 * Dispositivos, junto a la ticketera) y los pedidos de cobro.
 */
@Injectable({ providedIn: 'root' })
export class PointService {
  private http = inject(HttpClient);
  private conectividad = inject(ConectividadService);
  private modulos = inject(ModulosService);
  private url = `${environment.apiBase}/interno/point`;

  readonly habilitado = this.modulos.pointActivo;
  readonly terminales = signal<TerminalPoint[]>([]);
  readonly consultado = signal(false);
  /** Error al pedir la lista (token mal, MP caído): se muestra en el selector. */
  readonly errorTerminales = signal<string | null>(null);
  readonly elegida = signal<string | null>(this.leer(CLAVE_POINT));

  readonly integradas = computed(() => this.terminales().filter((t) => t.integrado));

  /** El Point al que se mandan los cobros: el elegido si está integrado; si no hay ninguno
   * elegido y hay uno solo integrado, ése. Null = la tarjeta se anota a mano. */
  readonly efectiva = computed<TerminalPoint | null>(() => {
    if (!this.habilitado() || this.elegida() === SIN_POINT) return null;
    const lista = this.integradas();
    const elegida = lista.find((t) => t.id === this.elegida());
    if (elegida) return elegida;
    return lista.length === 1 ? lista[0] : null;
  });

  /** Hay varios Points integrados y esta tablet todavía no eligió el suyo (ni "Sin Point"). */
  readonly faltaElegir = computed(
    () => this.habilitado() && this.consultado() && this.efectiva() === null && this.elegida() !== SIN_POINT && this.integradas().length > 1
  );

  /** Se cobra con el Point: módulo prendido, Point elegido y con conexión (sin señal la orden no llega). */
  readonly usable = computed(() => this.efectiva() !== null && this.conectividad.enLinea());

  /** Cobros que el Point aprobó (o que quedaron esperando) y todavía no son una venta registrada. */
  readonly cobrosPendientes = signal<CobroPoint[]>(this.leerCobros());

  constructor() {
    effect(() => {
      if (this.habilitado()) untracked(() => this.actualizar());
    });
  }

  /** La pantalla de venta lo llama al abrirse: consulta ya y después cada tanto, mientras viva. */
  seguir(destroyRef: DestroyRef): void {
    this.actualizar();
    interval(INTERVALO_MS)
      .pipe(takeUntilDestroyed(destroyRef))
      .subscribe(() => this.actualizar());
  }

  actualizar(): void {
    if (!this.habilitado() || !this.conectividad.enLinea()) return;
    this.http.get<TerminalPoint[]>(`${this.url}/terminales`).subscribe({
      next: (lista) => {
        this.terminales.set(lista);
        this.consultado.set(true);
        this.errorTerminales.set(null);
      },
      error: (err) => {
        this.consultado.set(true);
        this.errorTerminales.set(typeof err?.error === 'string' && err.error ? err.error : 'No se pudo consultar los Points');
      },
    });
  }

  elegir(id: string): void {
    this.elegida.set(id);
    this.escribir(CLAVE_POINT, id);
  }

  // ---------- Cobros ----------

  crearCobro(terminalId: string, monto: number, claveIdempotencia: string, formaPago: FormaPagoPoint): Observable<CobroPoint> {
    return this.http.post<CobroPoint>(`${this.url}/cobros`, { terminalId, monto, claveIdempotencia, formaPago });
  }

  consultarCobro(id: number): Observable<CobroPoint> {
    return this.http.get<CobroPoint>(`${this.url}/cobros/${id}`);
  }

  cancelarCobro(id: number): Observable<CobroPoint> {
    return this.http.post<CobroPoint>(`${this.url}/cobros/${id}/cancelar`, null);
  }

  devolverCobro(id: number): Observable<CobroPoint> {
    return this.http.post<CobroPoint>(`${this.url}/cobros/${id}/devolver`, null);
  }

  /** Agrega o actualiza un cobro pendiente (mismo id = el mismo cobro con su estado nuevo). */
  recordarCobro(cobro: CobroPoint): void {
    this.guardarCobros([...this.cobrosPendientes().filter((c) => c.id !== cobro.id), cobro]);
  }

  /** Ya es una venta, se devolvió, o terminó sin cobrar: deja de estar pendiente. */
  olvidarCobros(ids: number[]): void {
    this.guardarCobros(this.cobrosPendientes().filter((c) => !ids.includes(c.id)));
  }

  /** Lo cobrado con el Point en una caja (ADMIN), para el cierre. */
  resumenCaja(cajaId: number): Observable<ResumenCajaPoint> {
    return this.http.get<ResumenCajaPoint>(`${this.url}/caja/${cajaId}/resumen`);
  }

  /** PDV = integrado; STANDALONE = manual. También el boletero: si se corta internet en la boletería,
   * pasa el Point a manual desde el celular para seguir cobrando con él. */
  cambiarModo(id: string, modo: 'PDV' | 'STANDALONE'): Observable<void> {
    return this.http.put<void>(`${this.url}/terminales/${encodeURIComponent(id)}/modo`, { modo });
  }

  // ---------- Sistema > Módulos (SUPERADMIN) ----------

  terminalesActualizadas(): Observable<TerminalPoint[]> {
    return this.http.get<TerminalPoint[]>(`${environment.apiBase}/interno/sistema/point/terminales`);
  }

  renombrar(id: string, nombre: string): Observable<void> {
    return this.http.put<void>(`${environment.apiBase}/interno/sistema/point/terminales/${encodeURIComponent(id)}/nombre`, { nombre });
  }

  private guardarCobros(lista: CobroPoint[]): void {
    this.cobrosPendientes.set(lista);
    try {
      if (lista.length) localStorage.setItem(CLAVE_COBROS, JSON.stringify(lista));
      else localStorage.removeItem(CLAVE_COBROS);
    } catch {
      // Sin storage: el aviso no sobrevive a recargar la página.
    }
  }

  private leerCobros(): CobroPoint[] {
    try {
      const crudo = localStorage.getItem(CLAVE_COBROS);
      return crudo ? (JSON.parse(crudo) as CobroPoint[]) : [];
    } catch {
      return [];
    }
  }

  private leer(clave: string): string | null {
    try {
      return localStorage.getItem(clave);
    } catch {
      return null;
    }
  }

  private escribir(clave: string, valor: string): void {
    try {
      localStorage.setItem(clave, valor);
    } catch {
      // Sin storage: la elección vale sólo hasta recargar.
    }
  }
}
