package org.example.laserranitaentradas.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Estado de la facturación para Control de facturas.
 *
 * @param problemas facturas en ERROR o trabadas
 * @param certificadoVence vencimiento del certificado (null sin certificado)
 * @param diasParaVencer negativo = vencido
 * @param numeracionControlada si corre el control nocturno (sólo producción)
 * @param numeracionControladaEn último control (null = nunca)
 * @param desfases puntos de venta donde la base no coincide con ARCA
 */
public record ControlFacturacionDTO(
        List<Problema> problemas,
        LocalDate certificadoVence,
        Long diasParaVencer,
        boolean numeracionControlada,
        LocalDateTime numeracionControladaEn,
        List<Desfase> desfases
) {
    /** Una factura con problema y de qué compra es (null en una manual). */
    public record Problema(FacturaResponseDTO factura, String codigoCompra, LocalDateTime creada) {}

    /** Último número del punto de venta y tipo: el de la base y el que informa ARCA. */
    public record Desfase(int puntoVenta, int tipoComprobante, long ultimoBase, long ultimoArca) {}

    /** Totales de un punto de venta y tipo de comprobante en un período (Listado para el contador). */
    public record Totales(int puntoVenta, int tipoComprobante, long cantidad, BigDecimal total, BigDecimal neto, BigDecimal iva) {}
}
