package org.example.laserranitaentradas.model.entity;

import lombok.Getter;

@Getter
public enum FormaPago {
    MERCADO_PAGO("Mercado Pago", "Pago digital a través de pasarela"),
    EFECTIVO_BOLETERIA("Efectivo en Boletería", "Reserva web para abonar en caja el día de la visita"),
    // Sólo se usan en la venta presencial (POS): el cobro lo hace el boletero con su
    // propio posnet o QR y acá se registra, no hay integración con la pasarela.
    TARJETA("Tarjeta", "Débito o crédito cobrado en la boletería"),
    MERCADO_PAGO_QR("Mercado Pago QR", "El cliente escanea el QR y paga en la boletería"),
    // Sólo la crea un ADMIN a mano (ver CompraInternoController): nunca es una opción que
    // el cliente pueda elegir en el storefront público. No implica que sea gratis "porque sí":
    // cubre tanto invitados como ventas por agencia, donde el cobro real se hizo por fuera
    // del sistema (por eso el nombre neutro, sin decir "regalo").
    RESERVA_ADMIN("Reserva generada (admin)", "Reserva cargada a mano por un administrador, sin cobrar nada por acá — el cobro, si lo hay, se resolvió por fuera (ej. invitados, ventas por agencia)");

    private final String descripcion;
    private final String detalle;

    FormaPago(String descripcion, String detalle) {
        this.descripcion = descripcion;
        this.detalle = detalle;
    }
}
