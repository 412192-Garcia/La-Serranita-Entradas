package org.example.laserranitaentradas.service.impl;

import org.example.laserranitaentradas.model.entity.*;
import org.example.laserranitaentradas.repository.CompraRepository;
import org.example.laserranitaentradas.repository.FacturaRepository;
import org.example.laserranitaentradas.service.EmailService;
import org.example.laserranitaentradas.service.FacturaService;
import org.example.laserranitaentradas.service.afip.WsfeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La factura de una compra online se pide después de que commitea la aprobación del pago, en su
 * propia transacción: con transacciones reales (los tests de Mockito no pueden probar esto).
 * ARCA y el mail están simulados: la emisión no sale a internet.
 */
@SpringBootTest(properties = {
        "afip.access-token=solo-para-tests",
        "afip.environment=dev",
        "afip.punto-venta.online=12",
        "afip.emision.intervalo-ms=3600000",
})
class FacturacionOnlineIntegracionTest {

    @Autowired private FacturaService facturaService;
    @Autowired private CompraRepository compraRepository;
    @Autowired private FacturaRepository facturaRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockitoBean private WsfeService wsfe;
    @MockitoBean private EmailService emailService;

    @AfterEach
    void limpiar() {
        facturaRepository.deleteAll();
    }

    private Compra compraPagada(String codigo) {
        return compraRepository.save(Compra.builder()
                .codigoReserva(codigo)
                .estado(EstadoCompra.APROBADO)
                .formaPago(FormaPago.MERCADO_PAGO)
                .montoTotal(new BigDecimal("50000"))
                .contactEmail("cliente@mail.com")
                .fechaVisita(LocalDate.now().plusDays(3))
                .build());
    }

    private List<Factura> facturasDe(Long compraId) {
        return facturaRepository.findAll().stream()
                .filter(f -> f.getCompra() != null && f.getCompra().getId().equals(compraId))
                .toList();
    }

    @Test
    void laFacturaSePideRecienCuandoCommiteaLaAprobacion() {
        Compra compra = compraPagada("ONLINE-1");
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        int dentroDeLaTransaccion = tx.execute(s -> {
            facturaService.solicitarOnline(compraRepository.findById(compra.getId()).orElseThrow());
            return facturasDe(compra.getId()).size();
        });

        assertThat(dentroDeLaTransaccion).isZero();
        List<Factura> facturas = facturasDe(compra.getId());
        assertThat(facturas).hasSize(1);
        assertThat(facturas.get(0).getPuntoVenta()).isEqualTo(12);
        assertThat(facturas.get(0).getDestino()).isEqualTo(DestinoFactura.MAIL);
        assertThat(facturas.get(0).getEmail()).isEqualTo("cliente@mail.com");
    }

    @Test
    void siLaAprobacionSeDeshace_noQuedaFactura() {
        Compra compra = compraPagada("ONLINE-2");
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        tx.executeWithoutResult(s -> {
            facturaService.solicitarOnline(compraRepository.findById(compra.getId()).orElseThrow());
            s.setRollbackOnly();
        });

        assertThat(facturasDe(compra.getId())).isEmpty();
    }

    @Test
    void dosConfirmacionesDelMismoPago_unaSolaFactura() {
        Compra compra = compraPagada("ONLINE-3");

        facturaService.solicitarOnline(compra);
        facturaService.solicitarOnline(compra);

        assertThat(facturasDe(compra.getId())).hasSize(1);
    }

    @Test
    void facturarUnaCompraOnlineQueQuedoSinFactura_unaSolaVez() {
        Compra sinFactura = compraPagada("ONLINE-5");

        assertThat(facturaService.facturarOnlineAhora(sinFactura.getId()).getPuntoVenta()).isEqualTo(12);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> facturaService.facturarOnlineAhora(sinFactura.getId()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("ya tiene factura");
        assertThat(facturasDe(sinFactura.getId())).hasSize(1);
    }
}
