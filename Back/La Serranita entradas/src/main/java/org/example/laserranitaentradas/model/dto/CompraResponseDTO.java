package org.example.laserranitaentradas.model.dto;

import lombok.Data;
import org.example.laserranitaentradas.model.entity.FormaPago;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CompraResponseDTO {
    private Long id;
    /** Código visible para el cliente/boletería: yyMMdd-N. */
    private String codigoReserva;
    private ClienteResponseDTO cliente;
    private String contactEmail;
    private String contactPhone;
    private LocalDate fechaVisita;
    private BigDecimal montoTotal;
    private BigDecimal descuentoAplicado;
    private String estado;
    private String cuponCodigo;
    private List<CompraDetalleResponseDTO> detalles;

    /** Momento exacto del check-in en boletería (null si todavía no se validó). */
    private LocalDateTime fechaValidacion;

    /** Usuario de boletería que validó el ingreso (null si todavía no se validó). */
    private String usuarioValidador;

    private FormaPago formaPago;

    private String preferenceId;
    private String initPoint;
}
