package org.example.laserranitaentradas.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalTime;

/**
 * Horario general público (sin login): sólo apertura, cierre y límite de compra, nunca la entidad completa.
 */
@Data
@AllArgsConstructor
public class HorarioResponseDTO {
    private LocalTime horaApertura;
    private LocalTime horaCierre;
    /** Minutos antes del cierre en que se corta la compra online para el mismo día. */
    private Integer minutosLimiteCompra;
    /** true si es el horario especial de un día puntual y no el general. */
    private boolean especial;
}
