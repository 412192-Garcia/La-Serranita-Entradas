package org.example.laserranitaentradas.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * Casilla desde la que salen las facturas (ej. facturas@), separada de la de reservas y
 * comprobantes de compra (MAIL_RESERVAS_USERNAME, ej. reservas@). Es otra cuenta, con su propia contraseña
 * (MAIL_FACTURAS_USERNAME / MAIL_FACTURAS_PASSWORD); el servidor SMTP es el mismo salvo que se
 * indique otro.
 *
 * Sin MAIL_FACTURAS_USERNAME las facturas salen de la casilla general, como antes.
 *
 * No es un bean JavaMailSender a propósito: declarar uno apagaría el que Spring Boot arma solo
 * para la casilla general (se configura sólo si no hay ninguno).
 */
@Component
public class CasillaFacturas {

    private static final Logger log = LoggerFactory.getLogger(CasillaFacturas.class);

    private final JavaMailSender sender;
    private final String direccion;

    public CasillaFacturas(JavaMailSender general,
                           @Value("${spring.mail.username}") String direccionGeneral,
                           @Value("${app.mail.facturas.username:}") String usuario,
                           @Value("${app.mail.facturas.password:}") String password,
                           @Value("${app.mail.facturas.host:}") String host,
                           @Value("${app.mail.facturas.port:}") String puerto,
                           @Value("${spring.mail.host}") String hostGeneral,
                           @Value("${spring.mail.port}") int puertoGeneral) {
        if (usuario == null || usuario.isBlank()) {
            this.sender = general;
            this.direccion = direccionGeneral;
            return;
        }
        JavaMailSenderImpl propio = new JavaMailSenderImpl();
        // docker-compose pasa las variables sin cargar como texto vacío (y ahí Spring no usa el
        // valor por defecto): vacío = el mismo servidor que la casilla general.
        propio.setHost(host == null || host.isBlank() ? hostGeneral : host.trim());
        propio.setPort(puerto == null || puerto.isBlank() ? puertoGeneral : Integer.parseInt(puerto.trim()));
        propio.setUsername(usuario.trim());
        propio.setPassword(password);
        propio.setDefaultEncoding("UTF-8");
        Properties props = propio.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        this.sender = propio;
        this.direccion = usuario.trim();
        log.info("Las facturas se envían desde {}", this.direccion);
    }

    public JavaMailSender sender() {
        return sender;
    }

    /** Dirección del remitente (la cuenta con la que se autentica: el servidor no deja usar otra). */
    public String direccion() {
        return direccion;
    }
}
