package org.example.laserranitaentradas.service.afip;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.laserranitaentradas.model.entity.TicketAccesoArca;
import org.example.laserranitaentradas.repository.TicketAccesoArcaRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ArcaClientTest {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final String WSAA = "https://wsaahomo.afip.gov.ar/ws/services/LoginCms";
    private static final String WSFE = "https://wswhomo.afip.gov.ar/wsfev1/service.asmx";
    private static final String PADRON = "https://awshomo.afip.gov.ar/sr-padron/webservices/personaServiceA5";

    private static CertificadosDePrueba.Par par;

    @TempDir
    Path carpeta;

    private TicketAccesoArcaRepository repo;
    private MockRestServiceServer arca;
    private RestClient restClient;

    @BeforeAll
    static void generar() {
        par = CertificadosDePrueba.generar("20111111112", Instant.parse("2036-10-03T00:30:55Z"));
    }

    @BeforeEach
    void setUp() {
        repo = mock(TicketAccesoArcaRepository.class);
        when(repo.findById(anyString())).thenReturn(Optional.empty());
        RestClient.Builder builder = RestClient.builder();
        arca = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
    }

    private ArcaClient cliente(String ambiente, String cuit, String cert, String key) {
        return new ArcaClient(restClient, repo, mock(PlatformTransactionManager.class), ambiente, cuit, cert, key);
    }

    private ArcaClient cliente() {
        return cliente("dev", "", par.certPem(), par.clavePkcs8());
    }

    private String archivo(String nombre, String contenido) throws Exception {
        Path p = carpeta.resolve(nombre);
        Files.writeString(p, contenido + "\n");
        return p.toString();
    }

    // --- Certificado y clave ---

    @Test
    void sinCertificado_apagada() {
        assertThat(cliente("dev", "", "", "").estaConfigurado()).isFalse();
        assertThat(cliente("prod", "30717015734", par.certPem(), "").estaConfigurado()).isFalse();
    }

    @Test
    void conArchivosMontados_encendida_yElCuitSaleDelCertificado() throws Exception {
        ArcaClient c = cliente("prod", "", archivo("certificado.crt", par.certPem()), archivo("clave.key", par.clavePkcs8()));

        assertThat(c.estaConfigurado()).isTrue();
        assertThat(c.esProduccion()).isTrue();
        assertThat(c.getCuit()).isEqualTo("20111111112");
    }

    @Test
    void cuitConfigurado_ganaAlDelCertificado_peroTieneQueSerValido() {
        assertThat(cliente("dev", "30717015734", par.certPem(), par.clavePkcs8()).getCuit()).isEqualTo("30717015734");
        assertThat(cliente("dev", "30717015735", par.certPem(), par.clavePkcs8()).estaConfigurado()).isFalse();
    }

    @Test
    void claveEnPkcs1_tambienSirve() {
        assertThat(cliente("dev", "", par.certPem(), par.clavePkcs1()).estaConfigurado()).isTrue();
    }

    @Test
    void rutaQueNoExiste_apagada() {
        assertThat(cliente("prod", "", "/run/afip/certificado.crt", "/run/afip/clave.key").estaConfigurado()).isFalse();
    }

    @Test
    void certificadoYClaveCruzados_apagada() throws Exception {
        assertThat(cliente("prod", "", archivo("a", par.clavePkcs8()), archivo("b", par.certPem())).estaConfigurado()).isFalse();
    }

    @Test
    void claveDeOtroCertificado_apagada() {
        CertificadosDePrueba.Par otro = CertificadosDePrueba.generar("20111111112", Instant.now().plus(1, ChronoUnit.DAYS));

        assertThat(cliente("prod", "", par.certPem(), otro.clavePkcs8()).estaConfigurado()).isFalse();
    }

    @Test
    void contenidoEnUnaSolaLineaYEntreComillas_seRearma() {
        String unaLinea = "\"" + par.clavePkcs8().replace("\n", "\\n") + "\"";

        assertThat(cliente("prod", "", par.certPem(), unaLinea).estaConfigurado()).isTrue();
    }

    @Test
    void vencimientoDelCertificado_enHoraDeArgentina() {
        // En UTC ya es el 3; en Argentina todavía es el 2: vale el de acá.
        assertThat(cliente().vencimientoCertificado()).contains(LocalDate.of(2036, 10, 2));
        assertThat(cliente("dev", "", "", "").vencimientoCertificado()).isEmpty();
    }

    // --- Llamadas ---

    private static String loginCmsResponse(String token, LocalDateTime vence) {
        String ticket = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><loginTicketResponse version=\"1.0\"><header>"
                + "<expirationTime>" + vence.atZone(ZONA).toOffsetDateTime() + "</expirationTime></header>"
                + "<credentials><token>" + token + "</token><sign>firma</sign></credentials></loginTicketResponse>";
        return "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\"><soapenv:Body>"
                + "<loginCmsResponse xmlns=\"http://wsaa.view.sua.dvadac.desein.afip.gov\"><loginCmsReturn>"
                + SoapXml.escapar(ticket) + "</loginCmsReturn></loginCmsResponse></soapenv:Body></soapenv:Envelope>";
    }

    private static String ultimoAutorizadoResponse(long numero) {
        return "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>"
                + "<FECompUltimoAutorizadoResponse xmlns=\"http://ar.gov.afip.dif.FEV1/\"><FECompUltimoAutorizadoResult>"
                + "<PtoVta>5</PtoVta><CbteTipo>6</CbteTipo><CbteNro>" + numero + "</CbteNro>"
                + "</FECompUltimoAutorizadoResult></FECompUltimoAutorizadoResponse></soap:Body></soap:Envelope>";
    }

    private static String falla(String codigo, String mensaje) {
        return "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\"><soapenv:Body><soapenv:Fault>"
                + "<faultcode xmlns:ns1=\"http://xml.apache.org/axis/\">" + codigo + "</faultcode><faultstring>" + mensaje
                + "</faultstring></soapenv:Fault></soapenv:Body></soapenv:Envelope>";
    }

    private void esperarWsfe(long numero) {
        arca.expect(requestTo(WSFE)).andExpect(method(HttpMethod.POST))
                .andExpect(header("SOAPAction", "\"http://ar.gov.afip.dif.FEV1/FECompUltimoAutorizado\""))
                .andExpect(content().string(containsString("<Auth><Token>tok</Token><Sign>firma</Sign><Cuit>20111111112</Cuit></Auth>"
                        + "<PtoVta>5</PtoVta><CbteTipo>6</CbteTipo>")))
                .andRespond(withSuccess(ultimoAutorizadoResponse(numero), MediaType.TEXT_XML));
    }

    @Test
    void wsfe_laPrimeraVezPideElTicketYLoGuarda_despuesLoReusa() {
        LocalDateTime vence = LocalDateTime.now(ZONA).plusHours(12).withNano(0);
        arca.expect(requestTo(WSAA)).andExpect(header("SOAPAction", "\"\""))
                .andExpect(content().string(allOf(containsString("<loginCms xmlns=\"http://wsaa.view.sua.dvadac.desein.afip.gov\"><in0>"),
                        not(containsString("<in0></in0>")))))
                .andRespond(withSuccess(loginCmsResponse("tok", vence), MediaType.TEXT_XML));
        esperarWsfe(41);
        esperarWsfe(42);
        ArcaClient c = cliente();

        JsonNode primera = c.llamarWsfe("FECompUltimoAutorizado", SoapXml.campos("PtoVta", 5, "CbteTipo", 6));
        JsonNode segunda = c.llamarWsfe("FECompUltimoAutorizado", SoapXml.campos("PtoVta", 5, "CbteTipo", 6));

        arca.verify();
        assertThat(primera.path("CbteNro").asLong()).isEqualTo(41);
        assertThat(segunda.path("CbteNro").asLong()).isEqualTo(42);
        ArgumentCaptor<TicketAccesoArca> guardado = ArgumentCaptor.forClass(TicketAccesoArca.class);
        verify(repo, times(1)).save(guardado.capture());
        assertThat(guardado.getValue().getServicio()).isEqualTo("dev:wsfe");
        assertThat(guardado.getValue().getCertificado()).isEqualTo(par.certificado().getSerialNumber().toString(16));
        assertThat(guardado.getValue().getToken()).isEqualTo("tok");
        assertThat(guardado.getValue().getVence()).isEqualTo(vence);
    }

    private TicketAccesoArca ticketGuardado(String certificado, LocalDateTime vence) {
        TicketAccesoArca t = new TicketAccesoArca();
        t.setServicio("dev:wsfe");
        t.setCertificado(certificado);
        t.setToken("tok");
        t.setSign("firma");
        t.setVence(vence);
        return t;
    }

    @Test
    void ticketGuardadoVigente_despuesDeUnReinicio_noPideOtro() {
        when(repo.findById("dev:wsfe")).thenReturn(Optional.of(ticketGuardado(
                par.certificado().getSerialNumber().toString(16), LocalDateTime.now(ZONA).plusHours(3))));
        esperarWsfe(7);

        assertThat(cliente().llamarWsfe("FECompUltimoAutorizado", SoapXml.campos("PtoVta", 5, "CbteTipo", 6)).path("CbteNro").asLong())
                .isEqualTo(7);
        arca.verify();
        verify(repo, never()).save(any());
    }

    @Test
    void ticketGuardadoDeOtroCertificado_oVencido_sePideOtro() {
        when(repo.findById("dev:wsfe")).thenReturn(Optional.of(ticketGuardado("abc", LocalDateTime.now(ZONA).plusHours(3))));
        arca.expect(requestTo(WSAA)).andRespond(withSuccess(loginCmsResponse("tok", LocalDateTime.now(ZONA).plusHours(12)), MediaType.TEXT_XML));
        esperarWsfe(7);

        cliente().llamarWsfe("FECompUltimoAutorizado", SoapXml.campos("PtoVta", 5, "CbteTipo", 6));

        arca.verify();
        verify(repo).save(any());
    }

    @Test
    void ticketYaEntregadoAOtro_esTransitorioYExplicaQueHacer() {
        arca.expect(requestTo(WSAA)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.TEXT_XML)
                .body(falla("ns1:coe.alreadyAuthenticated", "El CEE ya posee un TA valido para el acceso al WSN solicitado")));

        assertThatThrownBy(() -> cliente().llamarWsfe("FECompUltimoAutorizado", SoapXml.campos("PtoVta", 5, "CbteTipo", 6)))
                .isInstanceOfSatisfying(AfipException.class, e -> {
                    assertThat(e.esTransitorio()).isTrue();
                    assertThat(e.getMessage()).contains("12 h");
                });
    }

    @Test
    void certificadoNoAutorizado_noEsTransitorio() {
        arca.expect(requestTo(WSAA)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.TEXT_XML)
                .body(falla("ns1:coe.notAuthorized", "Computador no autorizado a acceder al servicio")));

        assertThatThrownBy(() -> cliente().consultarPadron(20000000001L))
                .isInstanceOfSatisfying(AfipException.class, e -> {
                    assertThat(e.esTransitorio()).isFalse();
                    assertThat(e.getMessage()).contains("ws_sr_constancia_inscripcion").contains("autorizar");
                });
    }

    @Test
    void errorHttpSinSobreSoap_esTransitorio() {
        arca.expect(requestTo(WSAA)).andRespond(withStatus(HttpStatus.BAD_GATEWAY).contentType(MediaType.TEXT_HTML)
                .body("<html><body>502 Bad Gateway</body></html>"));

        assertThatThrownBy(() -> cliente().consultarPadron(20000000001L))
                .isInstanceOfSatisfying(AfipException.class, e -> {
                    assertThat(e.esTransitorio()).isTrue();
                    assertThat(e.getMessage()).contains("502");
                });
    }

    @Test
    void padron_camposSinCalificar_yRespuestaComoAntes() {
        arca.expect(requestTo(WSAA)).andExpect(content().string(containsString("in0")))
                .andRespond(withSuccess(loginCmsResponse("tokpadron", LocalDateTime.now(ZONA).plusHours(12)), MediaType.TEXT_XML));
        arca.expect(requestTo(PADRON)).andExpect(header("SOAPAction", "\"\""))
                .andExpect(content().string(containsString("<m:getPersona_v2 xmlns:m=\"http://a5.soap.ws.server.puc.sr/\">"
                        + "<token>tokpadron</token><sign>firma</sign><cuitRepresentada>20111111112</cuitRepresentada>"
                        + "<idPersona>30500010912</idPersona></m:getPersona_v2>")))
                .andRespond(withSuccess("""
                        <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"><soap:Body>
                        <ns2:getPersona_v2Response xmlns:ns2="http://a5.soap.ws.server.puc.sr/"><personaReturn>
                          <datosGenerales><razonSocial>EMPRESA</razonSocial></datosGenerales>
                          <datosRegimenGeneral><impuesto><idImpuesto>10</idImpuesto></impuesto><impuesto><idImpuesto>30</idImpuesto></impuesto></datosRegimenGeneral>
                        </personaReturn></ns2:getPersona_v2Response></soap:Body></soap:Envelope>
                        """, MediaType.TEXT_XML));

        JsonNode r = cliente().consultarPadron(30500010912L);

        arca.verify();
        assertThat(r.path("personaReturn").path("datosGenerales").path("razonSocial").asText()).isEqualTo("EMPRESA");
        assertThat(r.path("personaReturn").path("datosRegimenGeneral").path("impuesto").get(1).path("idImpuesto").asInt()).isEqualTo(30);
        verify(repo).save(argThat(t -> t.getServicio().equals("dev:ws_sr_constancia_inscripcion")));
    }

    @Test
    void padron_personaInexistente_elMensajeLoDice() {
        arca.expect(requestTo(WSAA)).andRespond(withSuccess(loginCmsResponse("tok", LocalDateTime.now(ZONA).plusHours(12)), MediaType.TEXT_XML));
        arca.expect(requestTo(PADRON)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.TEXT_XML)
                .body(falla("soap:Server", "No existe persona con ese Id")));

        assertThatThrownBy(() -> cliente().consultarPadron(30717015734L)).hasMessageContaining("No existe persona");
    }

    @Test
    void sinConfigurar_noLlamaAArca() {
        assertThatThrownBy(() -> cliente("dev", "", "", "").consultarPadron(30717015734L))
                .isInstanceOfSatisfying(AfipException.class, e -> assertThat(e.esTransitorio()).isFalse());
        arca.verify();
    }

    @Test
    void produccion_usaLosEndpointsDeProduccion() {
        arca.expect(requestTo("https://wsaa.afip.gov.ar/ws/services/LoginCms"))
                .andRespond(withSuccess(loginCmsResponse("tok", LocalDateTime.now(ZONA).plusHours(12)), MediaType.TEXT_XML));
        arca.expect(requestTo("https://servicios1.afip.gov.ar/wsfev1/service.asmx"))
                .andRespond(withSuccess(ultimoAutorizadoResponse(1), MediaType.TEXT_XML));

        cliente("prod", "", par.certPem(), par.clavePkcs8()).llamarWsfe("FECompUltimoAutorizado", SoapXml.campos("PtoVta", 5, "CbteTipo", 6));

        arca.verify();
        verify(repo).save(argThat(t -> t.getServicio().equals("prod:wsfe")));
    }
}
