package org.example.laserranitaentradas.model.dto;

import lombok.Data;

import java.util.List;

/** Validación de una anticipada. Sin cuerpo = vinieron todos. */
@Data
public class ValidarIngresoRequestDTO {
    /** Entradas cuyo titular no vino, por línea de la compra (sólo pagadas online). */
    private List<Ausente> ausentes;
    /** Ticketera para los vouchers. Null = la única conectada. */
    private String impresora;

    @Data
    public static class Ausente {
        private Long detalleId;
        private Integer cantidad;
    }
}
