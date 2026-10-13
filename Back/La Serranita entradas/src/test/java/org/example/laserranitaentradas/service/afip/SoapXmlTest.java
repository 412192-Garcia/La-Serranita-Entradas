package org.example.laserranitaentradas.service.afip;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SoapXmlTest {

    private static SoapXml.Respuesta leer(String xml) {
        return SoapXml.leer(xml.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void sobreCalificado_losCamposHeredanElNamespace() {
        String xml = SoapXml.sobre("http://ar.gov.afip.dif.FEV1/", "FECompUltimoAutorizado",
                SoapXml.campos("Auth", SoapXml.campos("Token", "t", "Sign", "s", "Cuit", "20111111112"), "PtoVta", 5, "CbteTipo", 6), true);

        assertThat(xml).contains("<FECompUltimoAutorizado xmlns=\"http://ar.gov.afip.dif.FEV1/\">"
                + "<Auth><Token>t</Token><Sign>s</Sign><Cuit>20111111112</Cuit></Auth>"
                + "<PtoVta>5</PtoVta><CbteTipo>6</CbteTipo></FECompUltimoAutorizado>");
    }

    @Test
    void sobreSinCalificar_soloElMetodoLlevaNamespace() {
        String xml = SoapXml.sobre("http://a5.soap.ws.server.puc.sr/", "getPersona_v2", SoapXml.campos("token", "t", "idPersona", 20000000001L), false);

        assertThat(xml).contains("<m:getPersona_v2 xmlns:m=\"http://a5.soap.ws.server.puc.sr/\"><token>t</token>"
                + "<idPersona>20000000001</idPersona></m:getPersona_v2>");
    }

    @Test
    void listasRepetidas_nullsAfuera_importesSinNotacionCientifica_yTextoEscapado() {
        String xml = SoapXml.sobre("urn:x", "M", SoapXml.campos(
                "Iva", SoapXml.campos("AlicIva", List.of(SoapXml.campos("Id", 5), SoapXml.campos("Id", 4))),
                "Nada", null,
                "ImpTotal", new BigDecimal("1E+3"),
                "Texto", "A&B <C>"), true);

        assertThat(xml).contains("<Iva><AlicIva><Id>5</Id></AlicIva><AlicIva><Id>4</Id></AlicIva></Iva>")
                .doesNotContain("Nada")
                .contains("<ImpTotal>1000</ImpTotal>")
                .contains("<Texto>A&amp;B &lt;C&gt;</Texto>");
    }

    @Test
    void unMapaSinOrden_noSeManda() {
        assertThatThrownBy(() -> SoapXml.sobre("urn:x", "M", Map.of("PtoVta", 1, "CbteTipo", 6), true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("orden");
        // De un solo campo no hay orden que perder.
        assertThat(SoapXml.sobre("urn:x", "M", Map.of("PtoVta", 1), true)).contains("<PtoVta>1</PtoVta>");
    }

    @Test
    void respuesta_unElementoQuedaObjeto_variosQuedanArray() {
        JsonNode r = leer("""
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                  <soap:Header><FEHeaderInfo xmlns="http://ar.gov.afip.dif.FEV1/"><ambiente>Homo</ambiente></FEHeaderInfo></soap:Header>
                  <soap:Body><FECAESolicitarResponse xmlns="http://ar.gov.afip.dif.FEV1/"><FECAESolicitarResult>
                    <FeDetResp><FECAEDetResponse><Resultado>R</Resultado>
                      <Observaciones><Obs><Code>10016</Code><Msg>uno</Msg></Obs></Observaciones>
                      <CAE /><CAEFchVto xsi:nil="true" /></FECAEDetResponse></FeDetResp>
                    <Errors><Err><Code>1</Code><Msg>a</Msg></Err><Err><Code>2</Code><Msg>b</Msg></Err></Errors>
                  </FECAESolicitarResult></FECAESolicitarResponse></soap:Body></soap:Envelope>
                """).contenido();

        JsonNode resultado = r.path("FECAESolicitarResult");
        JsonNode det = resultado.path("FeDetResp").path("FECAEDetResponse");
        assertThat(det.path("Observaciones").path("Obs").isObject()).isTrue();
        assertThat(det.path("Observaciones").path("Obs").path("Code").asInt()).isEqualTo(10016);
        assertThat(det.path("CAE").asText()).isEmpty();
        assertThat(det.path("CAEFchVto").isNull()).isTrue();
        assertThat(resultado.path("Errors").path("Err").isArray()).isTrue();
        assertThat(resultado.path("Errors").path("Err").get(1).path("Msg").asText()).isEqualTo("b");
    }

    @Test
    void falla_codigoSinPrefijo() {
        SoapXml.Respuesta r = leer("""
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"><soapenv:Body><soapenv:Fault>
                  <faultcode xmlns:ns1="http://xml.apache.org/axis/">ns1:coe.alreadyAuthenticated</faultcode>
                  <faultstring>El CEE ya posee un TA valido para el acceso al WSN solicitado</faultstring>
                </soapenv:Fault></soapenv:Body></soapenv:Envelope>
                """);

        assertThat(r.contenido()).isNull();
        assertThat(r.falla().codigo()).isEqualTo("coe.alreadyAuthenticated");
        assertThat(r.falla().mensaje()).startsWith("El CEE ya posee");
    }

    @Test
    void noEsSoap_oTraeDtd_seRechaza() {
        assertThatThrownBy(() -> leer("<html><body>502 Bad Gateway</body></html>")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> leer("<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><x>&e;</x>"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
