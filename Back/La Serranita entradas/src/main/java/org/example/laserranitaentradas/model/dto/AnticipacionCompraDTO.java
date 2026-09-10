package org.example.laserranitaentradas.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Un tramo del histograma de anticipación: con cuántos días de antelación se compraron las
 * anticipadas que se usaron en el rango (fechaValidacion − fechaCreacion). Ayuda a ver si la
 * gente reserva con tiempo o compra sobre la fecha.
 */
@Data
@AllArgsConstructor
public class AnticipacionCompraDTO {
    /** "Mismo día", "1 a 2 días", etc. */
    private String etiqueta;
    private long cantidad;
}
