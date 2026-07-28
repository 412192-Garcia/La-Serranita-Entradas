package org.example.laserranitaentradas.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class VentasPorHoraDTO {
    /** 0-23, hora local del momento de la compra (no de la visita). */
    private int hora;
    private long cantidadCompras;
    private long cantidadPases;
}
