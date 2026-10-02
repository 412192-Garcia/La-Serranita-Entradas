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

    /** La venta se canceló: anula su factura (o le emite nota de crédito si ya estaba autorizada).
     * Corre dentro de la transacción de la cancelación. */
    void alCancelarVenta(Compra compra);

    /** La venta se editó: si cambió el total, nota de crédito por la factura vieja y factura nueva
     * por el monto nuevo. Si el total no cambió, no hace nada. */
    void alEditarVenta(Compra compra, java.math.BigDecimal montoAnterior);

    /** Intenta emitir una factura PENDIENTE contra ARCA. Seguro de llamar en paralelo y repetido. */
    void emitir(Long facturaId);

    /** Lo que llama el job periódico: emite todas las pendientes cuyo reintento ya llegó. */
    void emitirPendientes();

    /** Vuelve a PENDIENTE una factura en ERROR y la emite (ADMIN, después de revisar el error). */
    FacturaResponseDTO reintentar(Long facturaId);

    /** Reenvía el mail con el PDF (ADMIN, desde los rechazos). Lanza si no sale. */
    void reenviarMail(Long facturaId);

    /** PDF de una factura emitida (mismo formato que el ticket). */
    byte[] generarPdf(Long facturaId);

    Optional<FacturaResponseDTO> obtenerPorCompra(Long compraId);

    /** La factura B más reciente de cada compra, en dos consultas en vez de dos por compra. */
    java.util.Map<Long, FacturaResponseDTO> obtenerPorCompras(java.util.Collection<Long> compraIds);

    boolean estaHabilitada();
}
