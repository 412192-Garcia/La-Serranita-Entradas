import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';
import { registerLocaleData } from '@angular/common';
import localeEsAr from '@angular/common/locales/es-AR';
import { esRutaPublica } from './app/shared/rutas-publicas';
import { esDispositivoDelStaff } from './app/services/sesion.service';

registerLocaleData(localeEsAr, 'es-AR');

const MARCA_SW_QUITADO = 'serranita.swQuitado';

/** Visitantes que quedaron con el service worker de antes: se quita y se recarga una vez para que
 *  vean la versión actual. En equipos del staff se deja (el POS lo usa sin internet). */
async function quitarServiceWorkerViejo(): Promise<boolean> {
  if (!esRutaPublica(location.pathname) || !navigator.serviceWorker?.controller || esDispositivoDelStaff()) {
    return false;
  }
  try {
    if (sessionStorage.getItem(MARCA_SW_QUITADO)) return false;
    sessionStorage.setItem(MARCA_SW_QUITADO, '1');
    const registros = await navigator.serviceWorker.getRegistrations();
    await Promise.all(registros.map((r) => r.unregister()));
    const nombres = await caches.keys();
    await Promise.all(nombres.filter((c) => c.startsWith('ngsw:')).map((c) => caches.delete(c)));
    location.reload();
    return true;
  } catch {
    return false;
  }
}

quitarServiceWorkerViejo().then((recargando) => {
  if (recargando) return;
  bootstrapApplication(App, appConfig)
    .catch((err) => console.error(err));
});
