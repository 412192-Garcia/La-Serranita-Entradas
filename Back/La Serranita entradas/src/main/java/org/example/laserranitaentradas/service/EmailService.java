package org.example.laserranitaentradas.service;

import org.example.laserranitaentradas.model.entity.Compra;

public interface EmailService {
    void enviarComprobanteCompra(Long compraId);
}
