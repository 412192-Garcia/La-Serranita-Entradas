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
    /** Puntos por módulo del QR: con 5 (a 203 ppp) queda de unos 38 mm, como el de la cantina. */
    private static final int PUNTOS_POR_MODULO = 5;
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Caracteres por línea con la fuente A: 48 en papel de 80 mm, 32 en 58 mm. */
    @Value("${impresion.columnas:48}")
    private int columnas = 48;

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

        t.grande(true).negrita(true).linea("FACTURA B").negrita(false).grande(false);
        t.linea("ORIGINAL (COD. 006)");
        t.linea(String.format("Pto Vta: %04d   Nro: %08d", c.puntoVenta(), c.numero()));
        t.separador();

        t.izquierda();
        t.fila("Fecha: " + c.fechaEmision().format(FECHA), "Reserva: #" + c.codigoReserva());
        t.linea("Cliente: Consumidor Final");
        t.linea("Cond. IVA: Consumidor Final");
        t.linea("Cond. Venta: Contado");
        t.separador();

        t.negrita(true).linea("CANT DESCRIPCIÓN").negrita(false);
        for (ComprobanteFactura.Item item : c.items()) {
            t.linea(item.cantidad() + "x " + item.descripcion());
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
            t.qr(c.qrUrl());
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

        /** GS v 0: imagen monocromo, 1 bit por punto, filas de ancho múltiplo de 8. */
        void qr(String contenido) {
            BitMatrix m;
            try {
                m = new QRCodeWriter().encode(contenido, BarcodeFormat.QR_CODE, 0, 0, Map.of(EncodeHintType.MARGIN, 0));
            } catch (Exception e) {
                throw new IllegalStateException("No se pudo generar el QR del ticket", e);
            }
            int ancho = m.getWidth() * PUNTOS_POR_MODULO;
            int alto = m.getHeight() * PUNTOS_POR_MODULO;
            int bytesPorFila = (ancho + 7) / 8;
            comando(GS, 'v', '0', 0, bytesPorFila & 0xFF, (bytesPorFila >> 8) & 0xFF, alto & 0xFF, (alto >> 8) & 0xFF);
            for (int y = 0; y < alto; y++) {
                for (int bx = 0; bx < bytesPorFila; bx++) {
                    int b = 0;
                    for (int bit = 0; bit < 8; bit++) {
                        int x = bx * 8 + bit;
                        if (x < ancho && m.get(x / PUNTOS_POR_MODULO, y / PUNTOS_POR_MODULO)) {
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
