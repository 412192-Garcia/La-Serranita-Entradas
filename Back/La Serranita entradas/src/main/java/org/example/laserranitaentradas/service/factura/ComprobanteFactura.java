package org.example.laserranitaentradas.service.factura;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Todo lo que va impreso en una factura emitida, ya resuelto: lo usan el PDF del mail y (más
 * adelante) el ticket de la ticketera, así el formato del comprobante sale de un solo lugar.
 */
public record ComprobanteFactura(
        String razonSocial,
        String domicilio,
        String cuit,
        String ingresosBrutos,
        String inicioActividades,
        int puntoVenta,
        long numero,
        LocalDate fechaEmision,
        String codigoReserva,
        List<Item> items,
        BigDecimal total,
        BigDecimal ivaContenido,
        String cae,
        LocalDate caeVencimiento,
        String qrUrl
) {
    /** Sin precio por línea a propósito (mismo criterio que el mail de comprobante): el precio
     * de una entrada depende de la forma de pago, el grupo y los descuentos, así que
     * "cantidad × precio de lista" puede no coincidir con lo cobrado. El total sí es exacto. */
    public record Item(int cantidad, String descripcion) {}

    /** "0037-00000021". */
    public String numeroFormateado() {
        return String.format("%04d-%08d", puntoVenta, numero);
    }

    /** "30-71701573-4". */
    public String cuitFormateado() {
        if (cuit == null || cuit.length() != 11) return cuit;
        return cuit.substring(0, 2) + "-" + cuit.substring(2, 10) + "-" + cuit.substring(10);
    }
}
