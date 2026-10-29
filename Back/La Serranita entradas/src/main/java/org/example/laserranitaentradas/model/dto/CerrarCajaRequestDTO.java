package org.example.laserranitaentradas.model.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CerrarCajaRequestDTO {
    /** Billetes por denominación; montoContado = Σ denominación×cantidad + cambioContado. */
    private List<ConteoDenominacionDTO> conteoEfectivo;
    private List<CierrePosnetRequestDTO> cierresPosnet;
    /** Cuántas entradas físicas quedan sin cortar en el talonario (contadas al cerrar). */
    private Integer entradasFisicasRestantes;
    /** Billetes chicos cargados de una vez. Opcional. */
    private BigDecimal cambioContado;
    /** Dólares contados. Sólo se exige si la caja tuvo alguna venta en dólares. */
    private BigDecimal dolaresContado;
}
