package org.example.laserranitaentradas.model.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Caja abierta (contado y diferencia en null, totales parciales) o recién cerrada. */
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
    /** Billetes chicos cargados de una vez; incluidos en montoContado. */
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
    /** Con cierre combinado (Tarjeta + QR juntos) reemplazan a los de arriba; si no, null. */
    private BigDecimal totalVentasPosnet;
    private BigDecimal totalCerradoPosnet;
    private BigDecimal diferenciaPosnet;
    private List<CierrePosnetResponseDTO> cierresPosnet;

    /**
     * Si hubo ventas en dólares. Se muestra aunque la caja esté abierta (es un booleano): el front lo usa
     * para pedir el conteo de dólares.
     */
    private Boolean huboVentaDolares;
    /** Dólares esperados (según lo que entró de cada venta en dólares) — null hasta el cierre. */
    private BigDecimal dolaresEsperado;
    /** Dólares que el boletero contó al cerrar — null si esta caja no tuvo ventas en dólares. */
    private BigDecimal dolaresContado;
    private BigDecimal diferenciaDolares;

    private Integer entradasFisicasInicial;
    /** Entradas que quedaron en el talonario al cerrar. Null con la caja abierta. */
    private Integer entradasFisicasRestantes;
    /** inicial + ingresos − entregadas. Null sin inicial o con la caja abierta. */
    private Integer entradasFisicasEsperadas;
    /** restantes − esperadas: negativo = faltan en el talonario, positivo = sobran. */
    private Integer diferenciaEntradas;
    /** Anticipadas validadas en el turno a las que se les dio talonario. Null con la caja abierta. */
    private Integer entradasAnticipadasEntregadas;
    private Integer totalIngresosEntradas;
    private List<IngresoEntradasResponseDTO> ingresosEntradas;

    /** Ventas y retiros en orden. Null con la caja abierta. */
    private List<OperacionCajaDTO> operaciones;

    /** Traspasos entre formas de pago cargados por un admin. Los totales de arriba ya los incluyen. */
    private List<AjusteCajaResponseDTO> ajustes;

    /**
     * Entradas con precio > 0: venta de puerta + anticipadas cobradas acá + anticipadas validadas. Null con
     * la caja abierta.
     */
    private Integer totalEntradasPagas;
    /** Cuántas son reservas cobradas en esta caja. Null con la caja abierta. */
    private Integer entradasPagasAnticipadas;
    /**
     * Cuántas son anticipadas validadas sin cobrar acá. El resto es venta de puerta. Null con la caja
     * abierta.
     */
    private Integer entradasPagasValidadas;
    private List<EntradasPorTipoDTO> entradasVendidasPorTipo;

    /** false = caja deshabilitada. Nunca null. */
    private Boolean habilitada;

    /** true = abierta sin control: sin conteos, se cierra sola a fin de día. */
    private Boolean controlOmitido;

    /** Facturas B emitidas menos notas de crédito. Null con la caja abierta. */
    private BigDecimal totalFacturado;
    /** Facturas B sin CAE (pendientes o con error). Null con la caja abierta. */
    private Integer facturasSinEmitir;
    /** Suma de esas facturas sin emitir. Null mientras sigue ABIERTA. */
    private BigDecimal montoSinEmitir;
}
