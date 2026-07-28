package org.example.laserranitaentradas.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@AllArgsConstructor
public class ReporteResumenDTO {
    private LocalDate desde;
    private LocalDate hasta;
    /** Suma de montoTotal de las compras aprobadas o usadas en el rango: lo efectivamente cobrado. */
    private BigDecimal recaudacionTotal;
    private long cantidadCompras;
    private List<AfluenciaDiariaDTO> afluenciaDiaria;
    private List<DesgloseTipoEntradaDTO> desglosePorTipo;
    private List<RecaudacionPorFormaPagoDTO> recaudacionPorFormaPago;
    private List<ComprasPorEstadoDTO> comprasPorEstado;
    /** Extras (ej. almuerzo), separado del desglose de pases de ingreso. */
    private List<DesgloseTipoEntradaDTO> desgloseExtras;
    /** Distribución horaria (0-23) de las compras cobradas, para ver a qué hora se compra más. */
    private List<VentasPorHoraDTO> ventasPorHora;
}
