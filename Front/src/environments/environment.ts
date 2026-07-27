/**
 * Configuración dependiente del entorno.
 *
 * Es el único lugar donde se define la dirección del backend: al desplegar fuera
 * de la máquina de desarrollo se cambia acá y no en cada servicio, que era como
 * estaba antes (la URL estaba repetida en los 7 servicios, y olvidarse de uno
 * hacía que esa pantalla siguiera apuntando a localhost sin ningún error de build).
 */
export const environment = {
  apiBase: 'http://localhost:8080/api',
};
