package org.example.laserranitaentradas.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Historial de acciones: quién hizo qué y cuándo (Sistema > Historial de acciones). Se registra
 * solo, para toda operación que modifica algo hecha por un usuario logueado y para los inicios de
 * sesión (ver AuditoriaFilter); en lo crítico el servicio suma el antes y el después
 * (ver AuditoriaContexto).
 *
 * accion es texto y no enum a propósito (ver Incidente).
 */
@Entity
@Table(name = "auditoria", indexes = {
        @Index(name = "ix_auditoria_fecha", columnList = "fecha"),
        @Index(name = "ix_auditoria_usuario", columnList = "usuario"),
        @Index(name = "ix_auditoria_accion", columnList = "accion")
})
@Getter
@Setter
@NoArgsConstructor
public class RegistroAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Hora de Argentina. */
    @Column(nullable = false)
    private LocalDateTime fecha;

    /** Username (o el que se intentó usar, en un login fallido). */
    @Column(length = 100)
    private String usuario;

    @Column(length = 20)
    private String rol;

    /** Código estable de la acción (ej. VENTA_CANCELADA), para filtrar. */
    @Column(nullable = false, length = 60)
    private String accion;

    /** Para leer: "Canceló la venta #261005-3". */
    @Column(nullable = false, length = 300)
    private String descripcion;

    @Column(length = 60)
    private String entidad;

    @Column(name = "entidad_id", length = 60)
    private String entidadId;

    /** Antes → después, o un resumen de los datos enviados (contraseñas y tokens tapados). */
    @Column(columnDefinition = "text")
    private String detalle;

    /** Código HTTP con que terminó (200 = se hizo; 4xx/5xx = se intentó y falló). */
    @Column(nullable = false)
    private int resultado;

    @Column(length = 250)
    private String ruta;

    @Column(length = 60)
    private String ip;
}
