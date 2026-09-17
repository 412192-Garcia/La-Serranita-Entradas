package org.example.laserranitaentradas.model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** Clave compuesta de NotificacionVista: qué tipo de aviso, sobre qué entidad (refId), visto por
 * qué usuario. Los nombres y tipos de estos campos tienen que coincidir exactamente con los @Id
 * de NotificacionVista (así lo exige @IdClass). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificacionVistaId implements Serializable {
    private TipoNotificacion tipo;
    private Long refId;
    private Long usuarioId;
}
