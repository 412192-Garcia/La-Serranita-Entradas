import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, firstValueFrom } from 'rxjs';
import { timeout } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { BoleteriaService, Reserva, VentaPosRequest } from './boleteria.service';
import { Caja, CajaService, TipoMovimientoCaja } from './caja.service';
import { ConectividadService } from './conectividad.service';

const COLA_KEY = 'serranita.pos.operacionesPendientes';

/** Un intento que tarda más que esto se da por caído y la operación queda encolada. */
const TIMEOUT_ENVIO_MS = 8_000;

/** Cada cuánto se reintenta la cola por su cuenta, por si no llegó el evento de reconexión. */
const INTERVALO_REINTENTO_MS = 20_000;

export interface PayloadRetiroAporte {
  monto: number;
  motivo: string;
  tipo: TipoMovimientoCaja;
}

export interface PayloadIngresoEntradas {
  cantidad: number;
}

export type OperacionPendiente =
  | { tipo: 'VENTA'; payload: VentaPosRequest }
  | { tipo: 'RETIRO_APORTE'; payload: PayloadRetiroAporte }
  | { tipo: 'INGRESO_ENTRADAS'; payload: PayloadIngresoEntradas };

interface EntradaCola {
  idempotencyKey: string;
  tipo: OperacionPendiente['tipo'];
  payload: unknown;
  /** ISO del momento real en que el boletero hizo la operación, no el de la sincronización. */
  fechaOriginal: string;
  estado: 'pendiente' | 'error';
  /** Motivo del rechazo del servidor. Sólo en estado 'error'. */
  mensajeError?: string;
}

/** Lo que devuelve ejecutar(): el servidor confirmó, quedó guardada para sincronizar sola (sin
 * conexión / timeout — no es culpa de la operación en sí), o el servidor la rechazó de una
 * (dato inválido, caja ya cerrada) — este último caso NO se reintenta solo, y el llamador no
 * debería tratarlo como si hubiera salido bien. */
export type ResultadoOperacion<T> =
  | { confirmada: true; resultado: T }
  | { confirmada: false; rechazada: false }
  | { confirmada: false; rechazada: true; mensaje: string };

/**
 * Cola local de las operaciones del POS que tienen que sobrevivir a un corte de conexión:
 * ventas, retiros/aportes e ingresos de entradas.
 *
 * El patrón NO es "si estoy offline encolo, si estoy online mando": eso deja afuera el caso
 * peligroso de mandar la petición justo cuando se corta, donde el servidor la procesa pero la
 * respuesta nunca vuelve. Acá TODA operación se guarda primero con una clave de idempotencia,
 * se intenta enseguida, y si falla queda para reintentar con esa MISMA clave — el backend
 * reconoce la clave repetida y devuelve lo que ya había guardado en vez de duplicar el cobro.
 */
@Injectable({
  providedIn: 'root',
})
export class OperacionesPendientesService {
  private boleteriaService = inject(BoleteriaService);
  private cajaService = inject(CajaService);
  private conectividad = inject(ConectividadService);

  private cola = signal<EntradaCola[]>(this.leerCola());

  /** Cuántas esperan para subir. La UI lo muestra para que el boletero sepa que falta confirmar. */
  readonly pendientes = computed(() => this.cola().filter((e) => e.estado === 'pendiente').length);
  /** Las que el servidor rechazó: no se reintentan solas, alguien las tiene que mirar. */
  readonly conError = computed(() => this.cola().filter((e) => e.estado === 'error'));

  private sincronizando = false;

  constructor() {
    window.addEventListener('online', () => this.sincronizar());
    setInterval(() => this.sincronizar(), INTERVALO_REINTENTO_MS);
    // Al entrar al POS puede haber quedado algo de un turno anterior sin sincronizar.
    this.sincronizar();
  }

  /**
   * Registra la operación y trata de mandarla ya mismo. Si el servidor responde, devuelve su
   * resultado real; si no hay señal, queda encolada y el llamador sigue de largo mostrando el
   * dato calculado localmente.
   */
  async ejecutar<T>(operacion: OperacionPendiente): Promise<ResultadoOperacion<T>> {
    const entrada: EntradaCola = {
      idempotencyKey: crypto.randomUUID(),
      tipo: operacion.tipo,
      payload: operacion.payload,
      fechaOriginal: new Date().toISOString(),
      estado: 'pendiente',
    };
    // Se persiste ANTES de tocar la red: si el navegador se cierra en medio del intento, la
    // operación no se pierde.
    this.guardar([...this.cola(), entrada]);

    try {
      const resultado = await this.enviar(entrada);
      this.quitar(entrada.idempotencyKey);
      return { confirmada: true, resultado: resultado as T };
    } catch (error) {
      const mensajeRechazo = this.registrarFallo(entrada.idempotencyKey, error);
      if (mensajeRechazo !== null) return { confirmada: false, rechazada: true, mensaje: mensajeRechazo };
      return { confirmada: false, rechazada: false };
    }
  }

  /** Reintenta lo pendiente, de lo más viejo a lo más nuevo. Un solo ciclo a la vez. */
  async sincronizar(): Promise<void> {
    if (this.sincronizando) return;
    if (!this.cola().some((e) => e.estado === 'pendiente')) return;
    if (!(await this.conectividad.verificar())) return;

    this.sincronizando = true;
    try {
      // Se recorre una copia: la cola se va modificando a medida que cada una sale bien.
      for (const entrada of this.cola().filter((e) => e.estado === 'pendiente')) {
        try {
          await this.enviar(entrada);
          this.quitar(entrada.idempotencyKey);
        } catch (error) {
          this.registrarFallo(entrada.idempotencyKey, error);
          // Si se cayó la señal otra vez, no tiene sentido seguir intentando el resto ahora.
          if (!this.esRechazoDelServidor(error)) break;
        }
      }
    } finally {
      this.sincronizando = false;
    }
  }

  /** Descarta una operación que el servidor rechazó y que el admin ya resolvió a mano. */
  descartar(idempotencyKey: string): void {
    this.quitar(idempotencyKey);
  }

  private enviar(entrada: EntradaCola): Promise<Reserva | Caja> {
    const { idempotencyKey, fechaOriginal } = entrada;
    let peticion: Observable<Reserva | Caja>;
    if (entrada.tipo === 'VENTA') {
      const p = entrada.payload as VentaPosRequest;
      peticion = this.boleteriaService.registrarVentaPos({ ...p, idempotencyKey, fechaOriginal });
    } else if (entrada.tipo === 'RETIRO_APORTE') {
      const p = entrada.payload as PayloadRetiroAporte;
      peticion = this.cajaService.registrarRetiro(p.monto, p.motivo, p.tipo, idempotencyKey, fechaOriginal);
    } else {
      const p = entrada.payload as PayloadIngresoEntradas;
      peticion = this.cajaService.registrarIngresoEntradas(p.cantidad, idempotencyKey, fechaOriginal);
    }
    return firstValueFrom(peticion.pipe(timeout(TIMEOUT_ENVIO_MS)));
  }

  /**
   * Un rechazo del servidor (4xx de negocio: la caja ya se cerró, datos inválidos) no se
   * reintenta solo — reintentarlo va a fallar igual. Cualquier otra cosa (timeout, sin red,
   * 5xx) sí queda pendiente: es un problema de momento, no de la operación.
   * Devuelve el mensaje de rechazo (para mostrárselo ya mismo al que hizo la operación) o null
   * si no fue un rechazo real, sino un problema de conexión.
   */
  private registrarFallo(idempotencyKey: string, error: unknown): string | null {
    if (!this.esRechazoDelServidor(error)) return null;
    const mensaje = error instanceof HttpErrorResponse && typeof error.error === 'string'
      ? error.error
      : 'El servidor rechazó la operación';
    this.guardar(
      this.cola().map((e) =>
        e.idempotencyKey === idempotencyKey ? { ...e, estado: 'error' as const, mensajeError: mensaje } : e
      )
    );
    return mensaje;
  }

  private esRechazoDelServidor(error: unknown): boolean {
    return error instanceof HttpErrorResponse && error.status >= 400 && error.status < 500;
  }

  private quitar(idempotencyKey: string): void {
    this.guardar(this.cola().filter((e) => e.idempotencyKey !== idempotencyKey));
  }

  private guardar(entradas: EntradaCola[]): void {
    this.cola.set(entradas);
    try {
      localStorage.setItem(COLA_KEY, JSON.stringify(entradas));
    } catch {
      // Sin espacio: la cola sigue viva en memoria para este turno, que es lo que importa.
    }
  }

  private leerCola(): EntradaCola[] {
    try {
      const crudo = localStorage.getItem(COLA_KEY);
      return crudo ? (JSON.parse(crudo) as EntradaCola[]) : [];
    } catch {
      return [];
    }
  }
}
