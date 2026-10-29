package org.example.laserranitaentradas.model.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** Corrección de un cierre: recuento y traspasos entre formas de pago, en una transacción. */
@Data
public class CorreccionCajaRequestDTO {
    /** Billetes por denominación; montoContado = Σ denominación×cantidad + cambioContado. */
    private List<ConteoDenominacionDTO> conteoEfectivo;
    private List<CierrePosnetRequestDTO> cierresPosnet;
    /** Cuántas entradas físicas quedan sin cortar en el talonario (contadas al cerrar). */
    private Integer entradasFisicasRestantes;
    /** Total en billetes chicos, cargado de una vez. Opcional, default cero. */
    private BigDecimal cambioContado;
    /** Dólares contados. Sólo se exige si la caja tuvo alguna venta en dólares. */
    private BigDecimal dolaresContado;
    /** Traspasos entre efectivo/tarjeta/QR (uno por grupo firma+origen+destino). Puede venir vacío. */
    private List<AjusteCajaRequestDTO> ajustes;
}
