package org.example.laserranitaentradas.service.afip;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AfipSdkClientTest {

    private static final String CERT = "-----BEGIN CERTIFICATE-----\nMIIBcert\n-----END CERTIFICATE-----";
    private static final String KEY = "-----BEGIN PRIVATE KEY-----\nMIIBkey\n-----END PRIVATE KEY-----";

    @TempDir
    Path carpeta;

    private AfipSdkClient cliente(String token, String ambiente, String cert, String key) {
        AfipSdkClient c = org.mockito.Mockito.mock(AfipSdkClient.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(c, "accessToken", token);
        ReflectionTestUtils.setField(c, "environment", ambiente);
        ReflectionTestUtils.setField(c, "cuit", "30000000000");
        ReflectionTestUtils.setField(c, "cert", cert);
        ReflectionTestUtils.setField(c, "key", key);
        c.cargarCredenciales();
        return c;
    }

    private String archivo(String nombre, String contenido) throws Exception {
        Path p = carpeta.resolve(nombre);
        Files.writeString(p, contenido + "\n");
        return p.toString();
    }

    @Test
    void sinToken_apagada() {
        assertThat(cliente("", "dev", "", "").estaConfigurado()).isFalse();
    }

    @Test
    void homologacion_conToken_alcanza() {
        assertThat(cliente("tok", "dev", "", "").estaConfigurado()).isTrue();
    }

    @Test
    void produccion_sinCertificadoOClave_apagada() {
        assertThat(cliente("tok", "prod", "", "").estaConfigurado()).isFalse();
        assertThat(cliente("tok", "prod", CERT, "").estaConfigurado()).isFalse();
    }

    @Test
    void produccion_conArchivosMontados_encendida() throws Exception {
        AfipSdkClient c = cliente("tok", "prod", archivo("certificado.crt", CERT), archivo("clave.key", KEY));

        assertThat(c.estaConfigurado()).isTrue();
        assertThat(ReflectionTestUtils.getField(c, "keyPem")).isEqualTo(KEY);
    }

    @Test
    void produccion_rutaQueNoExiste_apagada() {
        assertThat(cliente("tok", "prod", "/run/afip/certificado.crt", "/run/afip/clave.key").estaConfigurado()).isFalse();
    }

    @Test
    void produccion_certificadoYClaveCruzados_apagada() throws Exception {
        assertThat(cliente("tok", "prod", archivo("a", KEY), archivo("b", CERT)).estaConfigurado()).isFalse();
    }

    /** Autofirmado, sólo para tests: vence el 3/10/2036 00:30 UTC = 2/10/2036 21:30 en Argentina. */
    private static final String CERT_REAL = "-----BEGIN CERTIFICATE-----\n" +
            "MIICFjCCAX+gAwIBAgIUDUeAmLZTCs472u+WIrTii8/+CkMwDQYJKoZIhvcNAQEL\n" +
            "BQAwHTEbMBkGA1UEAwwScHJ1ZWJhLXZlbmNpbWllbnRvMB4XDTI2MTAwNjAwMzA1\n" +
            "NVoXDTM2MTAwMzAwMzA1NVowHTEbMBkGA1UEAwwScHJ1ZWJhLXZlbmNpbWllbnRv\n" +
            "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQC3MXhDKcFZFSNmb0iLCGZV8QL0\n" +
            "agblq8ciPAkrZE7WxvzEdh8Th/0POKLnp1IDLmeGf+sJFjiOachYT8ZToOI/suoS\n" +
            "qF+8kd/CE0/wjkJJkwV65m4fE01JM3QtCT18aqCsUw6SvB5MsukKZU1W+oO4FvYj\n" +
            "LlDylK1f3fN53FKf6wIDAQABo1MwUTAdBgNVHQ4EFgQUinqiOhT2m9WltgcpllC0\n" +
            "eEstunwwHwYDVR0jBBgwFoAUinqiOhT2m9WltgcpllC0eEstunwwDwYDVR0TAQH/\n" +
            "BAUwAwEB/zANBgkqhkiG9w0BAQsFAAOBgQBZfRcJNfJ383CszXyz9xMQndYQg2GZ\n" +
            "ts27W6weLynBkO6AijRGgjBPFFAOV/QAses7aYEPHFeg3qUF49mbDpeiQme7jRd6\n" +
            "S2/TPdGMH6cqYHjzphBrSTVO7xIIEV/a6Z2KXWLC9y7R8m8TIcve70W8r3NF7OIw\n" +
            "iGdM7F2qagdzSA==\n" +
            "-----END CERTIFICATE-----\n";

    @Test
    void vencimientoDelCertificado_enHoraDeArgentina() {
        AfipSdkClient c = cliente("tok", "prod", CERT_REAL, KEY);

        // En UTC ya es el 3; en Argentina todavía es el 2: vale el de acá.
        assertThat(c.vencimientoCertificado()).contains(java.time.LocalDate.of(2036, 10, 2));
    }

    @Test
    void sinCertificado_sinVencimiento() {
        assertThat(cliente("tok", "dev", "", "").vencimientoCertificado()).isEmpty();
    }

    @Test
    void contenidoEnUnaSolaLineaYEntreComillas_seRearma() {
        String unaLinea = "\"" + KEY.replace("\n", "\\n") + "\"";

        AfipSdkClient c = cliente("tok", "prod", CERT, unaLinea);

        assertThat(c.estaConfigurado()).isTrue();
        assertThat(ReflectionTestUtils.getField(c, "keyPem")).isEqualTo(KEY);
    }
}
