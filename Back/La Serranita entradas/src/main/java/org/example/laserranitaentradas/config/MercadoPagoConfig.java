package org.example.laserranitaentradas.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Access token de Mercado Pago como bean. */
@Configuration
public class MercadoPagoConfig {

    @Value("${mercadopago.accessToken}")
    private String accessToken;

    @Bean
    public String mercadoPagoAccessToken() {
        return accessToken;
    }
}
