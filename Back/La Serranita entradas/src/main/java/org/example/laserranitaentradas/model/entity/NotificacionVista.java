package org.example.laserranitaentradas.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Que un usuario ya vio un aviso puntual (tipo + referencia) del sistema de notificaciones
 * genérico. Sólo importa para los tipos con desapareceAlVerse=true (ver TipoNotificacion): para
 * esos, "verlo" apaga el aviso PARA ESE usuario — otros que no lo vieron lo siguen viendo. No
 * extiende BaseEntity: no hace falta auditar quién la creó (usuarioId ya lo dice) y nunca se
 * modifica después de creada.
 */
@Entity
@Table(name = "notificaciones_vistas")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(NotificacionVistaId.class)
public class NotificacionVista {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", length = 30)
    private TipoNotificacion tipo;

    @Id
    @Column(name = "ref_id")
    private Long refId;

    @Id
    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "visto_en", nullable = false)
    private LocalDateTime vistoEn;
}
