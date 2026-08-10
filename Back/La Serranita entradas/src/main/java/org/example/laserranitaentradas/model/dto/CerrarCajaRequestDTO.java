package org.example.laserranitaentradas.model.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CerrarCajaRequestDTO {
    /** Cuántos billetes de cada denominación contó el boletero; montoContado se calcula sumando denominación×cantidad, más cambioContado. */
    private List<ConteoDenominacionDTO> conteoEfectivo;
    private List<CierrePosnetRequestDTO> cierresPosnet;
    /** Cuántas entradas físicas cortó del talonario durante el turno. */
    private Integer entradasFisicasCortadas;
    /** Total en billetes chicos (50, 20, etc.), cargado de una vez en vez de billete por billete. Opcional, default cero. */
    private BigDecimal cambioContado;
    /** Dólares contados. Sólo se exige si la caja tuvo alguna venta en dólares. */
    private BigDecimal dolaresContado;
}
