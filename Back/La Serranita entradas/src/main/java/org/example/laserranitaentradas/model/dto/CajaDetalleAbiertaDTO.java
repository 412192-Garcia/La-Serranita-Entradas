package org.example.laserranitaentradas.model.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Detalle de una caja para el admin (ADMIN-only, gateado en SecurityConfig): a diferencia del
 * detalle de una caja ya cerrada (ver CajaResponseDTO), funciona con la caja todavía ABIERTA —
 * ventas/retiros/ingresos en orden cronológico, más un resumen de lo vendido hasta el momento
 * (por forma de pago y por tipo de entrada). No incluye "esperado vs contado": eso recién existe
 * al cerrar.
 */
@Data
@Builder
public class CajaDetalleAbiertaDTO {
    private List<OperacionCajaDTO> operaciones;
    private BigDecimal totalVentasEfectivo;
    private BigDecimal totalVentasTarjeta;
    private BigDecimal totalVentasQr;
    private Integer totalEntradasVendidas;
    private List<EntradasPorTipoDTO> entradasVendidasPorTipo;
    private boolean huboVentaDolares;
    /** Inicial + ingresos − retiros de entradas físicas − las que ya se cortaron vendiendo hasta
     * ahora: cuántas le quedan al boletero en el talonario en este momento. Null si esta caja no
     * tiene cargado un inicial (cajas abiertas antes de agregar ese campo). */
    private Integer entradasFisicasRestantes;
}
