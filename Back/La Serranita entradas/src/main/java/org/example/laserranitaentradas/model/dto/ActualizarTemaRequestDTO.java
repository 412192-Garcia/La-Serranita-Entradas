package org.example.laserranitaentradas.model.dto;

import lombok.Data;

/** Null en cualquiera de los dos campos = volver al valor por defecto de ese color. */
@Data
public class ActualizarTemaRequestDTO {
    private String colorTema;
    private String colorFondo;
    private String colorTarjeta;
    private String colorBorde;
}
