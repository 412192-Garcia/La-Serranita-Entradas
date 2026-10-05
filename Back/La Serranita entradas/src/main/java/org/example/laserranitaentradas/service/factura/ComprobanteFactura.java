package org.example.laserranitaentradas.service.factura;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.LocalTime;
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
        String qrUrl,
        /** Código ARCA: 6 = Factura B, 8 = Nota de Crédito B. */
        int tipoComprobante,
        /** Sólo en una nota de crédito: "Factura B 0037-00000024". */
        String comprobanteAsociado,
        /** Hora en que ARCA la autorizó (hora de Argentina); null en las facturas viejas. */
        LocalTime horaEmision,
        /** Cómo se llama el código de la compra en el comprobante: "Reserva" (anticipada) o
         * "Venta" (venta de puerta, que nunca fue una reserva). */
        String etiquetaCodigo
) {
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    /** Sin la hora (comprobantes anteriores a ese dato). */
    public ComprobanteFactura(String razonSocial, String domicilio, String cuit, String ingresosBrutos,
                              String inicioActividades, int puntoVenta, long numero, LocalDate fechaEmision,
                              String codigoReserva, List<Item> items, BigDecimal total, BigDecimal ivaContenido,
                              String cae, LocalDate caeVencimiento, String qrUrl, int tipoComprobante,
                              String comprobanteAsociado) {
        this(razonSocial, domicilio, cuit, ingresosBrutos, inicioActividades, puntoVenta, numero, fechaEmision,
                codigoReserva, items, total, ivaContenido, cae, caeVencimiento, qrUrl, tipoComprobante,
                comprobanteAsociado, null, "Reserva");
    }

    /** Con la hora, para una reserva. */
    public ComprobanteFactura(String razonSocial, String domicilio, String cuit, String ingresosBrutos,
                              String inicioActividades, int puntoVenta, long numero, LocalDate fechaEmision,
                              String codigoReserva, List<Item> items, BigDecimal total, BigDecimal ivaContenido,
                              String cae, LocalDate caeVencimiento, String qrUrl, int tipoComprobante,
                              String comprobanteAsociado, LocalTime horaEmision) {
        this(razonSocial, domicilio, cuit, ingresosBrutos, inicioActividades, puntoVenta, numero, fechaEmision,
                codigoReserva, items, total, ivaContenido, cae, caeVencimiento, qrUrl, tipoComprobante,
                comprobanteAsociado, horaEmision, "Reserva");
    }

    /** "Reserva: #L615-261005-1" / "Venta: #261005-3"; vacío en una factura manual (sin compra). */
    public String referencia() {
        if (codigoReserva == null) return "";
        return (etiquetaCodigo != null ? etiquetaCodigo : "Reserva") + ": #" + codigoReserva;
    }

    /** "05/10/2026 14:32", o sólo la fecha si no se sabe la hora. */
    public String fechaYHora() {
        String fecha = fechaEmision.format(FECHA);
        return horaEmision != null ? fecha + " " + horaEmision.format(HORA) : fecha;
    }

    /**
     * Una línea del comprobante. subtotal = lo que se cobró por esa línea (con el precio de grupo si
     * correspondía); null en facturas viejas o si no se pudo calcular de forma que cierre con el
     * total. cantidad 0 = línea sin cantidad (la de "Descuento", con subtotal negativo).
     */
    public record Item(int cantidad, String descripcion, BigDecimal subtotal) {
        public Item(int cantidad, String descripcion) {
            this(cantidad, descripcion, null);
        }

        public boolean esDescuento() {
            return cantidad == 0;
        }
    }

    public boolean esNotaDeCredito() {
        return tipoComprobante == 8;
    }

    /** "FACTURA B" / "NOTA DE CRÉDITO B". */
    public String titulo() {
        return esNotaDeCredito() ? "NOTA DE CRÉDITO B" : "FACTURA B";
    }

    /** "ORIGINAL (COD. 006)". */
    public String codigoOriginal() {
        return String.format("ORIGINAL (COD. %03d)", tipoComprobante);
    }

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
