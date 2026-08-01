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
    /** Gente que realmente cruzó la puerta: venta en puerta + reservas anticipadas ya validadas por DNI. */
    private long personasIngresadas;
    private List<AfluenciaDiariaDTO> afluenciaDiaria;
    private List<DesgloseTipoEntradaDTO> desglosePorTipo;
    private List<RecaudacionPorFormaPagoDTO> recaudacionPorFormaPago;
    private List<ComprasPorEstadoDTO> comprasPorEstado;
    /** Extras (ej. almuerzo), separado del desglose de pases de ingreso. */
    private List<DesgloseTipoEntradaDTO> desgloseExtras;
    /** Distribución horaria (0-23) de las compras cobradas, para ver a qué hora se compra más. */
    private List<VentasPorHoraDTO> ventasPorHora;

    /** Cuánto se cobró en la puerta (POS) contra reservas anticipadas. */
    private List<VentasPorOrigenDTO> ventasPorOrigen;
    /** Suma de descuentoAplicado (cupones) de las compras cobradas en el rango. */
    private BigDecimal totalDescuentos;
    private long cantidadComprasConDescuento;

    /** Cajas (turnos) cerradas en el rango, filtradas por fechaCierre — no por fechaVisita. */
    private List<CajaResumenReporteDTO> cajas;
    private BigDecimal totalRetirosCajas;
    /** Suma de las diferencias negativas, en valor absoluto: plata que faltó al cerrar. */
    private BigDecimal totalFaltantesCajas;
    /** Suma de las diferencias positivas: plata de más al cerrar. */
    private BigDecimal totalSobrantesCajas;
}
