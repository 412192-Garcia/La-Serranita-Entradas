package org.example.laserranitaentradas.model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** Clave de NotificacionVista. Tiene que coincidir con sus @Id (@IdClass). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificacionVistaId implements Serializable {
    private TipoNotificacion tipo;
    private Long refId;
    private Long usuarioId;
}
