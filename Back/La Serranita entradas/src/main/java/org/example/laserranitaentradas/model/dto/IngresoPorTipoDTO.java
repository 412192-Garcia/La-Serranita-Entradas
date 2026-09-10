package org.example.laserranitaentradas.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Personas que cruzaron la puerta en el rango, por tipo de entrada, separadas según cómo
 * entraron. Se atribuye por fecha de validación (día que la persona entró), no por día de
 * cobro — por eso una anticipada pre-pagada aparece acá el día que se usó.
 */
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
