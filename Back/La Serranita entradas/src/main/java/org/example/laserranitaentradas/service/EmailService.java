package org.example.laserranitaentradas.service;

import org.example.laserranitaentradas.model.entity.Compra;

public interface EmailService {
    void enviarComprobanteCompra(Long compraId);

    /**
     * Confirmación de una compra online con la factura adjunta, en el momento y propagando el error. Marca la
     * factura como enviada.
     */
    void enviarComprobanteCompraConFactura(Long compraId, Long facturaId);

    /** Sólo envía algo si la compra es un regalo (tiene receptorEmail cargado). */
    void enviarAvisoRegalo(Long compraId);

    /** Manda la factura en segundo plano. Si falla queda como rechazo FACTURA_EMAIL. */
    void enviarFactura(Long facturaId);

    /** Como enviarFactura pero en el momento y propagando el error, para el reenvío manual. */
    void enviarFacturaOFallar(Long facturaId);

    /**
     * Como enviarFacturaOFallar a otro email. El email se guarda recién al salir, para que dos envíos no se
     * crucen.
     */
    void enviarFacturaA(Long facturaId, String destinatario);
}
