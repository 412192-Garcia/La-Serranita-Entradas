package org.example.laserranitaentradas.model.dto;

import lombok.Builder;
import lombok.Data;
import org.example.laserranitaentradas.model.entity.TipoVoucher;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class VoucherResponseDTO {
    private Long id;
    private String codigo;
    private TipoVoucher tipo;
    private String descripcion;
    private Integer cantidad;
    private BigDecimal monto;
    private LocalDateTime fechaEmision;
    private boolean anulado;
}
