package org.example.laserranitaentradas.service;

import org.example.laserranitaentradas.model.entity.ConfiguracionParque;

import java.time.LocalTime;

public interface ConfiguracionParqueService {
    ConfiguracionParque getHorarioGeneral();
    /** `minutosLimiteCompra` null = dejar el que ya estaba. Tiene que estar entre 0 y ConfiguracionParqueService.MAX_MINUTOS_LIMITE_COMPRA. */
    ConfiguracionParque actualizarHorarioGeneral(LocalTime horaApertura, LocalTime horaCierre, Integer minutosLimiteCompra);

    /** Tope razonable del lapso: un día entero de anticipación ya equivaldría a no vender para hoy. */
    int MAX_MINUTOS_LIMITE_COMPRA = 720;
}
