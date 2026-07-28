package org.example.laserranitaentradas.service;

import org.example.laserranitaentradas.model.entity.Compra;

public interface EmailService {
    void enviarComprobanteCompra(Long compraId);

    /** Sólo envía algo si la compra es un regalo (tiene receptorEmail cargado). */
    void enviarAvisoRegalo(Long compraId);
}
