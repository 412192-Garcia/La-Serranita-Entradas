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
 * Las dos formas en que arranca la emisión de una factura contra ARCA:
 *
 * - En el momento: apenas commitea la venta que la pidió, en segundo plano (la respuesta de la
 *   venta no espera a ARCA). Es lo que hace que "Imprimir factura" tarde unos segundos y no un
 *   minuto.
 * - De respaldo: un job periódico que retoma las que quedaron PENDIENTE porque ARCA estaba caída
 *   o no había internet, respetando el backoff de cada una.
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

    /** Ya se publica después del commit (ver FacturaServiceImpl#solicitarOnline): listener común, en segundo plano. */
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
