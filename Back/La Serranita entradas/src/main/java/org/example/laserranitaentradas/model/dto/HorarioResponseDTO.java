package org.example.laserranitaentradas.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalTime;

/** Horario general del parque para consumo público (GET /api/configuracion/horario, sin
 * autenticación): sólo horaApertura/horaCierre — nunca el ConfiguracionParque completo, que
 * hereda de BaseEntity y expondría usuarioCreacion/usuarioModificacion (nombres de admin) a
 * cualquier visitante anónimo. */
@Data
@AllArgsConstructor
public class HorarioResponseDTO {
    private LocalTime horaApertura;
    private LocalTime horaCierre;
}
