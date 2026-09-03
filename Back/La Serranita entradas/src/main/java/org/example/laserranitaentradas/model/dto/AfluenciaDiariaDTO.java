package org.example.laserranitaentradas.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;

@Data
@AllArgsConstructor
public class AfluenciaDiariaDTO {
    private LocalDate fecha;
    /** Demanda: pases de entrada reservados para venir ESE día (por fechaVisita), estén validados o no.
     * No incluye regalos (no tienen día elegido). */
    private long pasesVendidosAnticipada;
    /** Ingreso real de anticipadas y regalos ESE día (por fechaValidacion, el día que la persona
     * cruzó la puerta — puede no coincidir con el día que había reservado). */
    private long pasesValidadosAnticipada;
    /** Pases vendidos directamente en la puerta (POS) ese día: ingresan en el momento. */
    private long pasesVendidosBoleteria;
}
