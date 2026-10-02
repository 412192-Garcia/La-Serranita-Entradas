package org.example.laserranitaentradas.service.afip;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class AfipSdkClientTest {

    private AfipSdkClient cliente(String token, String ambiente, String cert, String key) {
        AfipSdkClient c = org.mockito.Mockito.mock(AfipSdkClient.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(c, "accessToken", token);
        ReflectionTestUtils.setField(c, "environment", ambiente);
        ReflectionTestUtils.setField(c, "cert", cert);
        ReflectionTestUtils.setField(c, "key", key);
        return c;
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
        assertThat(cliente("tok", "prod", "CERT", "").estaConfigurado()).isFalse();
        assertThat(cliente("tok", "prod", "CERT", "KEY").estaConfigurado()).isTrue();
    }
}
