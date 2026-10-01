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
    void conQr_agregaLaImagenRaster() {
        byte[] sinQr = generator.generar(comprobante(null));
        byte[] conQr = generator.generar(comprobante("https://www.afip.gob.ar/fe/qr/?p=eyJ2ZXIiOjF9"));

        assertThat(conQr.length).isGreaterThan(sinQr.length + 1000);
        assertThat(new String(conQr, Charset.forName("IBM850"))).contains("\u001Dv0");
    }
}
