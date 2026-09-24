package org.example.laserranitaentradas.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Las reglas públicas del storefront usan path variables ({id}) que aceptan cualquier segmento,
 * y Spring Security aplica la primera regla que matchea: una regla pública mal acotada deja
 * abierto sin login un endpoint interno que comparte prefijo (ej. /api/compras/buscar, que
 * devuelve datos personales de todas las compras).
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    private int statusAnonimo(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        return mockMvc.perform(request).andReturn().getResponse().getStatus();
    }

    @Test
    void busquedaDeBoleteria_sinLogin_rechaza401() throws Exception {
        assertThat(statusAnonimo(get("/api/compras/buscar"))).isEqualTo(401);
    }

    @Test
    void fechasDeVisitaDeBoleteria_sinLogin_rechaza401() throws Exception {
        assertThat(statusAnonimo(get("/api/compras/fechas-visita"))).isEqualTo(401);
    }

    @Test
    void resumenPublicoDeCompra_sinLogin_pasaLaSeguridad() throws Exception {
        // 404 exacto (no sólo "no 401"): llegó al controller sin login y la compra no existe.
        assertThat(statusAnonimo(get("/api/compras/999999"))).isEqualTo(404);
        assertThat(statusAnonimo(get("/api/compras/999999/estado"))).isEqualTo(404);
        assertThat(statusAnonimo(get("/api/compras/codigo/260101-1"))).isEqualTo(404);
    }

    @Test
    void reembolso_sinLogin_rechaza401() throws Exception {
        assertThat(statusAnonimo(post("/api/interno/compras/1/reembolsar"))).isEqualTo(401);
    }
}
