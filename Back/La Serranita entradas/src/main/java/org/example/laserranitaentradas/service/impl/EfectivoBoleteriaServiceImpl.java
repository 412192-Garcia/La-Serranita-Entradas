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
        // Sin confirmación externa: el comprobante sale ya, pero después del commit, porque el envío es
        // @Async y otro hilo no vería la compra.
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
