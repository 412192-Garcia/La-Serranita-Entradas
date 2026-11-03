/**
 * Configuración dependiente del entorno.
 *
 * Es el único lugar donde se define la dirección del backend: al desplegar fuera
 * de la máquina de desarrollo se cambia acá y no en cada servicio, que era como
 * estaba antes (la URL estaba repetida en los 7 servicios, y olvidarse de uno
 * hacía que esa pantalla siguiera apuntando a localhost sin ningún error de build).
 */
// TEMPORAL, prueba por wifi: por http y con IP el navegador no ofrece crypto.randomUUID. Revertir junto con apiBase.
if (!crypto.randomUUID) {
  crypto.randomUUID = () =>
    '10000000-1000-4000-8000-100000000000'.replace(/[018]/g, (c) =>
      (Number(c) ^ (crypto.getRandomValues(new Uint8Array(1))[0] & (15 >> (Number(c) / 4)))).toString(16),
    ) as ReturnType<Crypto['randomUUID']>;
}

export const environment = {
  apiBase: 'http://192.168.0.186:8080/api',
  // Vacío en dev a propósito: las compras de prueba no tienen que contar como conversiones.
  urlGracias: '',
  urlSitio: '',
};
