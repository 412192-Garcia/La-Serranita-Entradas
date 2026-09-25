package org.example.laserranitaentradas.service.impl;

import com.mercadopago.client.common.PhoneRequest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** El teléfono se escribe libre en el formulario; a Mercado Pago tiene que llegar partido bien. */
class MercadoPagoServiceImplTest {

    @ParameterizedTest(name = "\"{0}\" -> área {1}, número {2}")
    @CsvSource(delimiter = '|', value = {
            "351 5123456          | 351  | 5123456",
            "3515123456           | 351  | 5123456",
            "0351 15-512-3456     | 351  | 5123456",
            "351 15 5123456       | 351  | 5123456",
            "+54 9 351 512 3456   | 351  | 5123456",
            "11 2345-6789         | 11   | 23456789",
            "011 15 2345 6789     | 11   | 23456789",
            "3543 45-6789         | 3543 | 456789",
    })
    void numerosArgentinos_seParteEnCodigoDeAreaYNumero(String telefono, String area, String numero) {
        Optional<PhoneRequest> resultado = MercadoPagoServiceImpl.telefonoParaMercadoPago(telefono);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().getAreaCode()).isEqualTo(area);
        assertThat(resultado.get().getNumber()).isEqualTo(numero);
    }

    @ParameterizedTest
    @ValueSource(strings = {"5123456", "155123456", "+598 99 123 456", "hola", " "})
    void sinCodigoDeAreaDelExteriorOInvalido_noSeManda(String telefono) {
        // Antes que mandarle a MP un teléfono mal partido, no se manda.
        assertThat(MercadoPagoServiceImpl.telefonoParaMercadoPago(telefono)).isEmpty();
    }
}
