package org.example.laserranitaentradas.service;

import org.example.laserranitaentradas.model.entity.FormaPago;
import org.example.laserranitaentradas.model.entity.TipoEntrada;

import java.math.BigDecimal;

public interface CalculoPrecioService {
    /**
     * Calcula el monto total aplicando promociones por forma de pago si corresponden.
     */
    BigDecimal calcularTotal(TipoEntrada tipoEntrada, int cantidad, FormaPago formaPago);

    /**
     * Igual que la anterior, pero partiendo de un precio de lista dado en vez del actual del
     * tipo: para cobrar una reserva al precio que tenía cuando se reservó, aunque el tipo haya
     * subido después. Los escalones por grupo (efectivo) son importes absolutos, así que no
     * dependen del precio de lista.
     */
    BigDecimal calcularTotal(TipoEntrada tipoEntrada, int cantidad, FormaPago formaPago, BigDecimal precioLista);

    /**
     * Calcula el monto total ahorrado respecto al precio de lista.
     */
    BigDecimal calcularAhorro(TipoEntrada tipoEntrada, int cantidad, FormaPago formaPago);

    /** Ahorro respecto de un precio de lista dado (ver calcularTotal con precioLista). */
    BigDecimal calcularAhorro(TipoEntrada tipoEntrada, int cantidad, FormaPago formaPago, BigDecimal precioLista);
}
