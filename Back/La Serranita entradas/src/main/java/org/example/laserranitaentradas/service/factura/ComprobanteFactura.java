package org.example.laserranitaentradas.service.factura;

import org.example.laserranitaentradas.service.afip.WsfeService;
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
        /** Código ARCA: 1 = Factura A, 3 = Nota de Crédito A, 6 = Factura B, 8 = Nota de Crédito B. */
        int tipoComprobante,
        /** Sólo en una nota de crédito: "Factura B 0037-00000024" (o A). */
        String comprobanteAsociado,
        /** Hora en que ARCA la autorizó (hora de Argentina); null en las facturas viejas. */
        LocalTime horaEmision,
        /** Cómo se llama el código de la compra en el comprobante: "Reserva" (anticipada) o
         * "Venta" (venta de puerta, que nunca fue una reserva). */
        String etiquetaCodigo,
        /** El boletero que cobró en la puerta (el de la caja de la compra); null en una compra
         * online o una factura manual. */
        String atendidoPor,
        /** Sólo en una Factura A (o su nota de crédito); null = Consumidor Final. */
        Receptor receptor
) {
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    /** Sin receptor identificado (Factura B). */
    public ComprobanteFactura(String razonSocial, String domicilio, String cuit, String ingresosBrutos,
                              String inicioActividades, int puntoVenta, long numero, LocalDate fechaEmision,
                              String codigoReserva, List<Item> items, BigDecimal total, BigDecimal ivaContenido,
                              String cae, LocalDate caeVencimiento, String qrUrl, int tipoComprobante,
                              String comprobanteAsociado, LocalTime horaEmision, String etiquetaCodigo,
                              String atendidoPor) {
        this(razonSocial, domicilio, cuit, ingresosBrutos, inicioActividades, puntoVenta, numero, fechaEmision,
                codigoReserva, items, total, ivaContenido, cae, caeVencimiento, qrUrl, tipoComprobante,
                comprobanteAsociado, horaEmision, etiquetaCodigo, atendidoPor, null);
    }

    /** Sin el boletero. */
    public ComprobanteFactura(String razonSocial, String domicilio, String cuit, String ingresosBrutos,
                              String inicioActividades, int puntoVenta, long numero, LocalDate fechaEmision,
                              String codigoReserva, List<Item> items, BigDecimal total, BigDecimal ivaContenido,
                              String cae, LocalDate caeVencimiento, String qrUrl, int tipoComprobante,
                              String comprobanteAsociado, LocalTime horaEmision, String etiquetaCodigo) {
        this(razonSocial, domicilio, cuit, ingresosBrutos, inicioActividades, puntoVenta, numero, fechaEmision,
                codigoReserva, items, total, ivaContenido, cae, caeVencimiento, qrUrl, tipoComprobante,
                comprobanteAsociado, horaEmision, etiquetaCodigo, null);
    }

    /** Sin la hora (comprobantes anteriores a ese dato). */
    public ComprobanteFactura(String razonSocial, String domicilio, String cuit, String ingresosBrutos,
                              String inicioActividades, int puntoVenta, long numero, LocalDate fechaEmision,
                              String codigoReserva, List<Item> items, BigDecimal total, BigDecimal ivaContenido,
                              String cae, LocalDate caeVencimiento, String qrUrl, int tipoComprobante,
                              String comprobanteAsociado) {
        this(razonSocial, domicilio, cuit, ingresosBrutos, inicioActividades, puntoVenta, numero, fechaEmision,
                codigoReserva, items, total, ivaContenido, cae, caeVencimiento, qrUrl, tipoComprobante,
                comprobanteAsociado, null, "Reserva", null);
    }

    /** Con la hora, para una reserva. */
    public ComprobanteFactura(String razonSocial, String domicilio, String cuit, String ingresosBrutos,
                              String inicioActividades, int puntoVenta, long numero, LocalDate fechaEmision,
                              String codigoReserva, List<Item> items, BigDecimal total, BigDecimal ivaContenido,
                              String cae, LocalDate caeVencimiento, String qrUrl, int tipoComprobante,
                              String comprobanteAsociado, LocalTime horaEmision) {
        this(razonSocial, domicilio, cuit, ingresosBrutos, inicioActividades, puntoVenta, numero, fechaEmision,
                codigoReserva, items, total, ivaContenido, cae, caeVencimiento, qrUrl, tipoComprobante,
                comprobanteAsociado, horaEmision, "Reserva", null);
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

    /**
     * Receptor identificado de una Factura A.
     *
     * @param condicionIva el texto, ej. "IVA Responsable Inscripto"
     */
    public record Receptor(String razonSocial, String cuit, String domicilio, String condicionIva) {
        /** "30-71701573-4". */
        public String cuitFormateado() {
            return formatearCuit(cuit);
        }
    }

    public boolean esNotaDeCredito() {
        return WsfeService.esNotaDeCredito(tipoComprobante);
    }

    /** "A" o "B". */
    public String letra() {
        return WsfeService.letra(tipoComprobante);
    }

    /** Factura A (o su nota de crédito): el IVA va discriminado y los ítems sin IVA. */
    public boolean esA() {
        return "A".equals(letra());
    }

    /** Importe neto gravado: el total menos el IVA. */
    public BigDecimal neto() {
        return total.subtract(ivaContenido);
    }

    /** "FACTURA B" / "NOTA DE CRÉDITO A". */
    public String titulo() {
        return WsfeService.nombre(tipoComprobante).toUpperCase(java.util.Locale.ROOT);
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
        return formatearCuit(cuit);
    }

    private static String formatearCuit(String cuit) {
        if (cuit == null || cuit.length() != 11) return cuit;
        return cuit.substring(0, 2) + "-" + cuit.substring(2, 10) + "-" + cuit.substring(10);
    }
}
