package org.example.laserranitaentradas.monitoreo;

import com.sun.net.httpserver.HttpServer;
import org.example.laserranitaentradas.repository.EstadoAlertaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class LatidoExternoTest {

    private HttpServer servidor;
    private final AtomicInteger estadoSalud = new AtomicInteger(200);
    private final AtomicInteger latidos = new AtomicInteger();
    private AlertasService alertas;

    @BeforeEach
    void levantar() throws Exception {
        servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.createContext("/api/salud", h -> { h.sendResponseHeaders(estadoSalud.get(), -1); h.close(); });
        servidor.createContext("/ping", h -> { latidos.incrementAndGet(); h.sendResponseHeaders(200, -1); h.close(); });
        servidor.start();
        int puerto = servidor.getAddress().getPort();

        alertas = new AlertasService(mock(EstadoSistemaService.class), mock(EstadoAlertaRepository.class),
                mock(JavaMailSender.class), mock(EstadoMails.class), mock(DataSource.class));
        ReflectionTestUtils.setField(alertas, "puerto", puerto);
        ReflectionTestUtils.setField(alertas, "latidoUrl", "http://localhost:" + puerto + "/ping");
    }

    @AfterEach
    void bajar() {
        servidor.stop(0);
    }

    @Test
    void conSaludOk_mandaElLatido() {
        alertas.latidoExterno();
        assertThat(latidos.get()).isEqualTo(1);
    }

    @Test
    void siSaludNoDa200_noMandaElLatido() {
        estadoSalud.set(503);
        alertas.latidoExterno();
        assertThat(latidos.get()).isZero();
    }

    @Test
    void siElBackendNoAtiende_noMandaElLatido() {
        ReflectionTestUtils.setField(alertas, "puerto", 1);
        alertas.latidoExterno();
        assertThat(latidos.get()).isZero();
    }
}
