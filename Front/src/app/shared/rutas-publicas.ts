/** Rutas de la compra pública: lo que se embebe en el sitio del parque. */
export const RUTAS_PUBLICAS = ['/entradas', '/pago-exitoso', '/pago-fallido'];

export function esRutaPublica(url: string): boolean {
  return RUTAS_PUBLICAS.some((ruta) => url.startsWith(ruta));
}
