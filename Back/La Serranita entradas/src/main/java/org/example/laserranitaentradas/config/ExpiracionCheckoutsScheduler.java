package org.example.laserranitaentradas.config;

import org.example.laserranitaentradas.service.CompraService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Cancela los checkouts de Mercado Pago que quedaron colgados en PENDIENTE_PAGO (el usuario
 * abrió el pago y nunca lo terminó). Sin esto, cada checkout abandonado se queda para siempre
 * con un uso de cupón consumido y ocupando lugar en el cupo diario del día de visita.
 *
 * Antes de cancelar, cada compra se re-verifica contra Mercado Pago (por si el pago entró y el
 * webhook nunca llegó): eso lo resuelve {@link CompraService#expirarCheckoutAbandonado}.
 */
@Component
public class ExpiracionCheckoutsScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExpiracionCheckoutsScheduler.class);

    private final CompraService compraService;

    /** Cuántas horas sin pagarse antes de dar un checkout por abandonado. Una preferencia de
     * Mercado Pago no queda abierta tanto tiempo, así que 3 h es de sobra sin arriesgar cancelar
     * algo que el cliente todavía está por pagar. */
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
