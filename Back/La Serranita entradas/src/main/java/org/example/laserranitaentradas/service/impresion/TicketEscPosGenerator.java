package org.example.laserranitaentradas.service.impresion;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.example.laserranitaentradas.service.factura.ComprobanteFactura;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * Arma el ticket de la factura en ESC/POS, el lenguaje de las ticketeras térmicas (la SOL 802 de
 * la boletería lo habla). Mismo contenido que el PDF: los dos salen de {@link ComprobanteFactura}.
 *
 * El QR va como imagen (raster, GS v 0) y no con el comando nativo de QR (GS ( k): el raster lo
 * entiende cualquier ticketera ESC/POS, el nativo no todas.
 */
@Component
public class TicketEscPosGenerator {

    private static final byte ESC = 0x1B;
    private static final byte GS = 0x1D;
    /** Página de códigos 850 (Multilingual Latin I): tiene ñ y vocales con tilde. */
    private static final int CODEPAGE_PC850 = 2;
    private static final Charset CHARSET = Charset.forName("IBM850");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Caracteres por línea con la fuente A: 48 en papel de 80 mm, 32 en 58 mm. */
    @Value("${impresion.columnas:48}")
    private int columnas = 48;

    /**
     * Cómo se imprime el QR. "nativo" (por defecto): se le manda el texto y la ticketera lo dibuja
     * ella misma (GS ( k). Con la SOL 802 se lee mucho mejor que la imagen: sale nítido y con su
     * propio margen. "imagen": el QR se arma acá como imagen (GS v 0), para una ticketera que no
     * tenga el comando de QR.
     */
    @Value("${impresion.qr-modo:nativo}")
    private String qrModo = "nativo";

    /**
     * Puntos de impresora por cuadradito del QR (1 a 16). El contenido que exige ARCA (una URL con los
     * datos del comprobante en base64) da un QR muy denso, de 57×57 cuadraditos: lo que lo hace
     * legible en papel térmico es que cada cuadradito sea grande. Con 6 el QR mide ~43 mm.
     */
    @Value("${impresion.qr-puntos-por-modulo:6}")
    private int puntosPorModulo = 6;

    public byte[] generar(ComprobanteFactura c) {
        Ticket t = new Ticket();
        t.comando(ESC, '@');                       // reset
        t.comando(ESC, 't', CODEPAGE_PC850);

        t.centro().negrita(true).linea(c.razonSocial()).negrita(false);
        t.linea(c.domicilio());
        t.linea("CUIT: " + c.cuitFormateado());
        t.linea("Ingresos Brutos: " + c.ingresosBrutos());
        t.linea("Inicio de Actividades: " + c.inicioActividades());
        t.linea("Responsable Inscripto");
        t.separador();

        t.grande(true).negrita(true).linea(c.titulo()).negrita(false).grande(false);
        t.linea(c.codigoOriginal());
        t.linea(String.format("Pto Vta: %04d   Nro: %08d", c.puntoVenta(), c.numero()));
        t.separador();

        t.izquierda();
        // Una factura manual no tiene reserva.
        t.fila("Fecha: " + c.fechaYHora(), c.referencia());
        t.linea("Cliente: Consumidor Final");
        t.linea("Cond. IVA: Consumidor Final");
        t.linea("Cond. Venta: Contado");
        if (c.comprobanteAsociado() != null) {
            t.negrita(true).linea("Asociado: " + c.comprobanteAsociado()).negrita(false);
        }
        t.separador();

        boolean conSubtotales = c.items().stream().allMatch(i -> i.subtotal() != null);
        t.negrita(true);
        if (conSubtotales) t.fila("CANT DESCRIPCIÓN", "IMPORTE");
        else t.linea("CANT DESCRIPCIÓN");
        t.negrita(false);
        for (ComprobanteFactura.Item item : c.items()) {
            String texto = item.esDescuento() ? item.descripcion() : item.cantidad() + "x " + item.descripcion();
            if (conSubtotales) t.fila(texto, pesos(item.subtotal()));
            else t.linea(texto);
        }
        t.separador();

        t.negrita(true).fila("TOTAL:", pesos(c.total())).negrita(false);
        t.separador();

        t.centro();
        t.linea("Régimen de Transparencia Fiscal");
        t.linea("al Consumidor - Ley 27.743");
        t.izquierda();
        t.fila("IVA Contenido:", pesos(c.ivaContenido()));
        t.separador();

        t.linea("CAE: " + c.cae());
        t.linea("Vto. CAE: " + c.caeVencimiento().format(FECHA));

        if (c.qrUrl() != null) {
            t.centro();
            t.saltos(1);
            if ("imagen".equalsIgnoreCase(qrModo)) t.qrImagen(c.qrUrl());
            else t.qrNativo(c.qrUrl());
            t.saltos(1);
        }
        t.centro().linea("Comprobante Autorizado");

        t.comando(ESC, 'd', 4);                    // avanzar papel hasta pasar la cuchilla
        t.comando(GS, 'V', 66, 0);                 // corte parcial
        return t.bytes();
    }

    private static String pesos(BigDecimal monto) {
        return NumberFormat.getCurrencyInstance(new Locale("es", "AR")).format(monto).replace(' ', ' ');
    }

    /** Acumula los bytes del ticket con una API chica, para que generar() se lea como el ticket. */
    private class Ticket {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        void comando(int... bytes) {
            for (int b : bytes) out.write(b);
        }

        Ticket centro() { comando(ESC, 'a', 1); return this; }

        Ticket izquierda() { comando(ESC, 'a', 0); return this; }

        Ticket negrita(boolean si) { comando(ESC, 'E', si ? 1 : 0); return this; }

        /** Doble alto y doble ancho. */
        Ticket grande(boolean si) { comando(GS, '!', si ? 0x11 : 0x00); return this; }

        Ticket linea(String texto) {
            out.writeBytes((texto == null ? "" : texto).getBytes(CHARSET));
            out.write('\n');
            return this;
        }

        /** Texto a la izquierda y valor a la derecha, ocupando el ancho del papel. */
        Ticket fila(String izquierda, String derecha) {
            int espacios = columnas - izquierda.length() - derecha.length();
            if (espacios < 1) {
                return linea(izquierda).linea(" ".repeat(Math.max(0, columnas - derecha.length())) + derecha);
            }
            return linea(izquierda + " ".repeat(espacios) + derecha);
        }

        Ticket separador() { return linea("-".repeat(columnas)); }

        void saltos(int n) { comando(ESC, 'd', n); }

        /**
         * GS ( k: la ticketera arma y dibuja el QR (modelo 2, corrección L, la mínima: con un contenido
         * tan largo, más corrección = más cuadraditos). Primero se guardan los datos, después se imprime.
         */
        void qrNativo(String contenido) {
            byte[] datos = contenido.getBytes(StandardCharsets.US_ASCII);
            int modulo = Math.max(1, Math.min(16, puntosPorModulo));
            comando(GS, '(', 'k', 4, 0, 49, 65, 50, 0);          // modelo 2
            comando(GS, '(', 'k', 3, 0, 49, 67, modulo);         // tamaño del cuadradito
            comando(GS, '(', 'k', 3, 0, 49, 69, 48);             // corrección L
            int largo = datos.length + 3;
            comando(GS, '(', 'k', largo & 0xFF, (largo >> 8) & 0xFF, 49, 80, 48);
            out.writeBytes(datos);                               // guardar los datos
            comando(GS, '(', 'k', 3, 0, 49, 81, 48);             // imprimir
            out.write('\n');
        }

        /**
         * GS v 0: el QR como imagen monocromo (1 bit por punto, filas de ancho múltiplo de 8), para
         * ticketeras sin comando de QR. Con margen blanco de 4 cuadraditos alrededor: sin él (y pegado
         * al texto de arriba y abajo) el celular no encuentra el código.
         */
        void qrImagen(String contenido) {
            BitMatrix m;
            try {
                m = new QRCodeWriter().encode(contenido, BarcodeFormat.QR_CODE, 0, 0, Map.of(EncodeHintType.MARGIN, 4));
            } catch (Exception e) {
                throw new IllegalStateException("No se pudo generar el QR del ticket", e);
            }
            int ancho = m.getWidth() * puntosPorModulo;
            int alto = m.getHeight() * puntosPorModulo;
            int bytesPorFila = (ancho + 7) / 8;
            comando(GS, 'v', '0', 0, bytesPorFila & 0xFF, (bytesPorFila >> 8) & 0xFF, alto & 0xFF, (alto >> 8) & 0xFF);
            for (int y = 0; y < alto; y++) {
                for (int bx = 0; bx < bytesPorFila; bx++) {
                    int b = 0;
                    for (int bit = 0; bit < 8; bit++) {
                        int x = bx * 8 + bit;
                        if (x < ancho && m.get(x / puntosPorModulo, y / puntosPorModulo)) {
                            b |= 0x80 >> bit;
                        }
                    }
                    out.write(b);
                }
            }
            out.write('\n');
        }

        byte[] bytes() { return out.toByteArray(); }
    }
}
