package org.example.laserranitaentradas.monitoreo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.laserranitaentradas.model.entity.Incidente;
import org.example.laserranitaentradas.model.entity.RegistroAuditoria;
import org.example.laserranitaentradas.repository.IncidenteRepository;
import org.example.laserranitaentradas.repository.RegistroAuditoriaRepository;
import org.example.laserranitaentradas.repository.TerminalPosRepository;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Historial de acciones, errores y estado del sistema con la app completa (filtros, seguridad,
 * logging): lo que los tests de Mockito no pueden probar.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MonitoreoIntegracionTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RegistroAuditoriaRepository auditoriaRepository;
    @Autowired private IncidenteRepository incidenteRepository;
    @Autowired private IncidenteService incidenteService;

    @Autowired private TerminalPosRepository terminalRepository;
    @Autowired private EstadoTareas estadoTareas;

    private String token() throws Exception {
        return token("admin.parque", "admin123");
    }

    /** El SUPERADMIN de data.sql ("admin"): el único que ve Sistema. */
    private String tokenSoporte() throws Exception {
        return token("admin", "admin123");
    }

    private String token(String usuario, String password) throws Exception {
        String respuesta = mockMvc.perform(post("/api/usuarios/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + usuario + "\",\"password\":\"" + password + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(respuesta).get("token").asText();
    }

    private int estado(org.springframework.test.web.servlet.RequestBuilder pedido) throws Exception {
        return mockMvc.perform(pedido).andReturn().getResponse().getStatus();
    }

    private List<RegistroAuditoria> registros(String accion) {
        return auditoriaRepository.findAll().stream().filter(r -> accion.equals(r.getAccion())).toList();
    }

    @Test
    void loginFallido_quedaEnElHistorialSinLaContrasena() throws Exception {
        mockMvc.perform(post("/api/usuarios/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"mal-puesta\"}"));

        List<RegistroAuditoria> fallidos = registros("LOGIN_FALLIDO");
        RegistroAuditoria r = fallidos.get(fallidos.size() - 1);
        assertThat(r.getUsuario()).isEqualTo("admin");
        assertThat(r.getResultado()).isEqualTo(401);
        assertThat(String.valueOf(r.getDetalle())).doesNotContain("mal-puesta");
    }

    @Test
    void cambiarElPrecioDeUnaEntrada_quedaConQuienYElAntesYDespues() throws Exception {
        String token = token();
        JsonNode tipos = objectMapper.readTree(mockMvc.perform(get("/api/tipos-entrada").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString());
        ObjectNode tipo = (ObjectNode) tipos.get(0);
        long id = tipo.get("id").asLong();
        String precioAntes = tipo.get("precio").decimalValue().stripTrailingZeros().toPlainString();
        tipo.put("precio", tipo.get("precio").decimalValue().add(java.math.BigDecimal.valueOf(1000)));

        int estado = mockMvc.perform(put("/api/tipos-entrada/" + id).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(tipo))).andReturn().getResponse().getStatus();

        assertThat(estado).isEqualTo(200);
        List<RegistroAuditoria> cambios = registros("TIPO_ENTRADA_MODIFICADO");
        RegistroAuditoria r = cambios.get(cambios.size() - 1);
        assertThat(r.getUsuario()).isEqualTo("admin.parque");
        assertThat(r.getDescripcion()).startsWith("Modificó el tipo de entrada \"");
        assertThat(r.getDetalle()).contains("Precio: $" + precioAntes + " → $");
    }

    @Test
    void unAvisoDePagos_quedaComoErrorDelArea() {
        LoggerFactory.getLogger("alertas.pagos").error("La compra {} ya estaba paga y llegó otro pago aprobado (prueba)", "TEST-1");
        incidenteService.volcar();

        Incidente i = incidenteRepository.findAll().stream()
                .filter(x -> x.getMensaje() != null && x.getMensaje().contains("TEST-1")).findFirst().orElseThrow();
        assertThat(i.getArea()).isEqualTo("PAGOS");
        assertThat(i.isResuelto()).isFalse();
    }

    @Test
    void estadoDelSistema_traeTodasLasTarjetasYPrendeLaCampanita() throws Exception {
        String token = tokenSoporte();
        LoggerFactory.getLogger("org.example.laserranitaentradas.algo").error("Error de prueba para la campanita");
        incidenteService.volcar();

        JsonNode estado = objectMapper.readTree(mockMvc.perform(get("/api/interno/sistema/estado").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString());
        JsonNode notificaciones = objectMapper.readTree(mockMvc.perform(get("/api/interno/notificaciones/resumen")
                .header("Authorization", "Bearer " + token)).andReturn().getResponse().getContentAsString());

        assertThat(estado.get("tarjetas")).hasSize(13);
        assertThat(notificaciones.get("SISTEMA").asBoolean()).isTrue();
    }

    @Test
    void sistema_esSoloDelSuperadmin_yAlAdminNoLeSuenaLaCampanita() throws Exception {
        String admin = token();
        LoggerFactory.getLogger("org.example.laserranitaentradas.algo").error("Error que el admin del parque no ve");
        incidenteService.volcar();

        assertThat(estado(get("/api/interno/sistema/estado").header("Authorization", "Bearer " + admin))).isEqualTo(403);
        JsonNode notificaciones = objectMapper.readTree(mockMvc.perform(get("/api/interno/notificaciones/resumen")
                .header("Authorization", "Bearer " + admin)).andReturn().getResponse().getContentAsString());
        assertThat(notificaciones.get("SISTEMA").asBoolean()).isFalse();
    }

    @Test
    void elSuperadmin_puedeTodoLoDeAdmin() throws Exception {
        String soporte = tokenSoporte();
        assertThat(estado(get("/api/usuarios").header("Authorization", "Bearer " + soporte))).isEqualTo(200);
        assertThat(estado(get("/api/reportes/resumen").header("Authorization", "Bearer " + soporte))).isNotIn(401, 403);
    }

    @Test
    void elAdmin_noVeNiCreaSuperadmins() throws Exception {
        String admin = token();
        JsonNode usuarios = objectMapper.readTree(mockMvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + admin))
                .andReturn().getResponse().getContentAsString());
        assertThat(usuarios.findValuesAsText("username")).contains("admin.parque").doesNotContain("admin");

        int creado = estado(post("/api/usuarios").header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"colado\",\"password\":\"123456\",\"nombre\":\"A\",\"apellido\":\"B\",\"rol\":\"SUPERADMIN\",\"activo\":true}"));
        assertThat(creado).isEqualTo(403);
    }

    @Test
    void unaRutaQueNoExiste_es404YNoQuedaComoError() throws Exception {
        long antes = incidenteRepository.count();
        assertThat(estado(get("/api/no-existe-esta-ruta").header("Authorization", "Bearer " + token()))).isEqualTo(404);
        incidenteService.volcar();
        assertThat(incidenteRepository.count()).isEqualTo(antes);
    }

    @Test
    void salud_esPublicaYDiceQueLaBaseResponde() throws Exception {
        var r = mockMvc.perform(get("/api/salud")).andReturn().getResponse();
        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(r.getContentAsString()).contains("OK");
        assertThat(r.getHeader("X-Request-Id")).matches("[0-9A-F]{6}");
    }

    @Test
    void unErrorDelNavegador_quedaComoErrorDelAreaFront() throws Exception {
        int r = estado(post("/api/errores-cliente").contentType(MediaType.APPLICATION_JSON)
                .content("{\"mensaje\":\"TypeError: x is undefined (TEST-FRONT)\",\"stack\":\"at Pos.vender\",\"pantalla\":\"/pos\",\"navegador\":\"Prueba\"}"));
        incidenteService.volcar();

        assertThat(r).isEqualTo(202);
        Incidente i = incidenteRepository.findAll().stream()
                .filter(x -> x.getMensaje() != null && x.getMensaje().contains("TEST-FRONT")).findFirst().orElseThrow();
        assertThat(i.getArea()).isEqualTo("FRONT");
        assertThat(i.getRequest()).isEqualTo("Pantalla /pos");
    }

    @Test
    void elLatidoDeUnaTerminal_quedaGuardadoConSusPendientes() throws Exception {
        String boletero = token("boletero.marta", "boletero123");
        int r = estado(post("/api/interno/terminales/latido").header("Authorization", "Bearer " + boletero).contentType(MediaType.APPLICATION_JSON)
                .content("{\"terminalId\":\"terminal-de-prueba\",\"pendientes\":2,\"conError\":0,\"pendienteDesde\":\"2026-01-01T10:00:00\"}"));

        assertThat(r).isEqualTo(204);
        var t = terminalRepository.findById("terminal-de-prueba").orElseThrow();
        assertThat(t.getPendientes()).isEqualTo(2);
        assertThat(t.getUsuario()).isEqualTo("boletero.marta");
        assertThat(registros("OTRA")).noneMatch(x -> String.valueOf(x.getDescripcion()).contains("latido"));
    }

    @Test
    void lasTareasProgramadas_aparecenConSuNombreLegible() {
        assertThat(estadoTareas.tareas()).extracting(EstadoTareas.Tarea::nombre)
                .contains("Reintento de facturas pendientes", "Registro de errores", "Alertas por mail");
    }

    @Test
    void elHistorialYLosErrores_noSonDelBoletero() throws Exception {
        assertThat(mockMvc.perform(get("/api/interno/sistema/auditoria")).andReturn().getResponse().getStatus()).isEqualTo(401);
        String boletero = objectMapper.readTree(mockMvc.perform(post("/api/usuarios/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"boletero.marta\",\"password\":\"boletero123\"}"))
                .andReturn().getResponse().getContentAsString()).get("token").asText();
        assertThat(mockMvc.perform(get("/api/interno/sistema/incidentes").header("Authorization", "Bearer " + boletero))
                .andReturn().getResponse().getStatus()).isEqualTo(403);
    }
}
