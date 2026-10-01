package org.example.laserranitaentradas.service;

import org.example.laserranitaentradas.model.entity.Compra;

public interface EmailService {
    void enviarComprobanteCompra(Long compraId);

    /** Sólo envía algo si la compra es un regalo (tiene receptorEmail cargado). */
    void enviarAvisoRegalo(Long compraId);

    /**
     * Manda la factura emitida (destino MAIL) con el PDF adjunto, en segundo plano. Si falla, lo
     * deja registrado como rechazo FACTURA_EMAIL para que un admin lo reenvíe.
     */
    void enviarFactura(Long facturaId);

    /** Igual que enviarFactura pero en el momento y propagando el error: para el reenvío manual,
     * donde quien lo pidió está mirando la pantalla y tiene que saber si salió. */
    void enviarFacturaOFallar(Long facturaId);
}
