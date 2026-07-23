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
     * Calcula el monto total ahorrado respecto al precio de lista.
     */
    BigDecimal calcularAhorro(TipoEntrada tipoEntrada, int cantidad, FormaPago formaPago);
}
