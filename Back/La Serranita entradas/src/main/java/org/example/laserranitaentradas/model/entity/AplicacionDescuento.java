package org.example.laserranitaentradas.model.entity;

/**
 * Cómo se usa el monto de un cupón. Con un porcentaje no cambia nada (el resultado es el mismo
 * por compra que por entrada); sólo importa para el monto.
 */
public enum AplicacionDescuento {
    /** El monto se resta una sola vez del total de la compra. */
    COMPRA,
    /** El monto se resta a cada entrada (sin dejarla por debajo de cero). */
    ENTRADA,
    /** El monto es el precio al que queda cada entrada: se descuenta la diferencia con su precio, y nunca se la encarece. */
    PRECIO_ENTRADA
}
