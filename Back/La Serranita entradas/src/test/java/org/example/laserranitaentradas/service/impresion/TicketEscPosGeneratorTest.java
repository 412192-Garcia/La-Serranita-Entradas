package org.example.laserranitaentradas.service.impresion;

import org.example.laserranitaentradas.service.factura.ComprobanteFactura;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TicketEscPosGeneratorTest {

    private final TicketEscPosGenerator generator = new TicketEscPosGenerator();

    private static ComprobanteFactura comprobante(String qr) {
        return new ComprobanteFactura(
                "PARQUE RECREATIVO LA SERRANITA S.A.S.", "RUTA 5 KM43,5", "30717015734", "287116730", "30/11/2020",
                37, 21L, LocalDate.of(2026, 9, 30), "260930-6",
                List.of(new ComprobanteFactura.Item(2, "General"), new ComprobanteFactura.Item(1, "Menú Almuerzo")),
                new BigDecimal("68600.00"), new BigDecimal("11905.79"),
                "86390938715788", LocalDate.of(2026, 10, 10), qr, 6, null);
    }

    @Test
    void arrancaConResetYTerminaConCorte() {
        byte[] t = generator.generar(comprobante("https://www.afip.gob.ar/fe/qr/?p=x"));

        assertThat(t[0]).isEqualTo((byte) 0x1B);
        assertThat(t[1]).isEqualTo((byte) '@');
        // GS V 66 0: corte parcial al final.
        assertThat(t[t.length - 4]).isEqualTo((byte) 0x1D);
        assertThat(t[t.length - 3]).isEqualTo((byte) 'V');
    }

    @Test
    void llevaLosDatosFiscalesEnPagina850ConTildes() {
        String texto = new String(generator.generar(comprobante(null)), Charset.forName("IBM850"));

        assertThat(texto)
                .contains("FACTURA B")
                .contains("CUIT: 30-71701573-4")
                .contains("Pto Vta: 0037   Nro: 00000021")
                .contains("1x Menú Almuerzo")
                .contains("CAE: 86390938715788")
                .contains("Ley 27.743");
        // TOTAL alineado a la derecha.
        String filaTotal = texto.lines().filter(l -> l.contains("TOTAL:")).findFirst().orElseThrow();
        assertThat(filaTotal).endsWith("68.600,00");
    }

    @Test
    void qrNativo_porDefecto_leMandaElTextoALaTicketera() {
        byte[] t = generator.generar(comprobante(URL_QR_REAL));
        String texto = new String(t, Charset.forName("ISO-8859-1"));

        // GS ( k con la función de guardar datos (cn=49, fn=80) seguida de la URL tal cual...
        assertThat(texto).contains("\u001D(k").contains(URL_QR_REAL);
        // ...y la de imprimir (fn=81). Nada de imagen.
        assertThat(texto).contains("\u001D(k\u0003\u00001Q0").doesNotContain("\u001Dv0");
    }

    @Test
    void qrImagen_conMargenBlanco_paraTicketerasSinComandoDeQr() {
        org.springframework.test.util.ReflectionTestUtils.setField(generator, "qrModo", "imagen");
        byte[] t = generator.generar(comprobante(URL_QR_REAL));

        // Cabecera de GS v 0: 1D 76 30 m xL xH yL yH; el alto en puntos está en yL/yH.
        int i = new String(t, Charset.forName("ISO-8859-1")).indexOf("\u001Dv0");
        int alto = (t[i + 6] & 0xFF) | ((t[i + 7] & 0xFF) << 8);
        // (57 cuadraditos + 4 de margen a cada lado) × 6 puntos = 390 puntos.
        assertThat(alto).isEqualTo(390);
    }

    /** Una URL real de ARCA, ya sin los campos opcionales del receptor: QR de 57×57 cuadraditos. */
    private static final String URL_QR_REAL = "https://www.afip.gob.ar/fe/qr/?p="
            + java.util.Base64.getEncoder().encodeToString(("{\"ver\":1,\"fecha\":\"2026-10-02\",\"cuit\":30717015734,"
            + "\"ptoVta\":37,\"tipoCmp\":6,\"nroCmp\":35,\"importe\":34300,\"moneda\":\"PES\",\"ctz\":1,"
            + "\"tipoCodAut\":\"E\",\"codAut\":86400941725284}").getBytes(java.nio.charset.StandardCharsets.UTF_8));

}
