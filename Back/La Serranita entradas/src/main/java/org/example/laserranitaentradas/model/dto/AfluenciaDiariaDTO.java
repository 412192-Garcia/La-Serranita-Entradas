package org.example.laserranitaentradas.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;

@Data
@AllArgsConstructor
public class AfluenciaDiariaDTO {
    private LocalDate fecha;
    /** Pases de tipo ENTRADA de reservas anticipadas (aprobadas, reservadas en efectivo o ya usadas), sin contar venta en puerta. */
    private long pasesVendidosAnticipada;
    /** De esas reservas anticipadas, cuántas ya se validaron por DNI en boletería al llegar. */
    private long pasesValidadosAnticipada;
    /** Pases vendidos directamente en la puerta (POS): siempre ingresan en el momento, no tienen validación separada. */
    private long pasesVendidosBoleteria;
}
