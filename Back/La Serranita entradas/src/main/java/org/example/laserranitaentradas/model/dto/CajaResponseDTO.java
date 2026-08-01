package org.example.laserranitaentradas.model.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Sirve tanto para la caja abierta (montoContado/diferencia todavía null, los totales
 * son "hasta ahora") como para la caja recién cerrada (todo completo).
 */
@Data
@Builder
public class CajaResponseDTO {
    private Long id;
    private String estado;
    private LocalDateTime fechaApertura;
    private BigDecimal montoInicial;
    private LocalDateTime fechaCierre;
    private BigDecimal totalVentasEfectivo;
    private BigDecimal totalRetiros;
    private BigDecimal efectivoEsperado;
    private BigDecimal montoContado;
    private BigDecimal diferencia;
    private List<RetiroCajaResponseDTO> retiros;
}
