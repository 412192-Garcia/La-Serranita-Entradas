package org.example.laserranitaentradas.service.afip;

import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WsaaTest {

    private static CertificadosDePrueba.Par par;

    @BeforeAll
    static void generar() {
        par = CertificadosDePrueba.generar("30717015734", Instant.now().plus(365, ChronoUnit.DAYS));
    }

    @Test
    void pedidoDeTicket_conVentanaAlrededorDeAhoraEnHoraArgentina() {
        ZonedDateTime ahora = ZonedDateTime.of(2026, 10, 13, 15, 0, 7, 500, ZoneId.of("UTC"));

        String pedido = Wsaa.pedidoDeTicket("wsfe", ahora);

        assertThat(pedido)
                .contains("<uniqueId>" + ahora.toEpochSecond() + "</uniqueId>")
                .contains("<generationTime>2026-10-13T11:50:07-03:00</generationTime>")
                .contains("<expirationTime>2026-10-13T12:10:07-03:00</expirationTime>")
                .contains("<service>wsfe</service>");
    }

    @Test
    void firmar_cmsConElPedidoAdentroYFirmaQueVerificaConElCertificado() throws Exception {
        String pedido = Wsaa.pedidoDeTicket("wsfe", ZonedDateTime.now());

        CMSSignedData cms = new CMSSignedData(Base64.getDecoder().decode(Wsaa.firmar(pedido, par.certificado(), par.clave())));

        assertThat(new String((byte[]) cms.getSignedContent().getContent(), StandardCharsets.UTF_8)).isEqualTo(pedido);
        assertThat(cms.getCertificates().getMatches(null)).extracting(c -> ((X509CertificateHolder) c).getSerialNumber())
                .containsExactly(par.certificado().getSerialNumber());
        SignerInformation firmante = cms.getSignerInfos().getSigners().iterator().next();
        assertThat(firmante.verify(new JcaSimpleSignerInfoVerifierBuilder().build(par.certificado()))).isTrue();
    }

    @Test
    void leerTicket_venceEnHoraArgentina() {
        Wsaa.TicketAcceso ta = Wsaa.leerTicket("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <loginTicketResponse version="1.0"><header><source>CN=wsaahomo, O=AFIP, C=AR</source>
                  <uniqueId>1</uniqueId><generationTime>2026-10-13T03:50:00.123-03:00</generationTime>
                  <expirationTime>2026-10-13T15:50:00.123-03:00</expirationTime></header>
                <credentials><token>PD94bWwg</token><sign>a2luZw==</sign></credentials></loginTicketResponse>
                """);

        assertThat(ta.token()).isEqualTo("PD94bWwg");
        assertThat(ta.sign()).isEqualTo("a2luZw==");
        assertThat(ta.vence()).isEqualTo(LocalDateTime.of(2026, 10, 13, 15, 50, 0, 123_000_000));
    }

    @Test
    void leerTicket_incompleto_esTransitorio() {
        assertThatThrownBy(() -> Wsaa.leerTicket("<loginTicketResponse><header/></loginTicketResponse>"))
                .isInstanceOfSatisfying(AfipException.class, e -> assertThat(e.esTransitorio()).isTrue());
    }

    @Test
    void clave_pkcs1YPkcs8() throws Exception {
        assertThat(par.clavePkcs1()).startsWith("-----BEGIN RSA PRIVATE KEY-----");
        assertThat(Wsaa.clave(par.clavePkcs1())).isEqualTo(par.clave());
        assertThat(Wsaa.clave(par.clavePkcs8())).isEqualTo(par.clave());
    }

    @Test
    void sonPareja_soloConSuClave() {
        CertificadosDePrueba.Par otro = CertificadosDePrueba.generar("30717015734", Instant.now().plus(1, ChronoUnit.DAYS));

        assertThat(Wsaa.sonPareja(par.certificado(), par.clave())).isTrue();
        assertThat(Wsaa.sonPareja(par.certificado(), otro.clave())).isFalse();
    }

    @Test
    void cuitDelCertificado() {
        assertThat(Wsaa.cuitDelCertificado(par.certificado())).contains("30717015734");
        assertThat(Wsaa.cuitDelCertificado(CertificadosDePrueba.generar(null, Instant.now().plus(1, ChronoUnit.DAYS)).certificado()))
                .isEmpty();
    }
}
