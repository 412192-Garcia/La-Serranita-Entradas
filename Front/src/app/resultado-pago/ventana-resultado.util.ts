import { environment } from '../../environments/environment';

/**
 * Las pantallas de resultado del pago se abren donde las deje Mercado Pago: normalmente el
 * popup que abrió la compra, pero también una pestaña nueva (al volver desde la app de MP en
 * el celular) o un popup que perdió su vínculo con quien lo abrió. Por eso nunca navegan
 * dentro de la app (terminaría el calendario metido en el popup): intentan cerrarse, y si el
 * navegador no lo permite (sólo deja cerrar ventanas abiertas por script), van al sitio del parque.
 */
export function cerrarOVolverAlSitio(): void {
  window.close();
  setTimeout(() => {
    if (!window.closed && environment.urlSitio) {
      window.location.href = environment.urlSitio;
    }
  }, 300);
}

export function esVentanaDePago(): boolean {
  return !!window.opener && window.opener !== window;
}
