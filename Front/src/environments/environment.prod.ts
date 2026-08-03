/**
 * Configuración de producción. apiBase es relativo a propósito: el build de producción
 * está pensado para servirse detrás de un reverse proxy (ver Dockerfile del frontend)
 * que sirve los estáticos de Angular y reenvía /api al backend — así no hace falta
 * hornear ningún dominio específico en el build ni conocerlo de antemano.
 *
 * Si el backend se sirve en un dominio totalmente distinto (sin proxy delante), cambiar
 * esto por la URL absoluta correspondiente antes de compilar.
 */
export const environment = {
  apiBase: '/api',
};
