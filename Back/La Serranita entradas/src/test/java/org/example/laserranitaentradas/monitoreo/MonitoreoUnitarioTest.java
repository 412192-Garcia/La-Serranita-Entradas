package org.example.laserranitaentradas.monitoreo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.laserranitaentradas.model.entity.Incidente;
import org.example.laserranitaentradas.model.entity.RegistroAuditoria;
import org.example.laserranitaentradas.repository.IncidenteRepository;
import org.example.laserranitaentradas.repository.RegistroAuditoriaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MonitoreoUnitarioTest {

    private final AuditoriaService auditoria = new AuditoriaService(mock(RegistroAuditoriaRepository.class), new ObjectMapper());

    @AfterEach
    void limpiar() {
        AuditoriaContexto.tomar();
    }

    private static AuditoriaService.Pedido pedido(String metodo, String patron, String uri, Map<String, String> vars, int estado, String cuerpo) {
        return new AuditoriaService.Pedido(metodo, patron, uri, vars, estado, "admin", "ADMIN", "10.0.0.1",
                cuerpo == null ? null : cuerpo.getBytes(StandardCharsets.UTF_8));
    }

    // ---------- historial ----------

    @Test
    void cancelarVenta_conReferenciaYDetalleDelServicio() {
        AuditoriaContexto.iniciar();
        AuditoriaContexto.referencia("#261005-3");
        AuditoriaContexto.cambio("Total", new java.math.BigDecimal("34300.00"), new java.math.BigDecimal("34300"));
        AuditoriaContexto.cambio("Forma de pago", "EFECTIVO_BOLETERIA", "TARJETA");

        RegistroAuditoria r = auditoria.armar(pedido("PUT", "/api/interno/compras/{id}/cancelar-venta",
                "/api/interno/compras/9/cancelar-venta", Map.of("id", "9"), 200, null), AuditoriaContexto.tomar());

        assertThat(r.getAccion()).isEqualTo("VENTA_CANCELADA");
        assertThat(r.getDescripcion()).isEqualTo("Canceló la venta #261005-3");
        assertThat(r.getEntidadId()).isEqualTo("#261005-3");
        // El total no cambió (34300.00 = 34300): no se anota.
        assertThat(r.getDetalle()).isEqualTo("Forma de pago: EFECTIVO_BOLETERIA → TARJETA");
        assertThat(r.getIp()).isEqualTo("10.0.0.1");
    }

    @Test
    void sinDetalleDelServicio_resumeLoEnviado_tapandoLoSensible() {
        RegistroAuditoria r = auditoria.armar(pedido("POST", "/api/usuarios", "/api/usuarios", null, 200,
                "{\"username\":\"nuevo\",\"password\":\"secreta\",\"rol\":\"BOLETERO\"}"), null);

        assertThat(r.getAccion()).isEqualTo("USUARIO_CREADO");
        assertThat(r.getDetalle()).contains("username: nuevo").contains("password: (oculto)").contains("rol: BOLETERO")
                .doesNotContain("secreta");
    }

    @Test
    void operacionQueFallo_seAnotaComoIntento() {
        RegistroAuditoria r = auditoria.armar(pedido("POST", "/api/interno/compras/{id}/reembolsar",
                "/api/interno/compras/9/reembolsar", Map.of("id", "9"), 400, null), null);

        assertThat(r.getDescripcion()).isEqualTo("Intentó: reembolsó la compra (ID 9)");
        assertThat(r.getResultado()).isEqualTo(400);
    }

    @Test
    void rutaQueNoEstaEnElCatalogo_igualSeRegistra() {
        RegistroAuditoria r = auditoria.armar(pedido("POST", "/api/interno/algo-nuevo", "/api/interno/algo-nuevo", null, 200, null), null);

        assertThat(r.getAccion()).isEqualTo("OTRA");
        assertThat(r.getDescripcion()).isEqualTo("POST /api/interno/algo-nuevo");
    }

    @Test
    void login_okYFallido() {
        String cuerpo = "{\"username\":\"marta\",\"password\":\"x\"}";
        RegistroAuditoria ok = auditoria.armar(new AuditoriaService.Pedido("POST", "/api/usuarios/login", "/api/usuarios/login",
                null, 200, null, null, "1.1.1.1", cuerpo.getBytes(StandardCharsets.UTF_8)), null);
        RegistroAuditoria mal = auditoria.armar(new AuditoriaService.Pedido("POST", "/api/usuarios/login", "/api/usuarios/login",
                null, 401, null, null, "1.1.1.1", cuerpo.getBytes(StandardCharsets.UTF_8)), null);

        assertThat(ok.getAccion()).isEqualTo("LOGIN_OK");
        assertThat(mal.getAccion()).isEqualTo("LOGIN_FALLIDO");
        assertThat(mal.getUsuario()).isEqualTo("marta");
        assertThat(mal.getDetalle()).isNull();
    }

    @Test
    void fueraDeUnaRequest_elContextoNoHaceNada() {
        AuditoriaContexto.referencia("x");
        AuditoriaContexto.cambio("a", 1, 2);
        assertThat(AuditoriaContexto.tomar()).isNull();
    }

    // ---------- errores ----------

    private static IncidenteService.Evento evento(String logger, String mensaje, String request) {
        return new IncidenteService.Evento(IncidenteService.area(logger), "ERROR", logger, mensaje, "java.lang.IllegalStateException",
                "stack", null, "admin", request, LocalDateTime.now());
    }

    @Test
    void area_porElLoggerQueLoRegistro() {
        assertThat(IncidenteService.area("alertas.pagos")).isEqualTo("PAGOS");
        assertThat(IncidenteService.area("org.example.laserranitaentradas.service.impl.MercadoPagoServiceImpl")).isEqualTo("PAGOS");
        assertThat(IncidenteService.area("org.example.laserranitaentradas.service.impl.FacturaServiceImpl")).isEqualTo("FACTURACION");
        assertThat(IncidenteService.area("org.example.laserranitaentradas.service.impresion.ImpresionService")).isEqualTo("IMPRESION");
        assertThat(IncidenteService.area("org.example.laserranitaentradas.service.impl.EmailServiceImpl")).isEqualTo("MAILS");
        assertThat(IncidenteService.area("org.example.laserranitaentradas.controller.ManejadorGlobalErrores")).isEqualTo("SISTEMA");
    }

    @Test
    void elMismoErrorConOtrosNumeros_tieneLaMismaFirma() {
        String a = IncidenteService.firma(evento("x.Y", "No se pudo facturar la compra ID 123 de pepe@mail.com", "PUT /api/interno/compras/9/editar-venta"));
        String b = IncidenteService.firma(evento("x.Y", "No se pudo facturar la compra ID 456 de otro@mail.com", "PUT /api/interno/compras/77/editar-venta"));
        String c = IncidenteService.firma(evento("x.Y", "Otra cosa distinta", null));

        assertThat(a).isEqualTo(b).isNotEqualTo(c);
    }

    @Test
    void repetidos_seAgrupanEnElMismoRegistro() {
        IncidenteRepository repo = mock(IncidenteRepository.class);
        IncidenteService servicio = new IncidenteService(repo, mock(org.springframework.transaction.PlatformTransactionManager.class));
        Incidente existente = new Incidente();
        existente.setCantidad(3);
        existente.setPrimeraVez(LocalDateTime.now().minusHours(1));
        when(repo.findFirstByFirmaAndResueltoFalseOrderByIdDesc(any())).thenReturn(Optional.of(existente));

        servicio.guardar(evento("x.Y", "falló algo 1", null));

        assertThat(existente.getCantidad()).isEqualTo(4);
        verify(repo).save(existente);
    }

    @Test
    void conLaBaseCaida_noSePierdenErrores_yNoSeReintentaUnoPorUno() {
        IncidenteRepository repo = mock(IncidenteRepository.class);
        org.springframework.transaction.PlatformTransactionManager tm = mock(org.springframework.transaction.PlatformTransactionManager.class);
        when(tm.getTransaction(any()))
                .thenThrow(new org.springframework.transaction.CannotCreateTransactionException("Connection refused"))
                .thenReturn(mock(org.springframework.transaction.TransactionStatus.class));
        IncidenteService servicio = new IncidenteService(repo, tm);
        for (int i = 0; i < 5; i++) servicio.encolar(evento("x.Y", "falló " + i, null));

        servicio.volcar();

        // Un solo intento contra la base caída (no cinco timeouts seguidos) y los cinco siguen en la cola.
        verify(tm, times(1)).getTransaction(any());
        assertThat(servicio.enCola()).isEqualTo(5);

        servicio.volcar();

        assertThat(servicio.enCola()).isZero();
        verify(repo, times(5)).save(any());
    }

    @Test
    void avalancha_losQueNoEntranEnLaCola_quedanAvisados() {
        IncidenteRepository repo = mock(IncidenteRepository.class);
        org.springframework.transaction.PlatformTransactionManager tm = mock(org.springframework.transaction.PlatformTransactionManager.class);
        when(tm.getTransaction(any())).thenReturn(mock(org.springframework.transaction.TransactionStatus.class));
        IncidenteService servicio = new IncidenteService(repo, tm);
        for (int i = 0; i < 1_037; i++) servicio.encolar(evento("x.Y", "falló " + i, null));

        while (servicio.enCola() > 0) servicio.volcar();

        org.mockito.ArgumentCaptor<Incidente> guardados = org.mockito.ArgumentCaptor.forClass(Incidente.class);
        verify(repo, times(1_001)).save(guardados.capture());
        assertThat(guardados.getAllValues()).filteredOn(i -> i.getMensaje().contains("se descartaron 37")).hasSize(1);
    }

    @Test
    void elMensajeLlevaLaCausaRaiz() {
        Exception ex = new org.springframework.transaction.CannotCreateTransactionException("Could not open JPA EntityManager",
                new RuntimeException("pool", new java.net.ConnectException("Connection refused")));
        String m = RegistroIncidentesAppender.conCausa("Error no controlado atendiendo la request",
                RegistroIncidentesAppender.causaRaiz(new ch.qos.logback.classic.spi.ThrowableProxy(ex)));

        assertThat(m).isEqualTo("Error no controlado atendiendo la request — causa: ConnectException: Connection refused");
        assertThat(RegistroIncidentesAppender.conCausa("algo", null)).isEqualTo("algo");
    }

    // ---------- tareas, alertas, terminales, rendimiento ----------

    @Test
    void claveDeTarea_sacaPaqueteYSufijoDeProxy() {
        assertThat(EstadoTareas.clave("org.example.laserranitaentradas.config.EmisionFacturasScheduler.emitirPendientes"))
                .isEqualTo("EmisionFacturasScheduler.emitirPendientes");
        assertThat(EstadoTareas.clave("org.example.x.ImpresionService$$SpringCGLIB$$0.latido")).isEqualTo("ImpresionService.latido");
    }

    @Test
    void asuntoDelMailDeAlertas_priorizaLoNuevoSobreLoResuelto() {
        var facturacion = new org.example.laserranitaentradas.model.dto.EstadoSistemaDTO.Tarjeta("facturacion", "Facturación", "ALERTA", "x", java.util.List.of(), null);
        var backups = new org.example.laserranitaentradas.model.dto.EstadoSistemaDTO.Tarjeta("backups", "Backups", "OK", "Al día", java.util.List.of(), null);

        assertThat(AlertasService.asunto(java.util.List.of(facturacion), java.util.List.of(), java.util.List.of(), java.util.List.of(backups)))
                .isEqualTo("Alerta: Facturación");
        assertThat(AlertasService.asunto(java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(backups)))
                .isEqualTo("Resuelto: Backups");
    }

    @Test
    void terminalSinLatidoConPendientes_esUnProblema_yAlDiaNo() {
        TerminalesService servicio = new TerminalesService(mock(org.example.laserranitaentradas.repository.TerminalPosRepository.class));
        org.springframework.test.util.ReflectionTestUtils.setField(servicio, "minutosSinLatido", 10L);
        org.springframework.test.util.ReflectionTestUtils.setField(servicio, "minutosColaTrabada", 15L);
        LocalDateTime ahora = LocalDateTime.now(AuditoriaService.ZONA).withNano(0);

        var callada = new org.example.laserranitaentradas.model.entity.TerminalPos();
        callada.setId("abc123def");
        callada.setUltimaVez(ahora.minusMinutes(40));
        callada.setPendientes(3);
        callada.setPendienteDesde(ahora.minusMinutes(45));
        var alDia = new org.example.laserranitaentradas.model.entity.TerminalPos();
        alDia.setId("xyz");
        alDia.setUltimaVez(ahora.minusMinutes(1));
        alDia.setPendientes(1);
        alDia.setPendienteDesde(ahora.minusMinutes(2));

        assertThat(servicio.problema(callada).descripcion()).startsWith("sin conexión hace 40 min con 3");
        assertThat(servicio.problema(alDia)).isNull();
    }

    @Test
    void metricas_cuentanLentasYErroresPorEndpoint() {
        MetricasRequests m = new MetricasRequests();
        org.springframework.test.util.ReflectionTestUtils.setField(m, "umbralMs", 1000L);
        m.registrar("GET /api/a", 200, 200, null, "1");
        m.registrar("GET /api/a", 1800, 500, "admin", "2");

        MetricasRequests.Endpoint e = m.resumen().endpoints().get(0);
        assertThat(e.cantidad()).isEqualTo(2);
        assertThat(e.promedioMs()).isEqualTo(1000);
        assertThat(e.maxMs()).isEqualTo(1800);
        assertThat(e.lentas()).isEqualTo(1);
        assertThat(e.errores()).isEqualTo(1);
        assertThat(m.lentasUltimaHora()).isEqualTo(1);
    }

    @Test
    void ventas_ceroOnlineCuandoLoNormalEsVender_yWebhookQueNoLlega_sonAlertas() {
        var repo = mock(org.example.laserranitaentradas.repository.CompraRepository.class);
        LocalDateTime haceUnDia = LocalDateTime.now(AuditoriaService.ZONA).minusDays(1);
        // Ventana actual: 0 pagadas; las 4 semanas anteriores: 5 cada una.
        when(repo.countByFormaPagoAndEstadoInAndFechaCreacionBetween(any(), any(), any(), any()))
                .thenAnswer(inv -> ((LocalDateTime) inv.getArgument(2)).isAfter(haceUnDia) ? 0L : 5L);
        when(repo.confirmacionesPorOrigen(any())).thenReturn(java.util.List.<Object[]>of(new Object[]{"VERIFICACION", 6L}));
        AnomaliasVentas anomalias = new AnomaliasVentas(repo);
        org.springframework.test.util.ReflectionTestUtils.setField(anomalias, "ventanaHoras", 3);
        org.springframework.test.util.ReflectionTestUtils.setField(anomalias, "minimoEsperado", 3.0);
        org.springframework.test.util.ReflectionTestUtils.setField(anomalias, "minimoConfirmaciones", 4);
        org.springframework.test.util.ReflectionTestUtils.setField(anomalias, "movimientoPuerta", 15);

        var tarjeta = anomalias.tarjeta();

        assertThat(tarjeta.estado()).isEqualTo("ALERTA");
        assertThat(tarjeta.detalles()).anyMatch(d -> d.startsWith("Ninguna venta online en las últimas 3 h") && d.contains("son 5"));
        assertThat(tarjeta.detalles()).anyMatch(d -> d.startsWith("Ninguno de los 6 pagos"));
    }

    @Test
    void ventas_ceroOnlineEnHorarioFlojo_noEsAlerta() {
        var repo = mock(org.example.laserranitaentradas.repository.CompraRepository.class);
        when(repo.confirmacionesPorOrigen(any())).thenReturn(java.util.List.of());
        AnomaliasVentas anomalias = new AnomaliasVentas(repo);
        org.springframework.test.util.ReflectionTestUtils.setField(anomalias, "ventanaHoras", 3);
        org.springframework.test.util.ReflectionTestUtils.setField(anomalias, "minimoEsperado", 3.0);

        assertThat(anomalias.tarjeta().estado()).isEqualTo("OK");
    }

    @Test
    void motivoDeUnMailFallido_quedaEnUnaLineaLegible() {
        String javaMail = "Mail server connection failed. Failed messages: org.eclipse.angus.mail.util.MailConnectException: "
                + "Couldn't connect to host, port: 127.0.0.1, 2; timeout -1;\n  nested exception is:\n\tjava.net.ConnectException: Connection refused";
        assertThat(EstadoMails.resumir(javaMail)).isEqualTo("Couldn't connect to host, port: 127.0.0.1, 2; timeout -1");
        assertThat(EstadoMails.resumir("535 Authentication failed")).isEqualTo("535 Authentication failed");
        assertThat(EstadoMails.resumir(null)).isEqualTo("sin detalle");
    }
}
