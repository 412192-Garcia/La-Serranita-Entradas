package org.example.laserranitaentradas.service.afip;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
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
        AfipSdkClient client = mock(AfipSdkClient.class);
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
