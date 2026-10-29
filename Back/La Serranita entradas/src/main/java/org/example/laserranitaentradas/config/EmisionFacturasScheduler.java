package org.example.laserranitaentradas.config;

import org.example.laserranitaentradas.service.FacturaService;
import org.example.laserranitaentradas.service.impl.FacturaServiceImpl.FacturaSolicitadaEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.event.EventListener;
import org.example.laserranitaentradas.service.impl.FacturaServiceImpl;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Dos formas de arrancar la emisión:
 * - En el momento: al commitear la venta, en segundo plano.
 * - De respaldo: un job que retoma las PENDIENTE respetando el backoff.
 */
@Component
public class EmisionFacturasScheduler {

    private static final Logger log = LoggerFactory.getLogger(EmisionFacturasScheduler.class);

    private final FacturaService facturaService;

    public EmisionFacturasScheduler(FacturaService facturaService) {
        this.facturaService = facturaService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alSolicitarFactura(FacturaSolicitadaEvent evento) {
        facturaService.emitir(evento.facturaId());
    }

    /** Lote manual: de a una en un solo hilo, para no llenar el pool @Async. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alSolicitarLote(FacturaServiceImpl.LoteFacturasSolicitadoEvent evento) {
        for (Long id : evento.facturaIds()) {
            try {
                facturaService.emitir(id);
            } catch (RuntimeException e) {
                // Una que falla no frena las demás: queda para emitirPendientes.
                log.warn("No se pudo emitir la factura ID {} del lote", id, e);
            }
        }
    }

    /** Ya se publica después del commit. */
    @Async
    @EventListener
    public void alPagarseCompraOnline(FacturaServiceImpl.CompraOnlinePagadaEvent evento) {
        facturaService.procesarCompraOnlinePagada(evento.compraId());
    }

    /** Control nocturno de numeración contra ARCA (sólo hace algo en producción). */
    @Scheduled(cron = "${afip.control-numeracion.cron:0 30 3 * * *}", zone = "America/Argentina/Buenos_Aires")
    public void controlarNumeracion() {
        try {
            facturaService.controlarNumeracion();
        } catch (Exception e) {
            log.warn("Falló el control de numeración contra ARCA", e);
        }
    }

    @Scheduled(fixedDelayString = "${afip.emision.intervalo-ms:60000}",
            initialDelayString = "${afip.emision.intervalo-ms:60000}")
    public void emitirPendientes() {
        try {
            facturaService.emitirPendientes();
        } catch (Exception e) {
            log.warn("Falló la pasada de emisión de facturas pendientes", e);
        }
    }
}
