import { ErrorHandler, Injectable, Injector, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { mostrarAvisoGlobal } from './shared/aviso-global.util';
import { environment } from '../environments/environment';
import { SesionService } from './services/sesion.service';

/** Como mucho, esta cantidad de reportes por carga de página: un error dentro de un bucle no inunda el servidor. */
const MAX_REPORTES = 10;
/** El mismo mensaje no se vuelve a reportar antes de esto. */
const REPETIDO_MS = 60_000;

/**
 * Red de contención para errores de JS que no se manejaron en ningún otro lado: un bug real
 * (undefined.algo, un null que no debería), no un rechazo de negocio — esos ya los maneja cada
 * pantalla con su propio `.subscribe({ error })`, que nunca llega hasta acá. Antes de esto, un
 * error así rompía la pantalla en silencio: no quedaba loggeado en ningún lado visible ni se
 * avisaba nada, y el boletero se quedaba sin entender por qué la app dejó de responder.
 *
 * Además de avisarle al usuario, lo manda al servidor (Sistema > Errores, área Navegador): si no,
 * sólo quedaba en la consola de quien lo sufrió y nadie se enteraba.
 */
@Injectable()
export class GlobalErrorHandler implements ErrorHandler {
  // Se pide SesionService recién al reportar (no en el constructor): el ErrorHandler se crea muy
  // temprano y pedirlo acá armaría una dependencia circular con HttpClient.
  private injector = inject(Injector);
  private reportados = 0;
  private ultimoReporte = new Map<string, number>();

  handleError(error: unknown): void {
    // Un HttpErrorResponse que llega hasta acá es un observable al que a alguna pantalla se le
    // olvidó ponerle handler de error — ya lo logueamos, pero no hace falta un cartel genérico
    // encima: si la pantalla tiene su propio manejo, éste no debería dispararse casi nunca.
    if (error instanceof HttpErrorResponse) {
      console.error('Error HTTP sin manejar en la pantalla que lo generó:', error);
      return;
    }

    console.error('Error inesperado:', error);

    const versionVieja = esErrorDeVersionDesactualizada(error);
    // Una versión vieja en caché no es un bug: se arregla recargando.
    if (!versionVieja) this.reportar(error);

    const mensaje = versionVieja
      ? 'Hay una versión nueva de la app: recargá la página para actualizarla.'
      : 'Algo salió mal. Si la pantalla dejó de responder, recargá la página.';

    mostrarAvisoGlobal(mensaje, { etiqueta: 'Recargar', onClick: () => window.location.reload() });
  }

  /** Nunca puede tirar: si el reporte falla (sin conexión, servidor caído), se pierde y listo. */
  private reportar(error: unknown): void {
    try {
      const mensaje = (error instanceof Error ? `${error.name}: ${error.message}` : String(error)).slice(0, 1000);
      if (esRuido(mensaje) || this.reportados >= MAX_REPORTES) return;
      const ahora = Date.now();
      if (ahora - (this.ultimoReporte.get(mensaje) ?? 0) < REPETIDO_MS) return;
      this.ultimoReporte.set(mensaje, ahora);
      this.reportados++;

      const headers: Record<string, string> = { 'Content-Type': 'application/json' };
      const token = this.injector.get(SesionService).token();
      if (token) headers['Authorization'] = `Bearer ${token}`;
      // fetch y no HttpClient: un error dentro del interceptor no puede volver a caer acá en bucle.
      // Sólo la ruta, sin query string: puede traer datos del cliente (códigos de reserva, mails).
      fetch(`${environment.apiBase}/errores-cliente`, {
        method: 'POST',
        headers,
        keepalive: true,
        body: JSON.stringify({
          mensaje,
          stack: error instanceof Error ? (error.stack ?? '').slice(0, 6000) : null,
          pantalla: window.location.pathname,
          navegador: navigator.userAgent,
        }),
      }).catch(() => {});
    } catch {
      // Reportar es lo de menos: el aviso al usuario igual se muestra.
    }
  }
}

/**
 * Pasa cuando el navegador (por el service worker, que cachea agresivo) todavía tiene el
 * index.html de una versión vieja, que apunta a un archivo .js que ya no existe porque se
 * redesplegó una versión nueva — el mensaje genérico de "algo salió mal" sería engañoso acá,
 * cuando lo único que hace falta es recargar para traer la versión actual.
 */
function esErrorDeVersionDesactualizada(error: unknown): boolean {
  const mensaje = error instanceof Error ? error.message : String(error);
  return /Loading chunk|ChunkLoadError|Failed to fetch dynamically imported module|error loading dynamically imported module/i.test(
    mensaje
  );
}

/** Avisos del navegador que no son errores de la app (los dispara el propio navegador o una extensión). */
function esRuido(mensaje: string): boolean {
  return /ResizeObserver loop|Script error\.?$|chrome-extension:|moz-extension:/i.test(mensaje);
}
