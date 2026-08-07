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

/**
 * Reserva cargada a mano por un ADMIN, sin cobrar nada por acá (invitados, o ventas por
 * agencia donde el cobro real se hizo por fuera): no hay ningún pago que esperar, así que
 * queda aprobada al instante, igual que EfectivoBoleteriaServiceImpl pero sin "a cobrar en boletería".
 */
@Service
public class ReservaAdminServiceImpl implements PagoService {

    private final EmailService emailService;

    public ReservaAdminServiceImpl(EmailService emailService) {
        this.emailService = emailService;
    }

    @Override
    public PagoResponseDTO procesarPago(Compra compra) {
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
