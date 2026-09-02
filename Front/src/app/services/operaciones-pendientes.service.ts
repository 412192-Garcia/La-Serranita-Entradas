import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, firstValueFrom } from 'rxjs';
import { timeout } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { BoleteriaService, Reserva, VentaPosRequest } from './boleteria.service';
import { Caja, CajaService, TipoMovimientoCaja, TipoMovimientoEntradas } from './caja.service';
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
  /** Caja propia del boletero al momento de hacer el movimiento: si esto se rechaza porque esa
   * caja ya no está abierta, queda guardado para que un admin sepa cuál reabrir y reintentar. */
  cajaId: number;
}

export interface PayloadIngresoEntradas {
  cantidad: number;
  tipo: TipoMovimientoEntradas;
  motivo?: string;
  cajaId: number;
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
  /** Claves que un envío en vivo (ejecutar) tiene ahora mismo en vuelo: sincronizar() las
   * saltea para no mandar la misma operación —y su misma idempotencyKey— por duplicado en
   * paralelo (la colisión daría un 500 por la constraint unique y marcaría error una venta
   * que en realidad entró). */
  private enviandoEnVivo = new Set<string>();

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

    // Si ya sabemos (por el chequeo periódico de ConectividadService, o por el evento `offline`
    // del navegador) que no hay señal, ni vale la pena intentar mandarla: sin esto, cada
    // operación se quedaba esperando el timeout completo (8s) — o, peor, lo que tarde en volver
    // un 502 del proxy cuando el que está caído es el backend y no la red del dispositivo — antes
    // de caer a la cola. Con la señal ya conocida como caída, se encola al toque.
    if (!this.conectividad.enLinea()) {
      return { confirmada: false, rechazada: false };
    }

    this.enviandoEnVivo.add(entrada.idempotencyKey);
    try {
      // false: es el primer intento, en vivo — si el servidor lo rechaza, la persona que lo
      // tipeó lo ve ahí mismo (ver enviar()) y no hace falta guardarlo aparte para un admin.
      const resultado = await this.enviar(entrada, false);
      this.quitar(entrada.idempotencyKey);
      return { confirmada: true, resultado: resultado as T };
    } catch (error) {
      const mensajeRechazo = this.registrarFallo(entrada.idempotencyKey, error, false);
      if (mensajeRechazo !== null) return { confirmada: false, rechazada: true, mensaje: mensajeRechazo };
      // No fue un rechazo de negocio: probablemente se acaba de caer la señal. Se refresca el
      // estado ya mismo (sin esperar el próximo chequeo periódico, hasta 15s) para que la
      // PRÓXIMA operación que se intente ya sepa que hay que encolar directo, sin repetir la espera.
      this.conectividad.verificar();
      return { confirmada: false, rechazada: false };
    } finally {
      this.enviandoEnVivo.delete(entrada.idempotencyKey);
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
      // Se saltean las que un envío en vivo tiene en vuelo (ver enviandoEnVivo).
      for (const entrada of this.cola().filter(
        (e) => e.estado === 'pendiente' && !this.enviandoEnVivo.has(e.idempotencyKey),
      )) {
        try {
          // true: esto ya es un reintento en segundo plano — si el servidor lo rechaza acá,
          // nadie lo está mirando en vivo, así que sí amerita quedar registrado para un admin.
          await this.enviar(entrada, true);
          this.quitar(entrada.idempotencyKey);
        } catch (error) {
          this.registrarFallo(entrada.idempotencyKey, error, true);
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

  /** esReintento: false = primer intento en vivo (el que lo tipeó lo ve al toque); true = un
   * reintento en segundo plano de la cola, sin nadie mirando. Ver registrarFallo. */
  private enviar(entrada: EntradaCola, esReintento: boolean): Promise<Reserva | Caja> {
    const { idempotencyKey, fechaOriginal } = entrada;
    let peticion: Observable<Reserva | Caja>;
    if (entrada.tipo === 'VENTA') {
      const p = entrada.payload as VentaPosRequest;
      peticion = this.boleteriaService.registrarVentaPos({ ...p, idempotencyKey, fechaOriginal, esReintentoEncolado: esReintento });
    } else if (entrada.tipo === 'RETIRO_APORTE') {
      const p = entrada.payload as PayloadRetiroAporte;
      peticion = this.cajaService.registrarRetiro(p.monto, p.motivo, p.tipo, idempotencyKey, fechaOriginal, esReintento, p.cajaId);
    } else {
      const p = entrada.payload as PayloadIngresoEntradas;
      peticion = this.cajaService.registrarIngresoEntradas(p.cantidad, p.tipo, p.motivo, idempotencyKey, fechaOriginal, esReintento, p.cajaId);
    }
    return firstValueFrom(peticion.pipe(timeout(TIMEOUT_ENVIO_MS)));
  }

  /**
   * Un rechazo del servidor (4xx de negocio: la caja ya se cerró, datos inválidos) no se
   * reintenta solo — reintentarlo va a fallar igual. Cualquier otra cosa (timeout, sin red,
   * 5xx) sí queda pendiente: es un problema de momento, no de la operación.
   *
   * En el primer intento en vivo (esReintento=false), un rechazo no deja ningún rastro: la
   * persona que lo tipeó ya lo ve en el modal (vía el mensaje que se devuelve acá) y lo corrige
   * ahí mismo — guardar igual un cartel de "error" que después hay que descartar a mano sería
   * ruido de algo que ya se resolvió solo. Sólo en un reintento en segundo plano (nadie mirando)
   * vale la pena dejarlo marcado para que alguien lo note después.
   *
   * Devuelve el mensaje de rechazo (para mostrárselo ya mismo al que hizo la operación) o null
   * si no fue un rechazo real, sino un problema de conexión.
   */
  private registrarFallo(idempotencyKey: string, error: unknown, esReintento: boolean): string | null {
    if (!this.esRechazoDelServidor(error)) return null;
    const mensaje = error instanceof HttpErrorResponse && typeof error.error === 'string'
      ? error.error
      : 'El servidor rechazó la operación';
    if (esReintento) {
      this.guardar(
        this.cola().map((e) =>
          e.idempotencyKey === idempotencyKey ? { ...e, estado: 'error' as const, mensajeError: mensaje } : e
        )
      );
    } else {
      this.quitar(idempotencyKey);
    }
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
