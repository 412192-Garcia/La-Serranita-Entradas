package org.example.laserranitaentradas.model.entity;

import lombok.Getter;

@Getter
public enum FormaPago {
    MERCADO_PAGO("Mercado Pago", "Pago digital a través de pasarela"),
    EFECTIVO_BOLETERIA("Efectivo en Boletería", "Reserva web para abonar en caja el día de la visita"),
    // Sólo en el POS: el boletero cobra con su posnet o QR y acá se registra.
    TARJETA("Tarjeta", "Débito o crédito cobrado en la boletería"),
    MERCADO_PAGO_QR("Mercado Pago QR", "El cliente escanea el QR y paga en la boletería"),
    // Sólo la crea un ADMIN: invitados o ventas por agencia cobradas por fuera.
    RESERVA_ADMIN("Reserva generada", "Reserva cargada a mano por un administrador, sin cobrar nada por acá — el cobro, si lo hay, se resolvió por fuera (ej. invitados, ventas por agencia)"),
    // Venta de puerta de $0. Suma 0 a la caja; las entradas cuentan igual.
    SIN_COBRO("Sin cobro", "Venta de puerta sin monto a cobrar (entradas bonificadas)");

    private final String descripcion;
    private final String detalle;

    FormaPago(String descripcion, String detalle) {
        this.descripcion = descripcion;
        this.detalle = detalle;
    }
}
