package org.example.laserranitaentradas.service;

import org.example.laserranitaentradas.model.dto.FacturaResponseDTO;
import org.example.laserranitaentradas.model.dto.FacturacionPosDTO;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.Factura;

import java.util.Optional;

public interface FacturaService {

    /**
     * Encola la factura de una venta dentro de su transacción; se emite al commitear. No crea nada si se
     * eligió mail sin email, si es $0, si la facturación no está configurada o si ya había factura.
     */
    Optional<Factura> solicitar(Compra compra, FacturacionPosDTO pedido);

    /** Anula la factura de la venta cancelada (nota de crédito si ya estaba autorizada). */
    void alCancelarVenta(Compra compra);

    /** Si cambió el total: nota de crédito por la vieja y factura nueva. */
    void alEditarVenta(Compra compra, java.math.BigDecimal montoAnterior);

    /** Emite una factura PENDIENTE. Seguro de llamar en paralelo y repetido. */
    void emitir(Long facturaId);

    /** Job periódico: emite las pendientes cuyo reintento ya llegó. */
    void emitirPendientes();

    /** Vuelve a PENDIENTE una factura en ERROR y la emite. */
    FacturaResponseDTO reintentar(Long facturaId);

    /** Reenvía el mail con el PDF (ADMIN, desde los rechazos). Lanza si no sale. */
    void reenviarMail(Long facturaId);

    /** PDF de una factura emitida (mismo formato que el ticket). */
    byte[] generarPdf(Long facturaId);

    Optional<FacturaResponseDTO> obtenerPorCompra(Long compraId);

    /**
     * Factura una compra online pagada y manda un solo mail con la confirmación y la factura. Si ARCA tarda,
     * la factura llega después en otro mail.
     *
     * @return true si este flujo manda la confirmación; false si la compra no se factura acá y la
     * confirmación la manda quien llama.
     */
    boolean solicitarOnline(Compra compra);

    /** Parte en segundo plano de solicitarOnline. */
    void procesarCompraOnlinePagada(Long compraId);

    /** Problemas, vencimiento del certificado y numeración. */
    org.example.laserranitaentradas.model.dto.ControlFacturacionDTO controlFacturacion();

    /** Compara la última numeración de cada punto de venta con ARCA. Sólo en producción. */
    java.util.List<org.example.laserranitaentradas.model.dto.ControlFacturacionDTO.Desfase> controlarNumeracion();

    /** Ids para el aviso de facturación (-1 certificado por vencer, -2 numeración desfasada). */
    java.util.List<Long> idsAlertasFacturacion();

    /** Totales por punto de venta y tipo de comprobante en el período. */
    java.util.List<org.example.laserranitaentradas.model.dto.ControlFacturacionDTO.Totales> totales(java.time.LocalDate desde, java.time.LocalDate hasta);

    /** CSV con ; y coma decimal, para Excel en español. */
    String exportarCsv(java.time.LocalDate desde, java.time.LocalDate hasta);

    /** Hay punto de venta online y la facturación está configurada. */
    boolean facturacionOnlineActiva();

    /** Facturación configurada y prendida, con el submódulo de boletería. */
    boolean facturacionBoleteriaActiva();

    /** El servidor tiene el punto de venta de las compras online (AFIP_PUNTO_VENTA_ONLINE). */
    boolean puntoVentaOnlineConfigurado();

    /** Factura ahora una compra online paga que quedó sin factura (ADMIN). */
    FacturaResponseDTO facturarOnlineAhora(Long compraId);

    /** Factura manual sin venta (B, o A con receptor). Se emite al commitear. */
    FacturaResponseDTO emitirManual(org.example.laserranitaentradas.model.dto.FacturaManualDTO pedido);

    /** Varias facturas manuales sin entrega. Todo o nada. */
    java.util.List<FacturaResponseDTO> emitirManualLote(org.example.laserranitaentradas.model.dto.FacturaManualLoteDTO lote);

    /** Las últimas facturas manuales, con su estado (incluida la anulación). */
    java.util.List<FacturaResponseDTO> manuales();

    /**
     * Anula una factura: nota de crédito por el total, o anulada si no llegó a ARCA. La venta queda sin
     * factura.
     */
    void anularFactura(Long facturaId);

    /** La factura B más reciente de cada compra. */
    java.util.Map<Long, FacturaResponseDTO> obtenerPorCompras(java.util.Collection<Long> compraIds);

    boolean estaHabilitada();
}
