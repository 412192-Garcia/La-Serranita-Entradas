package org.example.laserranitaentradas.model.entity;

/**
 * Qué se hace con la factura una vez emitida. Es la opción que elige el boletero en el POS
 * ("Imprimir factura" / "Enviar por mail"): una u otra, nunca las dos.
 *
 * "Enviar por mail" con el campo de email vacío significa NO facturar: en ese caso ni siquiera
 * se crea una Factura (ver FacturaService#solicitar), así que este enum sólo modela los dos
 * casos que sí emiten.
 */
public enum DestinoFactura {
    IMPRIMIR,
    MAIL,
    /** Sin envío automático: notas de crédito y refacturaciones hechas por un admin desde la
     * oficina (imprimirlas solas en la boletería no tendría sentido). Quedan en PDF. */
    NINGUNO
}
