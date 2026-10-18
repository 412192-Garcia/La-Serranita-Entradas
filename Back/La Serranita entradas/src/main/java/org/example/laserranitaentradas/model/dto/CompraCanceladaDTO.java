package org.example.laserranitaentradas.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.example.laserranitaentradas.model.entity.FormaPago;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Una compra cancelada del rango, con el motivo: para Reportes > Cancelaciones. */
@Data
@AllArgsConstructor
public class CompraCanceladaDTO {
    private String codigoReserva;
    private LocalDateTime fechaCreacion;
    /** Null en las canceladas antes de que se guardara el motivo (ver scripts/backfill-motivos-cancelacion.sql). */
    private LocalDateTime fechaCancelacion;
    private FormaPago formaPago;
    private BigDecimal montoTotal;
    /** Nombre de un MotivoCancelacion, o null si no quedó registrado. */
    private String motivo;
    /** Lo que escribió quien la anuló (sólo ANULADA_EN_CAJA). */
    private String detalle;
    private String canceladaPor;
}
