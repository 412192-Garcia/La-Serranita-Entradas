package org.example.laserranitaentradas.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;

@Data
@AllArgsConstructor
public class AfluenciaDiariaDTO {
    private LocalDate fecha;
    /** Pases reservados para ese día (fechaVisita). Sin regalos. */
    private long pasesVendidosAnticipada;
    /** Anticipadas y regalos que entraron ese día (fechaValidacion). */
    private long pasesValidadosAnticipada;
    /** Pases vendidos directamente en la puerta (POS) ese día: ingresan en el momento. */
    private long pasesVendidosBoleteria;
    /** Pases anticipados comprados ese día (fechaCreacion). */
    private long pasesCompradosAnticipada;
}
