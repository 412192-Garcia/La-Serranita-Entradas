package org.example.laserranitaentradas.model.dto;

import lombok.Data;

import java.time.LocalTime;

@Data
public class HorarioRequest {
    private LocalTime horaApertura;
    private LocalTime horaCierre;
    /** Minutos antes del cierre en que se corta la compra online del día. Null = no cambiarlo. */
    private Integer minutosLimiteCompra;
}
