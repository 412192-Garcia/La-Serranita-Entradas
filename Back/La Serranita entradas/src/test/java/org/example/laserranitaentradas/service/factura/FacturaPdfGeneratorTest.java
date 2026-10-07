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
                "https://www.afip.gob.ar/fe/qr/?p=eyJ2ZXIiOjF9", 6, null);
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
    void imprimeFechaYHoraDeEmision_ySinHoraEnLasViejas() throws Exception {
        ComprobanteFactura viejo = comprobante(List.of(new ComprobanteFactura.Item(2, "General")));
        ComprobanteFactura conHora = new ComprobanteFactura(viejo.razonSocial(), viejo.domicilio(), viejo.cuit(),
                viejo.ingresosBrutos(), viejo.inicioActividades(), 37, 21L, viejo.fechaEmision(), viejo.codigoReserva(),
                viejo.items(), viejo.total(), viejo.ivaContenido(), viejo.cae(), viejo.caeVencimiento(), viejo.qrUrl(), 6,
                null, java.time.LocalTime.of(14, 32, 10));

        String textoConHora = new com.lowagie.text.pdf.parser.PdfTextExtractor(new PdfReader(generator.generar(conHora))).getTextFromPage(1);
        String textoViejo = new com.lowagie.text.pdf.parser.PdfTextExtractor(new PdfReader(generator.generar(viejo))).getTextFromPage(1);

        assertThat(textoConHora).contains("Fecha: 30/09/2026 14:32");
        assertThat(textoViejo).contains("Fecha: 30/09/2026").doesNotContain("Fecha: 30/09/2026 ");
    }

    @Test
    void notaDeCredito_llevaSuTituloYLaFacturaQueAnula() throws Exception {
        ComprobanteFactura f = comprobante(List.of(new ComprobanteFactura.Item(2, "General")));
        ComprobanteFactura nc = new ComprobanteFactura(f.razonSocial(), f.domicilio(), f.cuit(), f.ingresosBrutos(),
                f.inicioActividades(), 37, 3L, f.fechaEmision(), f.codigoReserva(), f.items(), f.total(), f.ivaContenido(),
                f.cae(), f.caeVencimiento(), f.qrUrl(), 8, "Factura B 0037-00000021");

        String texto = new PdfTextExtractor(new PdfReader(generator.generar(nc))).getTextFromPage(1);

        assertThat(texto)
                .contains("NOTA DE CRÉDITO B")
                .contains("COD. 008")
                .contains("Comprobante asociado: Factura B 0037-00000021");
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
