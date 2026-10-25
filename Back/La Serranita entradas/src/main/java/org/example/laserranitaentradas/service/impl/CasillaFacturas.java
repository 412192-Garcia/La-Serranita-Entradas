package org.example.laserranitaentradas.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * Casilla de las facturas (MAIL_FACTURAS_USERNAME / MAIL_FACTURAS_PASSWORD), aparte de la de reservas. Sin
 * ella salen de la general.
 *
 * No es un bean JavaMailSender: apagaría el que Spring Boot arma para la casilla general.
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
        // docker-compose pasa vacío lo no cargado: vacío = el servidor de la general.
        propio.setHost(host == null || host.isBlank() ? hostGeneral : host.trim());
        propio.setPort(puerto == null || puerto.isBlank() ? puertoGeneral : Integer.parseInt(puerto.trim()));
        propio.setUsername(usuario.trim());
        propio.setPassword(password);
        propio.setDefaultEncoding("UTF-8");
        Properties props = propio.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        if (propio.getPort() == 465) {
            props.put("mail.smtp.ssl.enable", "true");      // 465: TLS desde el arranque
        } else {
            props.put("mail.smtp.starttls.enable", "true"); // 587: arranca en claro y pasa a TLS
        }
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
