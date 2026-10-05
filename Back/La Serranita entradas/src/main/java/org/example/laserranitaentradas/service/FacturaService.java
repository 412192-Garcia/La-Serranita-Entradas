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

    /**
     * Factura de una compra online recién pagada con Mercado Pago: por el punto de venta online y
     * por mail al email de contacto. No hace nada si la facturación online no está configurada
     * (sin AFIP_PUNTO_VENTA_ONLINE), si la compra no es un pago online o si ya tiene factura.
     * Las reservas a pagar en puerta no pasan por acá: se facturan en el POS al cobrarlas.
     */
    void solicitarOnline(Compra compra);

    /**
     * Factura B manual, sin venta (Acciones > Facturación manual): los ítems e importes que cargó el
     * admin. Se emite cuando commitea la transacción, como cualquier otra.
     */
    FacturaResponseDTO emitirManual(org.example.laserranitaentradas.model.dto.FacturaManualDTO pedido);

    /** Las últimas facturas manuales, con su estado (incluida la anulación). */
    java.util.List<FacturaResponseDTO> manuales();

    /** Anula una factura manual: nota de crédito B por el total (o anulada, si no llegó a ARCA). */
    void anularManual(Long facturaId);

    /** La factura B más reciente de cada compra, en dos consultas en vez de dos por compra. */
    java.util.Map<Long, FacturaResponseDTO> obtenerPorCompras(java.util.Collection<Long> compraIds);

    boolean estaHabilitada();
}
