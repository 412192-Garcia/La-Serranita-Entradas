package org.example.laserranitaentradas.model.dto;

import lombok.Data;

@Data
public class ResolverRechazoRequestDTO {
    /** Opcional: qué se hizo para resolverlo (ej. "le avisé al boletero, cargó de nuevo bien"). */
    private String nota;
}
