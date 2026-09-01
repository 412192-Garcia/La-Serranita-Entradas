package org.example.laserranitaentradas.model.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Sirve tanto para la caja abierta (montoContado/diferencia todavía null, los totales
 * son "hasta ahora") como para la caja recién cerrada (todo completo).
 */
@Data
@Builder
public class CajaResponseDTO {
    private Long id;
    private String estado;
    private LocalDateTime fechaApertura;
    private BigDecimal montoInicial;
    private LocalDateTime fechaCierre;
    private BigDecimal totalVentasEfectivo;
    private BigDecimal totalRetiros;
    private BigDecimal efectivoEsperado;
    private BigDecimal montoContado;
    private BigDecimal diferencia;
    /** Total en billetes chicos cargado de una vez; ya está incluido en montoContado. Null hasta el cierre. */
    private BigDecimal cambioContado;
    private List<RetiroCajaResponseDTO> retiros;

    /** Detalle del conteo de billetes cargado al cerrar. Vacío mientras la caja sigue abierta. */
    private List<ConteoDenominacionDTO> conteoEfectivo;

    /** Esperado (según lo vendido con TARJETA/QR en esta caja) — null mientras sigue ABIERTA. */
    private BigDecimal totalVentasTarjeta;
    private BigDecimal totalVentasQr;
    /** Contado (suma de los cierresPosnet cargados) — null hasta el cierre. */
    private BigDecimal totalCerradoTarjeta;
    private BigDecimal totalCerradoQr;
    private BigDecimal diferenciaTarjeta;
    private BigDecimal diferenciaQr;
    /** Si el cierre se cargó combinado (Tarjeta+QR juntos, sin distinguir), estos tres reemplazan a los de arriba; si no, quedan null. */
    private BigDecimal totalVentasPosnet;
    private BigDecimal totalCerradoPosnet;
    private BigDecimal diferenciaPosnet;
    private List<CierrePosnetResponseDTO> cierresPosnet;

    /**
     * Si hubo o no alguna venta pagada en dólares en esta caja (sigue siendo un cobro en
     * EFECTIVO_BOLETERIA, sólo cambia la moneda física). A diferencia de los totales, esto se
     * expone SIEMPRE (aunque la caja siga ABIERTA): es sólo un booleano, no un monto, así que
     * no le sirve al boletero para "calcar" el cierre — pero el frontend lo necesita para saber
     * si mostrar el campo de dólares contados al cerrar.
     */
    private Boolean huboVentaDolares;
    /** Dólares esperados (según lo que entró de cada venta en dólares) — null hasta el cierre. */
    private BigDecimal dolaresEsperado;
    /** Dólares que el boletero contó al cerrar — null si esta caja no tuvo ventas en dólares. */
    private BigDecimal dolaresContado;
    private BigDecimal diferenciaDolares;

    private Integer entradasFisicasInicial;
    private Integer entradasFisicasCortadas;
    /** Null mientras sigue ABIERTA. */
    private Integer entradasFisicasEsperadas;
    private Integer diferenciaEntradas;
    private Integer totalIngresosEntradas;
    private List<IngresoEntradasResponseDTO> ingresosEntradas;

    /** Ventas + retiros en orden cronológico. Null mientras sigue ABIERTA (mismo motivo anti-trampa que los totales esperados). */
    private List<OperacionCajaDTO> operaciones;

    /**
     * Ajustes manuales de la repartición por forma de pago cargados por un admin sobre este
     * cierre (traspasos entre efectivo/tarjeta/QR). Vacío si no se ajustó nada. Los esperados
     * y diferencias de arriba ya vienen con estos ajustes aplicados.
     */
    private List<AjusteCajaResponseDTO> ajustes;

    /** Unidades vendidas de tipos de entrada con precio > 0 (excluye las gratis, los extras y los artículos), sin importar la forma de pago. Null mientras sigue ABIERTA. */
    private Integer totalEntradasPagas;
    private List<EntradasPorTipoDTO> entradasVendidasPorTipo;

    /** false = un admin deshabilitó esta caja (no figura en listados ni reportes). El detalle por id la sigue devolviendo. Nunca null. */
    private Boolean habilitada;
}
