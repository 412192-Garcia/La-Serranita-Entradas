package org.example.laserranitaentradas.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class CerrarCajaRequestDTO {
    /** Cuántos billetes de cada denominación contó el boletero; montoContado se calcula sumando denominación×cantidad. */
    private List<ConteoDenominacionDTO> conteoEfectivo;
    private List<CierrePosnetRequestDTO> cierresPosnet;
    /** Con cuántas entradas físicas termina el turno. */
    private Integer entradasFisicasFinal;
}
