package org.example.laserranitaentradas.service;

import org.example.laserranitaentradas.model.entity.ConfiguracionParque;

import java.time.LocalTime;

public interface ConfiguracionParqueService {
    ConfiguracionParque getHorarioGeneral();
    ConfiguracionParque actualizarHorarioGeneral(LocalTime horaApertura, LocalTime horaCierre);
}
