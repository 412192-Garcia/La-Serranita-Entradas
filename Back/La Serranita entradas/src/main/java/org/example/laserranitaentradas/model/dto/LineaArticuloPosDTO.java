package org.example.laserranitaentradas.model.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class LineaArticuloPosDTO {
    /** Sólo uno de los dos: catálogo o libre. */
    private Long articuloVarioId;
    private String descripcionLibre;
    /** Precio cargado por el cajero (puede venir del catálogo o ser un valor libre editado). */
    private BigDecimal precioUnitario;
    private Integer cantidad;
}
