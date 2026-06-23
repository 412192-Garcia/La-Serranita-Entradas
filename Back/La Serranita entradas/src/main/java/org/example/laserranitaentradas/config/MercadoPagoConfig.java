package org.example.laserranitaentradas.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuración para Mercado Pago.
 */
@Configuration
public class MercadoPagoConfig {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoConfig.class);

    @Value("${mercadopago.accessToken:}")
    private String accessToken;

    @Bean
    public String mercadoPagoAccessToken() {
        return accessToken;
    }

    @PostConstruct
    public void initSdk() {
        if (accessToken == null || accessToken.isBlank()) {
            log.warn("No se ha configurado 'mercadopago.accessToken'. Mercado Pago SDK no será inicializado.");
            return;
        }

        try {
            // Usar el nombre totalmente calificado para evitar colisión con esta clase
            com.mercadopago.MercadoPagoConfig.setAccessToken(accessToken);
            log.info("Mercado Pago SDK inicializado correctamente.");
        } catch (Throwable e) {
            log.error("Error al inicializar Mercado Pago SDK", e);
        }
    }
}
