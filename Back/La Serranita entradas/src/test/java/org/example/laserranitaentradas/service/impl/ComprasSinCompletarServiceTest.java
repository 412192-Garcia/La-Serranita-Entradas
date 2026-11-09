package org.example.laserranitaentradas.service.impl;

import org.example.laserranitaentradas.model.dto.ComprasSinCompletarDTO;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.EstadoCompra;
import org.example.laserranitaentradas.model.entity.FormaPago;
import org.example.laserranitaentradas.model.entity.MotivoCancelacion;
import org.example.laserranitaentradas.repository.CompraRepository;
import org.example.laserranitaentradas.repository.EventoPagoRepository;
import org.example.laserranitaentradas.model.entity.EventoPago;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ComprasSinCompletarServiceTest {

    private static final LocalDate DIA = LocalDate.of(2026, 11, 8);

    private static Compra compra(String codigo, int hora, EstadoCompra estado, MotivoCancelacion motivo, String detalle, String email) {
        Compra c = new Compra();
        c.setCodigoReserva(codigo);
        c.setFechaCreacion(DIA.atTime(hora, 0));
        c.setEstado(estado);
        c.setFormaPago(FormaPago.MERCADO_PAGO);
        c.setMontoTotal(new BigDecimal("34300"));
        c.setContactEmail(email);
        c.setDetalles(new ArrayList<>());
        if (motivo != null) c.cancelar(motivo, detalle, "SISTEMA");
        c.setEstado(estado);
        return c;
    }

    @Test
    void cuentaElEmbudoYDetectaQuienCompraDespues() {
        Compra abandonada = compra("261108-1", 10, EstadoCompra.CANCELADO, MotivoCancelacion.CHECKOUT_ABANDONADO, null, "ana@x.com");
        Compra rechazada = compra("261108-2", 11, EstadoCompra.CANCELADO, MotivoCancelacion.PAGO_RECHAZADO,
                "visa: rechazado, fondos insuficientes · master: rechazado, fondos insuficientes", "beto@x.com");
        Compra pendiente = compra("261108-3", 12, EstadoCompra.PENDIENTE_PAGO, null, null, "caro@x.com");
        Compra pagada = compra("261108-4", 13, EstadoCompra.APROBADO, null, null, "ANA@x.com");

        CompraRepository repo = mock(CompraRepository.class);
        when(repo.findOnlineCreadasEntre(eq(FormaPago.MERCADO_PAGO), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(abandonada, rechazada, pendiente, pagada));

        ComprasSinCompletarDTO r = new ComprasSinCompletarService(repo, mock(EventoPagoRepository.class)).generar(DIA, DIA);

        assertThat(r.iniciadas()).isEqualTo(4);
        assertThat(r.pagadas()).isEqualTo(1);
        assertThat(r.abandonadas()).isEqualTo(1);
        assertThat(r.rechazadas()).isEqualTo(1);
        assertThat(r.pendientes()).isEqualTo(1);
        // La abandonada compró después: sólo la rechazada cuenta como plata perdida (la pendiente todavía no).
        assertThat(r.noRecuperadas()).isEqualTo(1);
        assertThat(r.montoNoRecuperado()).isEqualByComparingTo("34300");
        assertThat(r.motivosRechazo()).extracting(ComprasSinCompletarDTO.Motivo::cantidad).containsExactly(1L, 1L);
        assertThat(r.intentos()).extracting(ComprasSinCompletarDTO.Intento::codigoReserva)
                .containsExactly("261108-3", "261108-2", "261108-1");
        assertThat(r.intentos().get(2).compraPosterior()).isEqualTo("261108-4");
        assertThat(r.porDia()).singleElement().satisfies(d -> {
            assertThat(d.iniciadas()).isEqualTo(4);
            assertThat(d.pagadas()).isEqualTo(1);
        });
    }

    @Test
    void hastaDondeLlego_usaElPasoMasAvanzado() {
        Compra c = compra("261108-5", 10, EstadoCompra.CANCELADO, MotivoCancelacion.CHECKOUT_ABANDONADO, null, null);
        assertThat(ComprasSinCompletarService.hastaDondeLlego(c, List.of())).startsWith("Sin datos");
        assertThat(ComprasSinCompletarService.hastaDondeLlego(c, List.of(evento("VENTANA_BLOQUEADA"))))
                .isEqualTo("El navegador bloqueó Mercado Pago y no lo pudo abrir");
        assertThat(ComprasSinCompletarService.hastaDondeLlego(c, List.of(evento("VENTANA_ABIERTA"), evento("VENTANA_CERRADA"))))
                .isEqualTo("Abrió Mercado Pago y lo cerró sin pagar");
    }

    @Test
    void clasificar_reconoceElNavegadorDeInstagram() {
        assertThat(SeguimientoPagoService.clasificar(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Instagram 300.0"))
                .isEqualTo("Celular · iOS · Instagram");
        assertThat(SeguimientoPagoService.clasificar(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0 Safari/537.36"))
                .isEqualTo("Computadora · Windows · Chrome");
    }

    private static EventoPago evento(String tipo) {
        return EventoPago.builder().tipo(tipo).fecha(DIA.atTime(10, 0)).build();
    }
}
