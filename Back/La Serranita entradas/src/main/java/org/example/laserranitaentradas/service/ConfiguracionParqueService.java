package org.example.laserranitaentradas.service;

import org.example.laserranitaentradas.model.dto.FacturaPredeterminadaDTO;
import org.example.laserranitaentradas.model.entity.ConfiguracionParque;

import java.time.LocalTime;

public interface ConfiguracionParqueService {
    ConfiguracionParque getHorarioGeneral();
    /** `minutosLimiteCompra` null = no cambiarlo. Entre 0 y MAX_MINUTOS_LIMITE_COMPRA. */
    ConfiguracionParque actualizarHorarioGeneral(LocalTime horaApertura, LocalTime horaCierre, Integer minutosLimiteCompra);

    /** Factura por defecto de cada forma de pago en el POS. Cada una tiene que ser IMPRIMIR o MAIL. */
    ConfiguracionParque actualizarFacturaPredeterminada(FacturaPredeterminadaDTO predeterminada);

    /** Tope razonable del lapso: un día entero de anticipación ya equivaldría a no vender para hoy. */
    int MAX_MINUTOS_LIMITE_COMPRA = 720;
}
