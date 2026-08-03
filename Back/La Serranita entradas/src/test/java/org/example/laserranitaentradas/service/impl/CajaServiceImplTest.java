package org.example.laserranitaentradas.service.impl;

import org.example.laserranitaentradas.model.entity.Caja;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.EstadoCompra;
import org.example.laserranitaentradas.model.entity.FormaPago;
import org.example.laserranitaentradas.model.entity.Usuario;
import org.example.laserranitaentradas.repository.CajaRepository;
import org.example.laserranitaentradas.repository.CompraRepository;
import org.example.laserranitaentradas.repository.RetiroCajaRepository;
import org.example.laserranitaentradas.service.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La caja es lo que un boletero abre/cierra por turno; si el cálculo de "monto esperado"
 * se rompe, el diferencia (faltante/sobrante) que ve el admin en Reportes queda mal.
 * Estos tests cubren la parte que no es obvia: qué compras cuentan como "efectivo real
 * en el cajón" (sólo EFECTIVO_BOLETERIA no cancelada) y las validaciones de apertura/cierre.
 */
@ExtendWith(MockitoExtension.class)
class CajaServiceImplTest {

    @Mock private CajaRepository cajaRepository;
    @Mock private RetiroCajaRepository retiroCajaRepository;
    @Mock private CompraRepository compraRepository;
    @Mock private UsuarioService usuarioService;

    private CajaServiceImpl service;

    private static final Long USUARIO_ID = 2L;

    @BeforeEach
    void setUp() {
        service = new CajaServiceImpl(cajaRepository, retiroCajaRepository, compraRepository, usuarioService);
    }

    @Test
    void abrir_conMontoNegativo_rechaza() {
        assertThatThrownBy(() -> service.abrir(USUARIO_ID, new BigDecimal("-100")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void abrir_conCajaYaAbiertaParaEseUsuario_rechaza() {
        when(cajaRepository.findByUsuarioIdAndFechaCierreIsNull(USUARIO_ID))
                .thenReturn(Optional.of(new Caja()));

        assertThatThrownBy(() -> service.abrir(USUARIO_ID, new BigDecimal("5000")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void abrir_casoNormal_creaLaCajaConElMontoInicial() {
        when(cajaRepository.findByUsuarioIdAndFechaCierreIsNull(USUARIO_ID)).thenReturn(Optional.empty());
        when(usuarioService.obtenerUsuarioPorId(USUARIO_ID)).thenReturn(Optional.of(new Usuario()));
        when(cajaRepository.save(any(Caja.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(retiroCajaRepository.findAllByCajaIdOrderByFechaAsc(any())).thenReturn(List.of());

        service.abrir(USUARIO_ID, new BigDecimal("5000"));

        ArgumentCaptor<Caja> captor = ArgumentCaptor.forClass(Caja.class);
        verify(cajaRepository).save(captor.capture());
        assertThat(captor.getValue().getMontoInicial()).isEqualByComparingTo("5000");
        assertThat(captor.getValue().getFechaCierre()).isNull();
    }

    @Test
    void registrarRetiro_conMontoCero_rechaza() {
        assertThatThrownBy(() -> service.registrarRetiro(USUARIO_ID, BigDecimal.ZERO, "motivo"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registrarRetiro_conMotivoVacio_rechaza() {
        assertThatThrownBy(() -> service.registrarRetiro(USUARIO_ID, new BigDecimal("1000"), "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cerrar_conMontoContadoNegativo_rechaza() {
        assertThatThrownBy(() -> service.cerrar(USUARIO_ID, new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cerrar_soloCuentaEfectivoBoleteriaNoCancelado_paraElMontoEsperado() {
        Caja cajaAbierta = new Caja();
        cajaAbierta.setId(7L);
        cajaAbierta.setMontoInicial(new BigDecimal("5000"));
        when(cajaRepository.findByUsuarioIdAndFechaCierreIsNull(USUARIO_ID)).thenReturn(Optional.of(cajaAbierta));

        Compra efectivoValida = compraConMontoFormaEstado("68600", FormaPago.EFECTIVO_BOLETERIA, EstadoCompra.VENDIDO_EN_PUERTA);
        Compra efectivoCancelada = compraConMontoFormaEstado("34300", FormaPago.EFECTIVO_BOLETERIA, EstadoCompra.CANCELADO);
        Compra tarjeta = compraConMontoFormaEstado("102900", FormaPago.TARJETA, EstadoCompra.VENDIDO_EN_PUERTA);
        when(compraRepository.findAllByCajaId(7L)).thenReturn(List.of(efectivoValida, efectivoCancelada, tarjeta));
        when(retiroCajaRepository.findAllByCajaIdOrderByFechaAsc(7L)).thenReturn(List.of());
        when(cajaRepository.save(any(Caja.class))).thenAnswer(inv -> inv.getArgument(0));

        service.cerrar(USUARIO_ID, new BigDecimal("73600"));

        // esperado = 5000 (inicial) + 68600 (única venta efectivo no cancelada) - 0 (retiros) = 73600
        assertThat(cajaAbierta.getMontoEsperado()).isEqualByComparingTo("73600");
        assertThat(cajaAbierta.getMontoContado()).isEqualByComparingTo("73600");
        assertThat(cajaAbierta.getDiferencia()).isEqualByComparingTo("0");
        assertThat(cajaAbierta.getFechaCierre()).isNotNull();
    }

    @Test
    void cerrar_restaLosRetirosDelMontoEsperado() {
        Caja cajaAbierta = new Caja();
        cajaAbierta.setId(7L);
        cajaAbierta.setMontoInicial(new BigDecimal("5000"));
        when(cajaRepository.findByUsuarioIdAndFechaCierreIsNull(USUARIO_ID)).thenReturn(Optional.of(cajaAbierta));
        when(compraRepository.findAllByCajaId(7L)).thenReturn(List.of(
                compraConMontoFormaEstado("68600", FormaPago.EFECTIVO_BOLETERIA, EstadoCompra.VENDIDO_EN_PUERTA)));

        var retiro = new org.example.laserranitaentradas.model.entity.RetiroCaja();
        retiro.setMonto(new BigDecimal("10000"));
        when(retiroCajaRepository.findAllByCajaIdOrderByFechaAsc(7L)).thenReturn(List.of(retiro));
        when(cajaRepository.save(any(Caja.class))).thenAnswer(inv -> inv.getArgument(0));

        service.cerrar(USUARIO_ID, new BigDecimal("60000"));

        // esperado = 5000 + 68600 - 10000 = 63600; diferencia = 60000 - 63600 = -3600 (faltante)
        assertThat(cajaAbierta.getMontoEsperado()).isEqualByComparingTo("63600");
        assertThat(cajaAbierta.getDiferencia()).isEqualByComparingTo("-3600");
    }

    private Compra compraConMontoFormaEstado(String monto, FormaPago formaPago, EstadoCompra estado) {
        Compra compra = new Compra();
        compra.setMontoTotal(new BigDecimal(monto));
        compra.setFormaPago(formaPago);
        compra.setEstado(estado);
        return compra;
    }
}
