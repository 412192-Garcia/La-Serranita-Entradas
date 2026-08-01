package org.example.laserranitaentradas.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class DesgloseTipoEntradaDTO {
    private Long tipoEntradaId;
    private String nombre;
    /** Vendido como reserva anticipada (pagada online o a cobrar en caja), sin contar venta en puerta. */
    private long cantidadAnticipada;
    private BigDecimal montoAnticipada;
    /** Vendido directamente en la puerta (POS). */
    private long cantidadBoleteria;
    private BigDecimal montoBoleteria;
}
