package org.example.laserranitaentradas.model.dto;

import lombok.Data;
import org.example.laserranitaentradas.model.entity.FormaPago;

import java.math.BigDecimal;

@Data
public class CierrePosnetRequestDTO {
    private FormaPago formaPago;
    private BigDecimal monto;
    private String nota;
}
