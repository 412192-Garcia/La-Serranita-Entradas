package org.example.laserranitaentradas.model.entity;

/** Cómo se usa el monto de un cupón. Con porcentaje da lo mismo. */
public enum AplicacionDescuento {
    /** El monto se resta una sola vez del total de la compra. */
    COMPRA,
    /** El monto se resta a cada entrada (sin dejarla por debajo de cero). */
    ENTRADA,
    /** El monto es el precio final de cada entrada; nunca la encarece. */
    PRECIO_ENTRADA
}
