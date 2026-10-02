package org.example.laserranitaentradas.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CasillaFacturasTest {

    private final JavaMailSender general = mock(JavaMailSender.class);

    @Test
    void sinCasillaPropia_usaLaGeneral() {
        CasillaFacturas c = new CasillaFacturas(general, "reservas@parque.com", "", "", "", "", "smtp.hostinger.com", 587);

        assertThat(c.sender()).isSameAs(general);
        assertThat(c.direccion()).isEqualTo("reservas@parque.com");
    }

    @Test
    void conCasillaPropia_otraCuentaEnElMismoServidor() {
        CasillaFacturas c = new CasillaFacturas(general, "reservas@parque.com", " facturas@parque.com ", "secreta", "", "", "smtp.hostinger.com", 587);

        assertThat(c.direccion()).isEqualTo("facturas@parque.com");
        JavaMailSenderImpl s = (JavaMailSenderImpl) c.sender();
        assertThat(s.getUsername()).isEqualTo("facturas@parque.com");
        assertThat(s.getHost()).isEqualTo("smtp.hostinger.com");
        assertThat(s.getPort()).isEqualTo(587);
    }

    @Test
    void conCasillaEnOtroServidor() {
        CasillaFacturas c = new CasillaFacturas(general, "reservas@parque.com", "facturas@otro.com", "x", "smtp.otro.com", "465", "smtp.hostinger.com", 587);

        JavaMailSenderImpl s = (JavaMailSenderImpl) c.sender();
        assertThat(s.getHost()).isEqualTo("smtp.otro.com");
        assertThat(s.getPort()).isEqualTo(465);
    }
}
