package org.example.laserranitaentradas.model.entity;

import lombok.Getter;

@Getter
public enum FormaPago {
    MERCADO_PAGO("Mercado Pago", "Pago digital a través de pasarela"),
    EFECTIVO_BOLETERIA("Efectivo en Boletería", "Reserva web para abonar en caja el día de la visita");

    private final String descripcion;
    private final String detalle;

    FormaPago(String descripcion, String detalle) {
        this.descripcion = descripcion;
        this.detalle = detalle;
    }
}
