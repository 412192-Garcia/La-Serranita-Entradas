package org.example.laserranitaentradas.config;

import org.example.laserranitaentradas.service.CompraService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Cancela los checkouts que quedaron en PENDIENTE_PAGO, para liberar cupo y cupón. Antes re-verifica contra
 * Mercado Pago ({@link CompraService#expirarCheckoutAbandonado}).
 */
@Component
public class ExpiracionCheckoutsScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExpiracionCheckoutsScheduler.class);

    private final CompraService compraService;

    /** Horas sin pagar para darlo por abandonado. */
    @Value("${compras.checkout-abandonado.horas:3}")
    private int horasAntiguedad;

    public ExpiracionCheckoutsScheduler(CompraService compraService) {
        this.compraService = compraService;
    }

    @Scheduled(fixedDelayString = "${compras.checkout-abandonado.intervalo-ms:900000}",
            initialDelayString = "${compras.checkout-abandonado.intervalo-ms:900000}")
    public void expirarCheckoutsAbandonados() {
        List<Long> ids = compraService.idsCheckoutsAbandonados(horasAntiguedad);
        if (ids.isEmpty()) {
            return;
        }
        log.info("Expirando {} checkout(s) abandonado(s)", ids.size());
        for (Long id : ids) {
            try {
                compraService.expirarCheckoutAbandonado(id);
            } catch (Exception e) {
                log.warn("No se pudo expirar el checkout abandonado de la compra ID {}", id, e);
            }
        }
    }
}
