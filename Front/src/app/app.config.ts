import { ApplicationConfig, ErrorHandler, LOCALE_ID, provideBrowserGlobalErrorListeners, isDevMode } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';

import { routes } from './app.routes';
import { authInterceptor } from './interceptors/auth.interceptor';
import { GlobalErrorHandler } from './global-error-handler';
import { provideServiceWorker } from '@angular/service-worker';
import { esRutaPublica } from './shared/rutas-publicas';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withFetch(), withInterceptors([authInterceptor])),
    { provide: LOCALE_ID, useValue: 'es-AR' },
    { provide: ErrorHandler, useClass: GlobalErrorHandler },
    // Sólo para el staff (POS sin internet). En la compra pública el visitante siempre carga
    // la versión actual y no se baja la app interna.
    provideServiceWorker('ngsw-worker.js', {
      enabled: !isDevMode() && !esRutaPublica(location.pathname),
      registrationStrategy: 'registerWhenStable:30000',
    }),
  ],
};
