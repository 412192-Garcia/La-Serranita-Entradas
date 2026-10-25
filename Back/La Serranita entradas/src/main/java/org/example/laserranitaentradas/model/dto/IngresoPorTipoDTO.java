package org.example.laserranitaentradas.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Personas que entraron en el rango por tipo, por fecha de validación. */
@Data
@AllArgsConstructor
public class IngresoPorTipoDTO {
    private Long tipoEntradaId;
    private String nombre;
    /** Entraron con una venta de puerta (POS) de este tipo. */
    private long enPuerta;
    /** Entraron validando una reserva anticipada de este tipo. */
    private long conAnticipada;
}
