package org.example.laserranitaentradas.model.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Detalle de una caja, también abierta, para el admin: operaciones y resumen de lo vendido. Sin esperado vs
 * contado.
 */
@Data
@Builder
public class CajaDetalleAbiertaDTO {
    private List<OperacionCajaDTO> operaciones;
    private BigDecimal totalVentasEfectivo;
    private BigDecimal totalVentasTarjeta;
    private BigDecimal totalVentasQr;
    /** Entradas con precio > 0: venta de puerta + anticipadas cobradas acá + anticipadas validadas. */
    private Integer totalEntradasPagas;
    /** De totalEntradasPagas, cuántas son anticipadas (reservas) cobradas en esta caja. */
    private Integer entradasPagasAnticipadas;
    /** Cuántas son anticipadas validadas sin cobrar acá. El resto es venta de puerta. */
    private Integer entradasPagasValidadas;
    private List<EntradasPorTipoDTO> entradasVendidasPorTipo;
    private boolean huboVentaDolares;
    /** Anticipadas validadas a las que se les dio talonario. */
    private Integer entradasAnticipadasEntregadas;
    /** Las que le quedan ahora en el talonario. Null si la caja no tiene inicial. */
    private Integer entradasFisicasRestantes;

    /** Facturas B con CAE − notas de crédito con CAE. */
    private BigDecimal totalFacturado;
    /** Facturas B que todavía esperan CAE (pendientes o con error), y cuánto suman. */
    private Integer facturasSinEmitir;
    private BigDecimal montoSinEmitir;
}
