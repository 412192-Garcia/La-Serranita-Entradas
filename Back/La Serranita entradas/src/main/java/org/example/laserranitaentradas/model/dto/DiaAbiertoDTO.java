package org.example.laserranitaentradas.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalTime;

/** Día abierto del calendario público, con el horario que rige ese día. */
@Data
@AllArgsConstructor
public class DiaAbiertoDTO {
    /** YYYY-MM-DD. */
    private String fecha;
    private LocalTime horaApertura;
    private LocalTime horaCierre;
    /** true si el día tiene un horario distinto al general. */
    private boolean especial;
}
