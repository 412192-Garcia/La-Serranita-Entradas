import { Injectable, signal } from '@angular/core';
import { environment } from '../../environments/environment';

/** Cada cuánto se reintenta el ping mientras estamos caídos, para notar la vuelta de la señal aunque no llegue el evento `online` del navegador. */
const INTERVALO_REINTENTO_MS = 15_000;

/** Un ping que tarda más que esto se considera "no hay señal útil" aunque termine respondiendo. */
const TIMEOUT_PING_MS = 5_000;

/**
 * Fuente de verdad de si el backend está realmente alcanzable.
 *
 * `navigator.onLine` sólo dice si hay una interfaz de red levantada — en el parque eso es
 * justamente lo que más engaña: el wifi sigue conectado pero sin salida a internet, y el
 * navegador informa `true`. Por eso se combina con un ping real y liviano al backend.
 *
 * Ojo: esto es una señal de conveniencia (sirve para decidir si vale la pena intentar algo
 * ya mismo, y para mostrar el estado en la UI), NO una garantía. Cualquier operación
 * importante igual tiene que manejar su propio fallo de red — ver OperacionesPendientesService.
 */
@Injectable({
  providedIn: 'root',
})
export class ConectividadService {
  private pingUrl = `${environment.apiBase}/ping`;

  private enLineaActual = signal(navigator.onLine);
  readonly enLinea = this.enLineaActual.asReadonly();

  private verificacionEnCurso = false;

  constructor() {
    window.addEventListener('online', () => this.verificar());
    // El navegador avisa que se cayó la interfaz: eso sí es confiable, no hace falta pingear.
    window.addEventListener('offline', () => this.enLineaActual.set(false));

    setInterval(() => {
      // Mientras estamos caídos se pingea para detectar la vuelta; si creemos estar en línea
      // igual se revisa de a ratos, porque el wifi puede quedar conectado y sin salida sin
      // que el navegador emita ningún evento.
      this.verificar();
    }, INTERVALO_REINTENTO_MS);

    this.verificar();
  }

  /** Fuerza una comprobación inmediata contra el backend. La usa la cola antes de reintentar. */
  async verificar(): Promise<boolean> {
    if (!navigator.onLine) {
      this.enLineaActual.set(false);
      return false;
    }
    if (this.verificacionEnCurso) {
      return this.enLineaActual();
    }
    this.verificacionEnCurso = true;
    try {
      const alcanzable = await this.pingear();
      this.enLineaActual.set(alcanzable);
      return alcanzable;
    } finally {
      this.verificacionEnCurso = false;
    }
  }

  private async pingear(): Promise<boolean> {
    const abortar = new AbortController();
    const temporizador = setTimeout(() => abortar.abort(), TIMEOUT_PING_MS);
    try {
      // fetch en vez de HttpClient: no tiene que pasar por el interceptor de auth (el ping es
      // público) ni ensuciar la consola con errores cada vez que se cae la señal.
      const respuesta = await fetch(this.pingUrl, { method: 'GET', signal: abortar.signal, cache: 'no-store' });
      return respuesta.ok;
    } catch {
      return false;
    } finally {
      clearTimeout(temporizador);
    }
  }
}
