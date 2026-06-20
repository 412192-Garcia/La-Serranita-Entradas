package org.example.laserranitaentradas.model.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class CompraResponseDTO {
    private Long id;
    private ClienteResponseDTO cliente;
    private String contactEmail;
    private String contactPhone;
    private LocalDate fechaVisita;
    private BigDecimal montoTotal;
    private BigDecimal descuentoAplicado;
    private String estado;
    private String cuponCodigo;
    private List<CompraDetalleResponseDTO> detalles;
}
