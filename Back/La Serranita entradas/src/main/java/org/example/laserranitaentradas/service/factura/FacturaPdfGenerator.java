package org.example.laserranitaentradas.service.factura;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * Representación impresa de la factura en PDF, con el mismo formato que el ticket de la
 * ticketera (80 mm de ancho, como el de la cantina): encabezado fiscal, tipo y número, ítems,
 * total, IVA contenido (Ley 27.743), CAE y QR de ARCA.
 */
@Component
public class FacturaPdfGenerator {

    /** 80 mm en puntos PDF. */
    private static final float ANCHO = 226.77f;
    private static final float MARGEN = 12f;
    /** Alto de la primera pasada: de sobra para cualquier venta; después se recorta al real. */
    private static final float ALTO_BORRADOR = 5000f;
    /** ~40 mm: el QR de ARCA es muy denso y, si se imprime el PDF, cuadraditos chicos cuestan de leer. */
    private static final float LADO_QR = 115f;

    private static final Font NORMAL = new Font(Font.HELVETICA, 8f, Font.NORMAL);
    private static final Font NEGRITA = new Font(Font.HELVETICA, 8f, Font.BOLD);
    private static final Font TITULO = new Font(Font.HELVETICA, 16f, Font.BOLD);
    private static final Font TOTAL = new Font(Font.HELVETICA, 11f, Font.BOLD);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public byte[] generar(ComprobanteFactura c) {
        // Dos pasadas: la primera en una hoja larguísima sólo para medir cuánto ocupa todo, la
        // segunda con el alto exacto. Así el PDF queda como un ticket y no con media hoja en
        // blanco abajo (o cortado si la venta tiene muchas líneas).
        float usado = renderizar(c, ALTO_BORRADOR).altoUsado();
        // + margen de abajo y aire de sobra: OpenPDF no cuenta en la posición vertical el último
        // renglón todavía pendiente, y si queda justo ese renglón salta a una segunda página.
        return renderizar(c, usado + MARGEN + 30f).pdf();
    }

    private Resultado renderizar(ComprobanteFactura c, float alto) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document doc = new Document(new Rectangle(ANCHO, alto), MARGEN, MARGEN, MARGEN, MARGEN);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, salida);
            doc.open();

            centrado(doc, c.razonSocial(), NEGRITA);
            centrado(doc, c.domicilio(), NORMAL);
            centrado(doc, "CUIT: " + c.cuitFormateado(), NORMAL);
            centrado(doc, "Ingresos Brutos: " + c.ingresosBrutos(), NORMAL);
            centrado(doc, "Inicio de Actividades: " + c.inicioActividades(), NORMAL);
            centrado(doc, "Responsable Inscripto", NORMAL);
            separador(doc);

            centrado(doc, c.titulo(), TITULO);
            centrado(doc, c.codigoOriginal(), NORMAL);
            centrado(doc, "Pto Vta: " + String.format("%04d", c.puntoVenta())
                    + "    Nro: " + String.format("%08d", c.numero()), NORMAL);
            separador(doc);

            // Una factura manual no tiene reserva.
            fila(doc, "Fecha: " + c.fechaEmision().format(FECHA), c.codigoReserva() != null ? "Reserva: #" + c.codigoReserva() : "", NORMAL);
            izquierda(doc, "Cliente: Consumidor Final", NORMAL);
            izquierda(doc, "Cond. IVA: Consumidor Final", NORMAL);
            izquierda(doc, "Cond. Venta: Contado", NORMAL);
            if (c.comprobanteAsociado() != null) {
                izquierda(doc, "Comprobante asociado: " + c.comprobanteAsociado(), NEGRITA);
            }
            separador(doc);

            boolean conSubtotales = c.items().stream().allMatch(i -> i.subtotal() != null);
            if (conSubtotales) fila(doc, "CANT  DESCRIPCIÓN", "IMPORTE", NEGRITA);
            else izquierda(doc, "CANT  DESCRIPCIÓN", NEGRITA);
            for (ComprobanteFactura.Item item : c.items()) {
                String texto = item.esDescuento() ? item.descripcion() : item.cantidad() + "x  " + item.descripcion();
                if (conSubtotales) fila(doc, texto, pesos(item.subtotal()), NORMAL);
                else izquierda(doc, texto, NORMAL);
            }
            separador(doc);

            fila(doc, "TOTAL:", pesos(c.total()), TOTAL);
            separador(doc);

            centrado(doc, "Régimen de Transparencia Fiscal", NORMAL);
            centrado(doc, "al Consumidor - Ley 27.743", NORMAL);
            fila(doc, "IVA Contenido:", pesos(c.ivaContenido()), NORMAL);
            separador(doc);

            izquierda(doc, "CAE: " + c.cae(), NORMAL);
            izquierda(doc, "Vto. CAE: " + c.caeVencimiento().format(FECHA), NORMAL);

            if (c.qrUrl() != null) {
                Image qr = Image.getInstance(qrImagen(c.qrUrl()), null);
                qr.scaleAbsolute(LADO_QR, LADO_QR);
                qr.setAlignment(Element.ALIGN_CENTER);
                qr.setSpacingBefore(8f);
                doc.add(qr);
            }
            centrado(doc, "Comprobante Autorizado", NORMAL);

            float altoUsado = alto - writer.getVerticalPosition(true);
            doc.close();
            return new Resultado(salida.toByteArray(), altoUsado);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF de la factura " + c.numeroFormateado(), e);
        }
    }

    private static void centrado(Document doc, String texto, Font font) throws DocumentException {
        Paragraph p = new Paragraph(texto, font);
        p.setAlignment(Element.ALIGN_CENTER);
        p.setLeading(font.getSize() * 1.3f);
        doc.add(p);
    }

    private static void izquierda(Document doc, String texto, Font font) throws DocumentException {
        Paragraph p = new Paragraph(texto, font);
        p.setLeading(font.getSize() * 1.3f);
        doc.add(p);
    }

    /** Texto a la izquierda y valor a la derecha en la misma línea. */
    private static void fila(Document doc, String izquierda, String derecha, Font font) throws DocumentException {
        PdfPTable tabla = new PdfPTable(2);
        tabla.setWidthPercentage(100);
        tabla.addCell(celda(izquierda, font, Element.ALIGN_LEFT));
        tabla.addCell(celda(derecha, font, Element.ALIGN_RIGHT));
        doc.add(tabla);
    }

    private static PdfPCell celda(String texto, Font font, int alineacion) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, font));
        celda.setBorder(Rectangle.NO_BORDER);
        celda.setHorizontalAlignment(alineacion);
        celda.setPadding(0);
        celda.setPaddingTop(1f);
        celda.setPaddingBottom(2f);
        return celda;
    }

    private static void separador(Document doc) throws DocumentException {
        LineSeparator linea = new LineSeparator(0.5f, 100f, Color.GRAY, Element.ALIGN_CENTER, -3f);
        Paragraph p = new Paragraph(new Chunk(linea));
        p.setSpacingBefore(2f);
        p.setSpacingAfter(6f);
        doc.add(p);
    }

    private static String pesos(BigDecimal monto) {
        return NumberFormat.getCurrencyInstance(new Locale("es", "AR")).format(monto);
    }

    private static BufferedImage qrImagen(String contenido) throws Exception {
        BitMatrix matriz = new QRCodeWriter().encode(contenido, BarcodeFormat.QR_CODE, 0, 0,
                Map.of(EncodeHintType.MARGIN, 0));
        int lado = matriz.getWidth();
        BufferedImage imagen = new BufferedImage(lado, lado, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < lado; x++) {
            for (int y = 0; y < lado; y++) {
                imagen.setRGB(x, y, matriz.get(x, y) ? 0x000000 : 0xFFFFFF);
            }
        }
        return imagen;
    }

    private record Resultado(byte[] pdf, float altoUsado) {}
}
