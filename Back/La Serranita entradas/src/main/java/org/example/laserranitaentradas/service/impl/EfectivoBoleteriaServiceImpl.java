package org.example.laserranitaentradas.service.impl;

import org.example.laserranitaentradas.model.dto.PagoResponseDTO;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.EstadoCompra;
import org.example.laserranitaentradas.model.entity.FormaPago;
import org.example.laserranitaentradas.service.EmailService;
import org.example.laserranitaentradas.service.PagoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class EfectivoBoleteriaServiceImpl implements PagoService {

    private final EmailService emailService;

    public EfectivoBoleteriaServiceImpl(EmailService emailService) {
        this.emailService = emailService;
    }

    @Override
    public PagoResponseDTO procesarPago(Compra compra) {
        // A diferencia de Mercado Pago, acá no hay que esperar ninguna confirmación externa:
        // la reserva ya queda comprometida en el momento de crearla, así que el comprobante
        // se manda al instante (no recién cuando se cobra en boletería).
        //
        // Esto se llama desde dentro de la transacción que crea la compra (iniciarCompraConPago
        // en CompraServiceImpl es @Transactional), así que no se puede mandar el mail ahora
        // mismo: como el envío es @Async, correría en otro hilo que todavía no vería la fila
        // recién insertada (no se commiteó). Se registra para recién disparar después del commit.
        Long compraId = compra.getId();
        boolean esRegalo = compra.getFechaVisita() == null;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    emailService.enviarComprobanteCompra(compraId);
                    if (esRegalo) {
                        emailService.enviarAvisoRegalo(compraId);
                    }
                }
            });
        } else {
            emailService.enviarComprobanteCompra(compraId);
            if (esRegalo) {
                emailService.enviarAvisoRegalo(compraId);
            }
        }

        return PagoResponseDTO.builder()
                .exitoso(true)
                .compraId(compra.getId())
                .initPoint(null)
                .preferenceId(null)
                .mensaje("Reserva registrada con éxito. Presentar DNI en boletería para abonar en efectivo.")
                .build();
    }

    @Override
    public FormaPago getFormaPago() {
        return FormaPago.EFECTIVO_BOLETERIA;
    }

    @Override
    public EstadoCompra getEstadoInicial() {
        return EstadoCompra.RESERVADO_EFECTIVO;
    }
}
