package org.example.laserranitaentradas.service.factura;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FacturaPdfGeneratorTest {

    private final FacturaPdfGenerator generator = new FacturaPdfGenerator();

    private static ComprobanteFactura comprobante(List<ComprobanteFactura.Item> items) {
        return new ComprobanteFactura(
                "PARQUE RECREATIVO LA SERRANITA S.A.S.", "RUTA 5 KM43,5", "30717015734", "287116730", "30/11/2020",
                37, 21L, LocalDate.of(2026, 9, 30), "260930-6", items,
                new BigDecimal("68600.00"), new BigDecimal("11905.79"),
                "86390938715788", LocalDate.of(2026, 10, 10),
                "https://www.afip.gob.ar/fe/qr/?p=eyJ2ZXIiOjF9");
    }

    @Test
    void generaUnTicketDe80mmConLosDatosFiscales() throws Exception {
        byte[] pdf = generator.generar(comprobante(List.of(
                new ComprobanteFactura.Item(2, "General"),
                new ComprobanteFactura.Item(1, "Menú Almuerzo"))));

        // Para revisarlo a ojo: FACTURA_PDF_SALIDA=/ruta/factura.pdf mvn test
        String salida = System.getenv("FACTURA_PDF_SALIDA");
        if (salida != null) Files.write(Path.of(salida), pdf);

        PdfReader reader = new PdfReader(pdf);
        assertThat(reader.getNumberOfPages()).isEqualTo(1);
        assertThat(reader.getPageSize(1).getWidth()).isCloseTo(226.77f, org.assertj.core.data.Offset.offset(0.5f));
        String texto = new PdfTextExtractor(reader).getTextFromPage(1);
        assertThat(texto)
                .contains("FACTURA B")
                .contains("30-71701573-4")
                .contains("0037")
                .contains("00000021")
                .contains("86390938715788")
                .contains("Ley 27.743")
                .contains("General");
    }

    @Test
    void muchasLineas_siguenEnUnaSolaPaginaMasLarga() throws Exception {
        List<ComprobanteFactura.Item> items = new ArrayList<>();
        for (int i = 0; i < 60; i++) items.add(new ComprobanteFactura.Item(1, "Artículo " + i));
        byte[] corto = generator.generar(comprobante(List.of(new ComprobanteFactura.Item(1, "General"))));
        byte[] largo = generator.generar(comprobante(items));

        PdfReader readerLargo = new PdfReader(largo);
        assertThat(readerLargo.getNumberOfPages()).isEqualTo(1);
        assertThat(readerLargo.getPageSize(1).getHeight())
                .isGreaterThan(new PdfReader(corto).getPageSize(1).getHeight());
    }
}
