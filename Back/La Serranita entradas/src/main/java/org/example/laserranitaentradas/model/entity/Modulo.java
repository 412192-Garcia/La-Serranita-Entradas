package org.example.laserranitaentradas.model.entity;

import lombok.Getter;

/**
 * Partes que se apagan desde Sistema > Módulos. Se guarda por nombre (texto) para no chocar con un CHECK. Un
 * submódulo sólo anda si su padre está prendido.
 */
@Getter
public enum Modulo {
    FACTURACION("Facturación",
            "Facturas electrónicas de ARCA. Apagado, se vende igual pero sin factura y no sale ninguna (tampoco la facturación manual ni en lote); las pendientes quedan esperando hasta que se vuelva a prender.",
            true, null),
    FACTURACION_BOLETERIA("Boletería",
            "Facturas de las ventas del POS (imprimir en la ticketera o mandar por mail). Apagado, en el POS no aparece la opción de facturar.",
            true, "FACTURACION"),
    FACTURACION_ONLINE("Compras online",
            "Las compras pagadas con Mercado Pago en la web se facturan solas y la factura va por mail. Apagado, se siguen vendiendo sin factura (se pueden facturar después a mano desde la compra).",
            true, "FACTURACION"),
    RESERVA_EFECTIVO("Reservas a pagar en el parque",
            "En la compra online aparece la opción \"Reservar y pagar en el parque\". Apagado, sólo se puede comprar pagando online; las reservas ya hechas se cobran igual en la boletería.",
            true, null),
    POINT("Cobro con Point",
            "Los cobros con tarjeta del POS se mandan al Point de Mercado Pago de cada tablet y la venta se registra recién cuando el pago queda aprobado. Apagado, la tarjeta se cobra en el posnet y se anota a mano, como siempre.",
            false, null);

    private final String nombre;
    private final String descripcion;
    /** Cómo arranca si nunca se tocó: la facturación ya existía prendida, el Point es nuevo. */
    private final boolean activoPorDefecto;
    /**
     * Módulo padre (null = principal). Texto porque un enum no puede referirse a sus constantes en el
     * constructor.
     */
    private final String codigoPadre;

    Modulo(String nombre, String descripcion, boolean activoPorDefecto, String codigoPadre) {
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.activoPorDefecto = activoPorDefecto;
        this.codigoPadre = codigoPadre;
    }

    public Modulo getPadre() {
        return codigoPadre == null ? null : Modulo.valueOf(codigoPadre);
    }
}
