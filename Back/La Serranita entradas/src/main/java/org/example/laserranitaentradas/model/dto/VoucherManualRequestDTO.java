package org.example.laserranitaentradas.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.example.laserranitaentradas.model.entity.TipoVoucher;

import java.math.BigDecimal;

@Data
public class VoucherManualRequestDTO {
    private TipoVoucher tipo;

    @Schema(description = "EXTRA (artículos): qué es. CANTINA (monto): motivo, opcional")
    private String descripcion;

    @Schema(description = "EXTRA: cuántos vale")
    private Integer cantidad;

    @Schema(description = "CANTINA: cuánto vale")
    private BigDecimal monto;

    @Schema(description = "A nombre de quién va, opcional")
    private String titular;

    @Schema(description = "Ticketera; null = la única conectada")
    private String impresora;
}
