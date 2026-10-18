package org.example.laserranitaentradas.service.point;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class MercadoPagoPointClientTest {

    @Test
    void montoRechazadoPorMpSeExplicaEnCastellano() {
        String cuerpo = "{\"errors\":[{\"code\":\"property_value\",\"message\":\"The value of the provided property is invalid\","
                + "\"details\":[\"transactions.payments[0].amount must be greater than or equal to 5\"]}]}";
        assertThat(MercadoPagoPointClient.mensajeDe(HttpStatus.BAD_REQUEST, cuerpo))
                .contains("no acepta cobrar ese monto");
    }

    @Test
    void otrosErroresIncluyenLosDetails() {
        String cuerpo = "{\"errors\":[{\"code\":\"invalid_properties\",\"message\":\"Invalid properties\","
                + "\"details\":[\"config.point.terminal_id is required\"]}]}";
        assertThat(MercadoPagoPointClient.mensajeDe(HttpStatus.BAD_REQUEST, cuerpo))
                .isEqualTo("Invalid properties: config.point.terminal_id is required (invalid_properties)");
    }

    @Test
    void sinDetailsQuedaComoAntes() {
        assertThat(MercadoPagoPointClient.mensajeDe(HttpStatus.BAD_REQUEST, "{\"message\":\"Bad request\",\"error\":\"bad_request\"}"))
                .isEqualTo("Bad request (bad_request)");
    }
}
