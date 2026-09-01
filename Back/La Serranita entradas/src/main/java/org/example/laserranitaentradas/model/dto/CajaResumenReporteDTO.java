package org.example.laserranitaentradas.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Una caja (turno) ya cerrada, para que el admin audite faltantes/sobrantes por boletero. */
@Data
@AllArgsConstructor
public class CajaResumenReporteDTO {
    private Long id;
    private String usuarioNombre;
    private LocalDateTime fechaApertura;
    private LocalDateTime fechaCierre;
    private BigDecimal montoInicial;
    private BigDecimal totalRetiros;
    private BigDecimal montoEsperado;
    private BigDecimal montoContado;
    /** montoContado − montoEsperado: positivo es sobrante, negativo es faltante. Sólo efectivo. */
    private BigDecimal diferencia;
    /** Diferencia combinada de Tarjeta + QR: lo cerrado en el/los posnet − lo vendido con tarjeta y QR
     * (con ajustes). Positivo es sobrante, negativo es faltante. 0 si esta caja no tuvo nada de posnet. */
    private BigDecimal diferenciaPosnet;
}
