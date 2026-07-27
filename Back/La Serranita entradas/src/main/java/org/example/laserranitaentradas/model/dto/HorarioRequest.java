package org.example.laserranitaentradas.model.dto;

import lombok.Data;

import java.time.LocalTime;

@Data
public class HorarioRequest {
    private LocalTime horaApertura;
    private LocalTime horaCierre;
}
