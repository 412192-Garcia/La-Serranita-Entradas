package org.example.laserranitaentradas.service;

import org.example.laserranitaentradas.model.entity.Compra;

public interface EmailService {
    void enviarComprobanteCompra(Long compraId);

    /**
     * La confirmación de una compra online con su factura adjunta: un solo mail. En el momento y
     * propagando el error (lo llama la facturación online, que si falla manda todo por separado).
     * Al salir, la factura queda marcada como enviada a ese email.
     */
    void enviarComprobanteCompraConFactura(Long compraId, Long facturaId);

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

    /**
     * Como enviarFacturaOFallar pero a otro email (el que da el cliente en la ventana "Ventas y
     * facturas"). El email se le pasa directo al envío: recién cuando salió se guarda en la factura,
     * junto con la hora, así dos envíos simultáneos no se cruzan los destinatarios.
     */
    void enviarFacturaA(Long facturaId, String destinatario);
}
