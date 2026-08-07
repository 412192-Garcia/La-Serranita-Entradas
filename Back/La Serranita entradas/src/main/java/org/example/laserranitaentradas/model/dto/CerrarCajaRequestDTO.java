package org.example.laserranitaentradas.model.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CerrarCajaRequestDTO {
    /** Cuántos billetes de cada denominación contó el boletero; montoContado se calcula sumando denominación×cantidad, más cambioContado. */
    private List<ConteoDenominacionDTO> conteoEfectivo;
    private List<CierrePosnetRequestDTO> cierresPosnet;
    /** Con cuántas entradas físicas termina el turno. */
    private Integer entradasFisicasFinal;
    /** Total en billetes chicos (50, 20, etc.), cargado de una vez en vez de billete por billete. Opcional, default cero. */
    private BigDecimal cambioContado;
}
