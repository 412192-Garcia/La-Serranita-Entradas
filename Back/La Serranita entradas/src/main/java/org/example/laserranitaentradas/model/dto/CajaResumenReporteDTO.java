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
    /** Tarjeta + QR: cerrado en posnet − vendido, con ajustes. Positivo = sobró. */
    private BigDecimal diferenciaPosnet;
    /** true = se abrió con "vender sin control de caja": diferencia $0 siempre, no es un cierre real. */
    private Boolean controlOmitido;
}
