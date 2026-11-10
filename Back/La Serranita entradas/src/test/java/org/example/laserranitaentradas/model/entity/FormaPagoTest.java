package org.example.laserranitaentradas.model.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormaPagoTest {

    @Test
    void efectivoSoloMantieneElPrecioDeEfectivo() {
        assertEquals(FormaPago.EFECTIVO_BOLETERIA, FormaPago.paraPrecio(FormaPago.EFECTIVO_BOLETERIA, null));
    }

    @Test
    void efectivoMasOtraFormaNoUsaPrecioDeEfectivo() {
        assertEquals(FormaPago.TARJETA, FormaPago.paraPrecio(FormaPago.EFECTIVO_BOLETERIA, FormaPago.TARJETA));
        assertEquals(FormaPago.MERCADO_PAGO_QR, FormaPago.paraPrecio(FormaPago.EFECTIVO_BOLETERIA, FormaPago.MERCADO_PAGO_QR));
    }

    @Test
    void otraFormaMasEfectivoSigueSinPrecioDeEfectivo() {
        assertEquals(FormaPago.TARJETA, FormaPago.paraPrecio(FormaPago.TARJETA, FormaPago.EFECTIVO_BOLETERIA));
    }
}
