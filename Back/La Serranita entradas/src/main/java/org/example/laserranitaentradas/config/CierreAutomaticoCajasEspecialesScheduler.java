package org.example.laserranitaentradas.config;

import org.example.laserranitaentradas.service.CajaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Cierra a la madrugada las cajas sin control de días anteriores, con diferencia 0. */
@Component
public class CierreAutomaticoCajasEspecialesScheduler {

    private static final Logger log = LoggerFactory.getLogger(CierreAutomaticoCajasEspecialesScheduler.class);

    private final CajaService cajaService;

    public CierreAutomaticoCajasEspecialesScheduler(CajaService cajaService) {
        this.cajaService = cajaService;
    }

    @Scheduled(cron = "0 5 0 * * *")
    public void cerrarCajasSinControlAtrasadas() {
        try {
            cajaService.cerrarCajasSinControlAtrasadas();
        } catch (Exception e) {
            log.warn("No se pudieron cerrar automáticamente las cajas sin control", e);
        }
    }
}
