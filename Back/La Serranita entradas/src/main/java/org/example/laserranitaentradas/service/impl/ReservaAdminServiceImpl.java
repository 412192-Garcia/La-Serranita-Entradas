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

/** Reserva de un ADMIN sin cobro (invitados, agencias): queda aprobada al instante. */
@Service
public class ReservaAdminServiceImpl implements PagoService {

    private final EmailService emailService;

    public ReservaAdminServiceImpl(EmailService emailService) {
        this.emailService = emailService;
    }

    @Override
    public PagoResponseDTO procesarPago(Compra compra) {
        Long compraId = compra.getId();
        // Sin fecha ni receptor es del titular: sólo va el comprobante.
        boolean esRegalo = compra.getFechaVisita() == null && compra.getReceptorEmail() != null;
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
                .mensaje("Reserva generada: no se cobra nada por acá, ya está aprobada.")
                .build();
    }

    @Override
    public FormaPago getFormaPago() {
        return FormaPago.RESERVA_ADMIN;
    }

    @Override
    public EstadoCompra getEstadoInicial() {
        return EstadoCompra.APROBADO;
    }
}
