package org.example.laserranitaentradas.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Tramo del histograma: días entre la compra y el uso de las anticipadas del rango. */
@Data
@AllArgsConstructor
public class AnticipacionCompraDTO {
    /** "Mismo día", "1 a 2 días", etc. */
    private String etiqueta;
    private long cantidad;
}
