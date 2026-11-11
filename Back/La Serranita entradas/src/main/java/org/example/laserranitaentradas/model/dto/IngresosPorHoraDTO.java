package org.example.laserranitaentradas.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Gente que entró al parque en cada hora (0-23), por hora de validación. Sin ausentes. */
@Data
@AllArgsConstructor
public class IngresosPorHoraDTO {
    private int hora;
    private long cantidadCompras;
    private long cantidadEntradas;
}
