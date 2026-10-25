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
 * Un usuario ya vio un aviso. Sólo importa con desapareceAlVerse=true. No extiende BaseEntity: no se
 * modifica.
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
