package org.example.laserranitaentradas.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;

@Data
@AllArgsConstructor
public class AfluenciaDiariaDTO {
    private LocalDate fecha;
    /** Pases de tipo ENTRADA vendidos ese día (compras aprobadas, reservadas en efectivo o ya usadas). */
    private long pasesVendidos;
    /** De esos, cuántos ya se validaron por DNI en boletería. */
    private long pasesValidados;
}
