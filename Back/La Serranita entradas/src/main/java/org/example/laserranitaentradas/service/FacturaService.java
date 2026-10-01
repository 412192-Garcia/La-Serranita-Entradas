package org.example.laserranitaentradas.service;

import org.example.laserranitaentradas.model.dto.FacturaResponseDTO;
import org.example.laserranitaentradas.model.dto.FacturacionPosDTO;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.Factura;

import java.util.Optional;

public interface FacturaService {

    /**
     * Encola la factura de una compra recién cobrada, dentro de la misma transacción que la
     * venta. No habla con ARCA: la emisión arranca cuando esa transacción commitea.
     *
     * No crea nada (y no es un error) si el boletero eligió mail con el campo vacío, si la venta
     * es de $0, si la facturación no está configurada o si la compra ya tenía factura (reintento
     * de la cola offline con la misma clave de idempotencia).
     */
    Optional<Factura> solicitar(Compra compra, FacturacionPosDTO pedido);

    /** Intenta emitir una factura PENDIENTE contra ARCA. Seguro de llamar en paralelo y repetido. */
    void emitir(Long facturaId);

    /** Lo que llama el job periódico: emite todas las pendientes cuyo reintento ya llegó. */
    void emitirPendientes();

    /** Vuelve a PENDIENTE una factura en ERROR y la emite (ADMIN, después de revisar el error). */
    FacturaResponseDTO reintentar(Long facturaId);

    Optional<FacturaResponseDTO> obtenerPorCompra(Long compraId);

    boolean estaHabilitada();
}
