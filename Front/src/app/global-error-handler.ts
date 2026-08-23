import { ErrorHandler, Injectable } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { mostrarAvisoGlobal } from './shared/aviso-global.util';

/**
 * Red de contención para errores de JS que no se manejaron en ningún otro lado: un bug real
 * (undefined.algo, un null que no debería), no un rechazo de negocio — esos ya los maneja cada
 * pantalla con su propio `.subscribe({ error })`, que nunca llega hasta acá. Antes de esto, un
 * error así rompía la pantalla en silencio: no quedaba loggeado en ningún lado visible ni se
 * avisaba nada, y el boletero se quedaba sin entender por qué la app dejó de responder.
 */
@Injectable()
export class GlobalErrorHandler implements ErrorHandler {
  handleError(error: unknown): void {
    // Un HttpErrorResponse que llega hasta acá es un observable al que a alguna pantalla se le
    // olvidó ponerle handler de error — ya lo logueamos, pero no hace falta un cartel genérico
    // encima: si la pantalla tiene su propio manejo, éste no debería dispararse casi nunca.
    if (error instanceof HttpErrorResponse) {
      console.error('Error HTTP sin manejar en la pantalla que lo generó:', error);
      return;
    }

    console.error('Error inesperado:', error);

    const mensaje = esErrorDeVersionDesactualizada(error)
      ? 'Hay una versión nueva de la app: recargá la página para actualizarla.'
      : 'Algo salió mal. Si la pantalla dejó de responder, recargá la página.';

    mostrarAvisoGlobal(mensaje, { etiqueta: 'Recargar', onClick: () => window.location.reload() });
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
