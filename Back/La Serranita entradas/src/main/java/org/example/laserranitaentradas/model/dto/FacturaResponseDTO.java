package org.example.laserranitaentradas.model.dto;

import lombok.Builder;
import lombok.Data;
import org.example.laserranitaentradas.model.entity.DestinoFactura;
import org.example.laserranitaentradas.model.entity.EstadoFactura;
import org.example.laserranitaentradas.model.entity.EstadoTrabajoImpresion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class FacturaResponseDTO {
    private Long id;
    private Long compraId;
    private EstadoFactura estado;
    private DestinoFactura destino;
    private String email;
    private Integer puntoVenta;
    private Integer tipoComprobante;
    private Long numero;
    private String cae;
    private LocalDate caeVencimiento;
    private LocalDate fechaEmision;
    private BigDecimal importeTotal;
    private BigDecimal importeNeto;
    private BigDecimal importeIva;
    private Integer intentos;
    private String ultimoError;
    private LocalDateTime mailEnviadoEn;
    /** Estado del último trabajo de impresión de esta factura; null si nunca se mandó a imprimir. */
    private EstadoTrabajoImpresion impresionEstado;
    private String impresionError;
    /** URL del QR de ARCA (RG 4892). Sólo cuando está EMITIDA. */
    private String qrUrl;
}
