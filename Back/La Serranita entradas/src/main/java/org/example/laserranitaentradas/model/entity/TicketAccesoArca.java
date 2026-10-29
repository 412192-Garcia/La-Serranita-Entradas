package org.example.laserranitaentradas.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Último ticket de WSAA de cada servicio. En la base porque WSAA no da otro mientras siga vigente (12 h). */
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
