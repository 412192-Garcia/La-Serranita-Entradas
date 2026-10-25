package org.example.laserranitaentradas.model.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CotizacionResponseDTO {
    private BigDecimal subtotal;
    private BigDecimal ahorro;
    /** Descuento del cupón sobre las entradas; 0 si no vino cupón o no aplica. */
    private BigDecimal descuentoCupon = BigDecimal.ZERO;
    /** Por qué el cupón no aplica (cantidad mínima, tipo de entrada, todavía no vigente...), o null. */
    private String avisoCupon;
    /** El cupón existe y está vigente (aunque no descuente con estas entradas). */
    private boolean cuponValido;
}
