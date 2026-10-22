package org.example.laserranitaentradas.model.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class CambiarFechaRequest {
    /** El nuevo día de visita. */
    LocalDate fecha;
    /** Si es true, se le reenvía el comprobante al titular con la fecha nueva. */
    boolean avisarPorMail;
}
