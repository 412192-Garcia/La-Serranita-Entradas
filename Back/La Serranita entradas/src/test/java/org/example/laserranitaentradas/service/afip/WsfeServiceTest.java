package org.example.laserranitaentradas.service.afip;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class WsfeServiceTest {

    @Test
    void cuitValido_chequeaElDigitoVerificador() {
        assertThat(WsfeService.cuitValido("30717015734")).isTrue();
        assertThat(WsfeService.cuitValido("20409378472")).isTrue();
        assertThat(WsfeService.cuitValido("30717015735")).isFalse();
        assertThat(WsfeService.cuitValido("3071701573")).isFalse();
        assertThat(WsfeService.cuitValido("30-71701573-4")).isFalse();
        assertThat(WsfeService.cuitValido(null)).isFalse();
    }

    @Test
    void tiposDeComprobante() {
        assertThat(WsfeService.notaDeCreditoDe(WsfeService.CBTE_TIPO_FACTURA_A)).isEqualTo(WsfeService.CBTE_TIPO_NOTA_CREDITO_A);
        assertThat(WsfeService.notaDeCreditoDe(WsfeService.CBTE_TIPO_FACTURA_B)).isEqualTo(WsfeService.CBTE_TIPO_NOTA_CREDITO_B);
        assertThat(WsfeService.nombre(3)).isEqualTo("Nota de Crédito A");
        assertThat(WsfeService.nombre(6)).isEqualTo("Factura B");
        assertThat(WsfeService.esFactura(1)).isTrue();
        assertThat(WsfeService.esNotaDeCredito(1)).isFalse();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> detalleEnviado(WsfeService.Receptor receptor) throws Exception {
        ArcaClient client = mock(ArcaClient.class);
        when(client.llamarWsfe(eq("FECAESolicitar"), any())).thenReturn(new ObjectMapper().readTree(
                "{\"FeDetResp\":{\"FECAEDetResponse\":{\"Resultado\":\"A\",\"CAE\":\"1\",\"CAEFchVto\":\"20261020\"}}}"));
        new WsfeService(client).solicitarCae(new WsfeService.SolicitudCae(5, receptor != null ? 1 : 6, 2, 1L,
                LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 13),
                new BigDecimal("121"), new BigDecimal("100"), new BigDecimal("21"), null, receptor));
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(client).llamarWsfe(eq("FECAESolicitar"), captor.capture());
        Map<String, Object> req = (Map<String, Object>) captor.getValue().get("FeCAEReq");
        return (Map<String, Object>) ((Map<String, Object>) req.get("FeDetReq")).get("FECAEDetRequest");
    }

    /** Orden de FEDetRequest en el WSDL. */
    private static final List<String> ORDEN_WSDL = List.of("Concepto", "DocTipo", "DocNro", "CbteDesde", "CbteHasta",
            "CbteFch", "ImpTotal", "ImpTotConc", "ImpNeto", "ImpOpEx", "ImpTrib", "ImpIVA", "FchServDesde", "FchServHasta",
            "FchVtoPago", "MonId", "MonCotiz", "CanMisMonExt", "CondicionIVAReceptorId", "CbtesAsoc", "Tributos", "Iva",
            "Opcionales", "Compradores", "PeriodoAsoc", "Actividades");

    @SuppressWarnings("unchecked")
    @Test
    void notaDeCredito_elPedidoSaleEnElOrdenDelWsdl() throws Exception {
        ArcaClient client = mock(ArcaClient.class);
        when(client.getCuit()).thenReturn("30717015734");
        when(client.llamarWsfe(eq("FECAESolicitar"), any())).thenReturn(new ObjectMapper().readTree(
                "{\"FeDetResp\":{\"FECAEDetResponse\":{\"Resultado\":\"A\",\"CAE\":\"1\",\"CAEFchVto\":\"20261020\"}}}"));
        new WsfeService(client).solicitarCae(new WsfeService.SolicitudCae(5, 8, 2, 2L,
                LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 13),
                new BigDecimal("121.00"), new BigDecimal("100.00"), new BigDecimal("21.00"),
                new WsfeService.Asociado(6, 5, 1L, LocalDate.of(2026, 10, 12)), null));
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(client).llamarWsfe(eq("FECAESolicitar"), captor.capture());

        String xml = SoapXml.sobre(ArcaClient.NS_WSFE, "FECAESolicitar", captor.getValue(), true);

        assertThat(xml).contains("<FeCabReq><CantReg>1</CantReg><PtoVta>5</PtoVta><CbteTipo>8</CbteTipo></FeCabReq>")
                .contains("<CbtesAsoc><CbteAsoc><Tipo>6</Tipo><PtoVta>5</PtoVta><Nro>1</Nro><Cuit>30717015734</Cuit>"
                        + "<CbteFch>20261012</CbteFch></CbteAsoc></CbtesAsoc>")
                .contains("<Iva><AlicIva><Id>5</Id><BaseImp>100.00</BaseImp><Importe>21.00</Importe></AlicIva></Iva>");
        String det = xml.substring(xml.indexOf("<FECAEDetRequest>") + "<FECAEDetRequest>".length(), xml.indexOf("</FECAEDetRequest>"));
        List<String> campos = new java.util.ArrayList<>();
        // Sólo los de primer nivel de FECAEDetRequest: los de adentro de CbtesAsoc/Iva se saltean.
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("<(\\w+)>(?:[^<]*|.*?)</\\1>").matcher(det);
        while (m.find()) campos.add(m.group(1));
        assertThat(campos).containsSubsequence("ImpTrib", "ImpIVA", "CondicionIVAReceptorId", "CbtesAsoc", "Iva");
        assertThat(campos).isSortedAccordingTo(java.util.Comparator.comparingInt(ORDEN_WSDL::indexOf));
        assertThat(ORDEN_WSDL).containsAll(campos);
    }

    @Test
    void facturaA_mandaCuitYCondicionDelReceptor() throws Exception {
        Map<String, Object> det = detalleEnviado(new WsfeService.Receptor("30717015734", 1));
        assertThat(det).containsEntry("DocTipo", 80).containsEntry("DocNro", 30717015734L)
                .containsEntry("CondicionIVAReceptorId", 1);
    }

    @Test
    void facturaB_consumidorFinalSinIdentificar() throws Exception {
        Map<String, Object> det = detalleEnviado(null);
        assertThat(det).containsEntry("DocTipo", 99).containsEntry("DocNro", 0)
                .containsEntry("CondicionIVAReceptorId", 5);
    }
}
