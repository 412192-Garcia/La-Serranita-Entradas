package org.example.laserranitaentradas.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración para Mercado Pago.
 * Provee el access token como bean para inyectarlo donde se necesite.
 * Si prefieres inicializar el SDK directamente, puedes hacerlo en un bean @PostConstruct
 * usando com.mercadopago.MercadoPagoConfig.setAccessToken(...)
 */
@Configuration
public class MercadoPagoConfig {

    @Value("${mercadopago.accessToken:}")
    private String accessToken;

    @Bean
    public String mercadoPagoAccessToken() {
        return accessToken;
    }
}

