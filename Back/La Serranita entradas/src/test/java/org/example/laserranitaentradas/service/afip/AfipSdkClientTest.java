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

    @Test
    void contenidoEnUnaSolaLineaYEntreComillas_seRearma() {
        String unaLinea = "\"" + KEY.replace("\n", "\\n") + "\"";

        AfipSdkClient c = cliente("tok", "prod", CERT, unaLinea);

        assertThat(c.estaConfigurado()).isTrue();
        assertThat(ReflectionTestUtils.getField(c, "keyPem")).isEqualTo(KEY);
    }
}
