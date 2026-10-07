package org.example.laserranitaentradas.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Un error del servidor (un log.error de cualquier parte de la app, también de los procesos en
 * segundo plano), para verlo desde Sistema > Errores sin entrar a los logs del contenedor. Los que
 * se repiten se agrupan en el mismo registro (misma firma, sin resolver): cantidad y última vez.
 *
 * area y nivel son texto y no enums a propósito: con ddl-auto, un valor nuevo de enum choca con el
 * CHECK que Hibernate creó con la lista vieja.
 */
@Entity
@Table(name = "incidentes", indexes = {
        @Index(name = "ix_incidente_firma", columnList = "firma"),
        @Index(name = "ix_incidente_resuelto", columnList = "resuelto, ultima_vez")
})
@Getter
@Setter
@NoArgsConstructor
public class Incidente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** PAGOS, FACTURACION, IMPRESION, MAILS o SISTEMA (ver IncidenteService#area). */
    @Column(nullable = false, length = 20)
    private String area;

    /** ERROR, o WARN para las alertas explícitas (loggers "alertas.*"). */
    @Column(nullable = false, length = 10)
    private String nivel;

    /** Hash de lo que hace "el mismo error" (origen + mensaje normalizado + excepción + request). */
    @Column(nullable = false, length = 64)
    private String firma;

    @Column(length = 1000)
    private String mensaje;

    /** Stack trace (recortado). */
    @Column(columnDefinition = "text")
    private String detalle;

    /** Logger que lo registró (la clase). */
    @Column(length = 200)
    private String origen;

    /** Código que se le mostró al usuario (errores de una request), para encontrarlo si lo reporta. */
    @Column(length = 20)
    private String codigo;

    @Column(length = 100)
    private String usuario;

    /** "POST /api/interno/..." si pasó atendiendo una request. */
    @Column(length = 300)
    private String request;

    @Column(name = "primera_vez", nullable = false)
    private LocalDateTime primeraVez;

    @Column(name = "ultima_vez", nullable = false)
    private LocalDateTime ultimaVez;

    @Column(nullable = false)
    private int cantidad;

    @Column(nullable = false)
    private boolean resuelto;

    @Column(name = "resuelto_por", length = 100)
    private String resueltoPor;

    @Column(name = "resuelto_en")
    private LocalDateTime resueltoEn;
}
