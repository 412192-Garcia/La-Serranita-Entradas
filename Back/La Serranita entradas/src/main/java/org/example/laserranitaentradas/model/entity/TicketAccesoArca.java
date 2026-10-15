package org.example.laserranitaentradas.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Último ticket de acceso (TA) de WSAA de cada web service de ARCA (ver ArcaClient). En la base y
 * no sólo en memoria porque WSAA no entrega otro mientras el anterior siga vigente (dura 12 h): si
 * se perdiera en un reinicio o un deploy, la facturación quedaría trabada hasta que venza.
 */
@Entity
@Table(name = "tickets_acceso_arca")
@Getter
@Setter
@NoArgsConstructor
public class TicketAccesoArca {

    /** Ambiente y web service: "prod:wsfe", "dev:ws_sr_constancia_inscripcion"... */
    @Id
    @Column(length = 80)
    private String servicio;

    /** Número de serie del certificado con el que se pidió: si se cambia el certificado, no sirve. */
    @Column(nullable = false, length = 80)
    private String certificado;

    @Column(nullable = false, columnDefinition = "text")
    private String token;

    @Column(nullable = false, columnDefinition = "text")
    private String sign;

    /** Hasta cuándo vale, en hora de Argentina. */
    @Column(nullable = false)
    private LocalDateTime vence;
}
