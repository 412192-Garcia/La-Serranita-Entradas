package org.example.laserranitaentradas.model.dto;

import lombok.Data;
import org.example.laserranitaentradas.model.entity.FormaPago;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CotizacionRequestDTO {
    FormaPago formaPago;
    List<DetalleCompraDTO> entradas;
    List<LineaArticuloPosDTO> articulos;
    Long promocionId;
    BigDecimal descuentoManualPorcentaje;
    BigDecimal descuentoManualMonto;
}
