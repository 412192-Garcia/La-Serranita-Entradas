package org.example.laserranitaentradas.model.entity;

import lombok.Getter;

/**
 * Partes del sistema que se pueden apagar desde Sistema > Módulos sin tocar el servidor. Se
 * guarda por nombre en {@link ModuloSistema} (columna de texto, no enum de base): sumar uno acá
 * no necesita ajustar ningún CHECK en las bases existentes.
 */
@Getter
public enum Modulo {
    FACTURACION("Facturación",
            "Facturas electrónicas de ARCA: en el POS (imprimir o mandar por mail), las compras online, la facturación manual y en lote. Apagado, se vende igual pero sin factura; las facturas pendientes quedan esperando hasta que se vuelva a prender.",
            true),
    POINT("Cobro con Point",
            "Los cobros con tarjeta del POS se mandan al Point de Mercado Pago de cada tablet y la venta se registra recién cuando el pago queda aprobado. Apagado, la tarjeta se cobra en el posnet y se anota a mano, como siempre.",
            false);

    private final String nombre;
    private final String descripcion;
    /** Cómo arranca si nunca se tocó: la facturación ya existía prendida, el Point es nuevo. */
    private final boolean activoPorDefecto;

    Modulo(String nombre, String descripcion, boolean activoPorDefecto) {
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.activoPorDefecto = activoPorDefecto;
    }
}
