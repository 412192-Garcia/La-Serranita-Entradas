package org.example.laserranitaentradas.service.impl;

import org.example.laserranitaentradas.model.dto.CajaResponseDTO;
import org.example.laserranitaentradas.model.dto.CierrePosnetRequestDTO;
import org.example.laserranitaentradas.model.dto.ConteoDenominacionDTO;
import org.example.laserranitaentradas.model.entity.Caja;
import org.example.laserranitaentradas.model.entity.CierrePosnet;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.CompraDetalle;
import org.example.laserranitaentradas.model.entity.EstadoCompra;
import org.example.laserranitaentradas.model.entity.FormaPago;
import org.example.laserranitaentradas.model.entity.IngresoEntradas;
import org.example.laserranitaentradas.model.entity.RetiroCaja;
import org.example.laserranitaentradas.model.entity.TipoEntrada;
import org.example.laserranitaentradas.model.entity.TipoMovimientoCaja;
import org.example.laserranitaentradas.model.entity.TipoMovimientoEntradas;
import org.example.laserranitaentradas.model.entity.Usuario;
import org.example.laserranitaentradas.repository.CajaRepository;
import org.example.laserranitaentradas.repository.CierrePosnetRepository;
import org.example.laserranitaentradas.repository.CompraRepository;
import org.example.laserranitaentradas.repository.IngresoEntradasRepository;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La caja es lo que un boletero abre/cierra por turno; si el cálculo de "monto esperado"
 * se rompe, el diferencia (faltante/sobrante) que ve el admin en Reportes queda mal.
 * Estos tests cubren la parte que no es obvia: qué compras cuentan como "efectivo real
 * en el cajón" (sólo EFECTIVO_BOLETERIA no cancelada), el conteo de efectivo por
 * denominación, la reconciliación de tarjeta/QR contra el posnet, la diferencia de
 * entradas físicas, y las validaciones de apertura/cierre.
 */
@ExtendWith(MockitoExtension.class)
class CajaServiceImplTest {

    @Mock private CajaRepository cajaRepository;
    @Mock private RetiroCajaRepository retiroCajaRepository;
    @Mock private CierrePosnetRepository cierrePosnetRepository;
    @Mock private IngresoEntradasRepository ingresoEntradasRepository;
    @Mock private CompraRepository compraRepository;
    @Mock private UsuarioService usuarioService;

    private CajaServiceImpl service;

    private static final Long USUARIO_ID = 2L;

    @BeforeEach
    void setUp() {
        service = new CajaServiceImpl(cajaRepository, retiroCajaRepository, cierrePosnetRepository, ingresoEntradasRepository, compraRepository, usuarioService);
    }

    @Test
    void abrir_conMontoNegativo_rechaza() {
        assertThatThrownBy(() -> service.abrir(USUARIO_ID, new BigDecimal("-100"), 50))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void abrir_sinEntradasFisicasIniciales_rechaza() {
        assertThatThrownBy(() -> service.abrir(USUARIO_ID, new BigDecimal("5000"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void abrir_conCajaYaAbiertaParaEseUsuario_rechaza() {
        when(cajaRepository.findByUsuarioIdAndFechaCierreIsNull(USUARIO_ID))
                .thenReturn(Optional.of(new Caja()));

        assertThatThrownBy(() -> service.abrir(USUARIO_ID, new BigDecimal("5000"), 50))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void abrir_casoNormal_creaLaCajaConElMontoInicialYLasEntradasFisicas() {
        when(cajaRepository.findByUsuarioIdAndFechaCierreIsNull(USUARIO_ID)).thenReturn(Optional.empty());
        when(usuarioService.obtenerUsuarioPorId(USUARIO_ID)).thenReturn(Optional.of(new Usuario()));
        when(cajaRepository.save(any(Caja.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(retiroCajaRepository.findAllByCajaIdOrderByFechaAsc(any())).thenReturn(List.of());

        service.abrir(USUARIO_ID, new BigDecimal("5000"), 50);

        ArgumentCaptor<Caja> captor = ArgumentCaptor.forClass(Caja.class);
        verify(cajaRepository).save(captor.capture());
        assertThat(captor.getValue().getMontoInicial()).isEqualByComparingTo("5000");
        assertThat(captor.getValue().getEntradasFisicasInicial()).isEqualTo(50);
        assertThat(captor.getValue().getFechaCierre()).isNull();
    }

    @Test
    void registrarRetiro_conMontoCero_rechaza() {
        assertThatThrownBy(() -> service.registrarRetiro(USUARIO_ID, BigDecimal.ZERO, "motivo", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registrarRetiro_conMotivoVacio_rechaza() {
        assertThatThrownBy(() -> service.registrarRetiro(USUARIO_ID, new BigDecimal("1000"), "   ", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registrarRetiroComoAdmin_enCajaAjena_laRegistraSinChequearDueno() {
        Caja cajaDeOtro = cajaAbierta(7L, "5000", 50);
        cajaDeOtro.setUsuario(usuarioConId(99L));
        when(cajaRepository.findById(7L)).thenReturn(Optional.of(cajaDeOtro));
        when(compraRepository.findAllByCajaId(7L)).thenReturn(List.of());
        when(retiroCajaRepository.findAllByCajaIdOrderByFechaAsc(7L)).thenReturn(List.of());
        when(ingresoEntradasRepository.findAllByCajaIdOrderByFechaAsc(7L)).thenReturn(List.of());

        service.registrarRetiroComoAdmin(7L, new BigDecimal("2000"), "motivo", TipoMovimientoCaja.RETIRO);

        ArgumentCaptor<RetiroCaja> captor = ArgumentCaptor.forClass(RetiroCaja.class);
        verify(retiroCajaRepository).save(captor.capture());
        assertThat(captor.getValue().getCaja()).isEqualTo(cajaDeOtro);
    }

    @Test
    void registrarRetiroComoAdmin_enCajaYaCerrada_rechaza() {
        Caja cajaCerrada = cajaCerradaDeUsuario(7L, 99L);
        when(cajaRepository.findById(7L)).thenReturn(Optional.of(cajaCerrada));

        assertThatThrownBy(() -> service.registrarRetiroComoAdmin(7L, new BigDecimal("2000"), "motivo", TipoMovimientoCaja.RETIRO))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void registrarRetiroComoAdmin_deCajaInexistente_rechaza() {
        when(cajaRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registrarRetiroComoAdmin(7L, new BigDecimal("2000"), "motivo", TipoMovimientoCaja.RETIRO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registrarIngresoEntradas_conCantidadCero_rechaza() {
        assertThatThrownBy(() -> service.registrarIngresoEntradas(USUARIO_ID, 0, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registrarIngresoEntradas_retiroSinMotivo_seGuardaIgual() {
        // El motivo es opcional también en un retiro de entradas (a diferencia del motivo de un
        // retiro de efectivo, que sí es obligatorio).
        Caja cajaAbierta = cajaAbierta(7L, "5000", 50);
        when(cajaRepository.findByUsuarioIdAndFechaCierreIsNull(USUARIO_ID)).thenReturn(Optional.of(cajaAbierta));
        when(compraRepository.findAllByCajaId(7L)).thenReturn(List.of());
        when(retiroCajaRepository.findAllByCajaIdOrderByFechaAsc(7L)).thenReturn(List.of());
        when(ingresoEntradasRepository.findAllByCajaIdOrderByFechaAsc(7L)).thenReturn(List.of());

        service.registrarIngresoEntradas(USUARIO_ID, 10, null, TipoMovimientoEntradas.RETIRO, null, null);

        ArgumentCaptor<IngresoEntradas> captor = ArgumentCaptor.forClass(IngresoEntradas.class);
        verify(ingresoEntradasRepository).save(captor.capture());
        assertThat(captor.getValue().getTipo()).isEqualTo(TipoMovimientoEntradas.RETIRO);
        assertThat(captor.getValue().getMotivo()).isNull();
    }

    @Test
    void registrarIngresoEntradas_retiroMayorAlStockActual_seGuardaIgual() {
        // El servidor no bloquea un retiro que deja el conteo en negativo: el frontend ya avisó
        // y pidió confirmación antes de mandarlo (ver stockActual en el modal).
        Caja cajaAbierta = cajaAbierta(7L, "5000", 50);
        when(cajaRepository.findByUsuarioIdAndFechaCierreIsNull(USUARIO_ID)).thenReturn(Optional.of(cajaAbierta));
        when(compraRepository.findAllByCajaId(7L)).thenReturn(List.of());
        when(retiroCajaRepository.findAllByCajaIdOrderByFechaAsc(7L)).thenReturn(List.of());
        when(ingresoEntradasRepository.findAllByCajaIdOrderByFechaAsc(7L)).thenReturn(List.of());

        service.registrarIngresoEntradas(USUARIO_ID, 51, null, TipoMovimientoEntradas.RETIRO, null, null);

        ArgumentCaptor<IngresoEntradas> captor = ArgumentCaptor.forClass(IngresoEntradas.class);
        verify(ingresoEntradasRepository).save(captor.capture());
        assertThat(captor.getValue().getCantidad()).isEqualTo(51);
    }

    @Test
    void cerrar_conDenominacionInvalida_rechaza() {
        Caja cajaAbierta = cajaAbierta(7L, "5000", 50);
        when(cajaRepository.findByUsuarioIdAndFechaCierreIsNull(USUARIO_ID)).thenReturn(Optional.of(cajaAbierta));

        assertThatThrownBy(() -> service.cerrar(USUARIO_ID, List.of(conteo(300, 2)), List.of(), 50, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cerrar_sinEntradasFisicasFinal_rechaza() {
        Caja cajaAbierta = cajaAbierta(7L, "5000", 50);
        when(cajaRepository.findByUsuarioIdAndFechaCierreIsNull(USUARIO_ID)).thenReturn(Optional.of(cajaAbierta));

        assertThatThrownBy(() -> service.cerrar(USUARIO_ID, List.of(conteo(1000, 5)), List.of(), null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cerrar_conCierreDePosnetDeFormaPagoInvalida_rechaza() {
        Caja cajaAbierta = cajaAbierta(7L, "5000", 50);
        when(cajaRepository.findByUsuarioIdAndFechaCierreIsNull(USUARIO_ID)).thenReturn(Optional.of(cajaAbierta));

        CierrePosnetRequestDTO cierreInvalido = new CierrePosnetRequestDTO();
        cierreInvalido.setFormaPago(FormaPago.EFECTIVO_BOLETERIA);
        cierreInvalido.setMonto(new BigDecimal("1000"));

        assertThatThrownBy(() -> service.cerrar(USUARIO_ID, List.of(), List.of(cierreInvalido), 50, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cerrar_calculaMontoContadoComoLaSumaDeDenominacionPorCantidad() {
        Caja cajaAbierta = cajaAbierta(7L, "5000", 50);
        when(cajaRepository.findByUsuarioIdAndFechaCierreIsNull(USUARIO_ID)).thenReturn(Optional.of(cajaAbierta));

        Compra efectivoValida = compraConMontoFormaEstado("68600", FormaPago.EFECTIVO_BOLETERIA, EstadoCompra.VENDIDO_EN_PUERTA);
        Compra efectivoCancelada = compraConMontoFormaEstado("34300", FormaPago.EFECTIVO_BOLETERIA, EstadoCompra.CANCELADO);
        Compra tarjeta = compraConMontoFormaEstado("102900", FormaPago.TARJETA, EstadoCompra.VENDIDO_EN_PUERTA);
        when(compraRepository.findAllByCajaId(7L)).thenReturn(List.of(efectivoValida, efectivoCancelada, tarjeta));
        when(retiroCajaRepository.findAllByCajaIdOrderByFechaAsc(7L)).thenReturn(List.of());
        when(cajaRepository.save(any(Caja.class))).thenAnswer(inv -> inv.getArgument(0));

        // 736 billetes de 100 = 73600
        service.cerrar(USUARIO_ID, List.of(conteo(100, 736)), List.of(), 50, null, null);

        // esperado = 5000 (inicial) + 68600 (única venta efectivo no cancelada) - 0 (retiros) = 73600
        assertThat(cajaAbierta.getMontoEsperado()).isEqualByComparingTo("73600");
        assertThat(cajaAbierta.getMontoContado()).isEqualByComparingTo("73600");
        assertThat(cajaAbierta.getDiferencia()).isEqualByComparingTo("0");
        assertThat(cajaAbierta.getFechaCierre()).isNotNull();
    }

    @Test
    void cerrar_sumaElCambioContadoAlMontoContado() {
        // El boletero no cuenta los billetes chicos uno por uno, carga el total directo.
        Caja cajaAbierta = cajaAbierta(7L, "5000", 50);
        when(cajaRepository.findByUsuarioIdAndFechaCierreIsNull(USUARIO_ID)).thenReturn(Optional.of(cajaAbierta));
        when(compraRepository.findAllByCajaId(7L)).thenReturn(List.of());
        when(retiroCajaRepository.findAllByCajaIdOrderByFechaAsc(7L)).thenReturn(List.of());
        when(cajaRepository.save(any(Caja.class))).thenAnswer(inv -> inv.getArgument(0));

        // 10 billetes de 1000 = 10000, más 1500 de cambio = 11500
        var respuesta = service.cerrar(USUARIO_ID, List.of(conteo(1000, 10)), List.of(), 50, new BigDecimal("1500"), null);

        assertThat(respuesta.getMontoContado()).isEqualByComparingTo("11500");
        assertThat(respuesta.getCambioContado()).isEqualByComparingTo("1500");
    }

    @Test
    void cerrar_restaLosRetirosDelMontoEsperado() {
        Caja cajaAbierta = cajaAbierta(7L, "5000", 50);
        when(cajaRepository.findByUsuarioIdAndFechaCierreIsNull(USUARIO_ID)).thenReturn(Optional.of(cajaAbierta));
        when(compraRepository.findAllByCajaId(7L)).thenReturn(List.of(
                compraConMontoFormaEstado("68600", FormaPago.EFECTIVO_BOLETERIA, EstadoCompra.VENDIDO_EN_PUERTA)));

        var retiro = new org.example.laserranitaentradas.model.entity.RetiroCaja();
        retiro.setMonto(new BigDecimal("10000"));
        when(retiroCajaRepository.findAllByCajaIdOrderByFechaAsc(7L)).thenReturn(List.of(retiro));
        when(cajaRepository.save(any(Caja.class))).thenAnswer(inv -> inv.getArgument(0));

        // 600 billetes de 100 = 60000
        service.cerrar(USUARIO_ID, List.of(conteo(100, 600)), List.of(), 50, null, null);

        // esperado = 5000 + 68600 - 10000 = 63600; diferencia = 60000 - 63600 = -3600 (faltante)
        assertThat(cajaAbierta.getMontoEsperado()).isEqualByComparingTo("63600");
        assertThat(cajaAbierta.getDiferencia()).isEqualByComparingTo("-3600");
    }

    @Test
    void cerrar_sumaLosAportesAlMontoEsperadoEnVezDeRestarlos() {
        // Un aporte es plata que entra a la caja: al revés de un retiro, tiene que sumar al
        // esperado, no restar.
        Caja cajaAbierta = cajaAbierta(7L, "5000", 50);
        when(cajaRepository.findByUsuarioIdAndFechaCierreIsNull(USUARIO_ID)).thenReturn(Optional.of(cajaAbierta));
        when(compraRepository.findAllByCajaId(7L)).thenReturn(List.of(
                compraConMontoFormaEstado("68600", FormaPago.EFECTIVO_BOLETERIA, EstadoCompra.VENDIDO_EN_PUERTA)));

        var retiro = new RetiroCaja();
        retiro.setMonto(new BigDecimal("10000"));
        retiro.setTipo(TipoMovimientoCaja.RETIRO);
        var aporte = new RetiroCaja();
        aporte.setMonto(new BigDecimal("3000"));
        aporte.setTipo(TipoMovimientoCaja.APORTE);
        when(retiroCajaRepository.findAllByCajaIdOrderByFechaAsc(7L)).thenReturn(List.of(retiro, aporte));
        when(cajaRepository.save(any(Caja.class))).thenAnswer(inv -> inv.getArgument(0));

        service.cerrar(USUARIO_ID, List.of(), List.of(), 50, null, null);

        // esperado = 5000 + 68600 - 10000 (retiro) + 3000 (aporte) = 66600
        assertThat(cajaAbierta.getMontoEsperado()).isEqualByComparingTo("66600");
    }

    @Test
    void registrarRetiro_guardaElTipoIndicado() {
        Caja cajaAbierta = cajaAbierta(7L, "5000", 50);
        when(cajaRepository.findByUsuarioIdAndFechaCierreIsNull(USUARIO_ID)).thenReturn(Optional.of(cajaAbierta));
        when(compraRepository.findAllByCajaId(7L)).thenReturn(List.of());
        when(retiroCajaRepository.findAllByCajaIdOrderByFechaAsc(7L)).thenReturn(List.of());
        when(ingresoEntradasRepository.findAllByCajaIdOrderByFechaAsc(7L)).thenReturn(List.of());

        service.registrarRetiro(USUARIO_ID, new BigDecimal("2000"), "vuelto extra", TipoMovimientoCaja.APORTE, null, null);

        ArgumentCaptor<RetiroCaja> captor = ArgumentCaptor.forClass(RetiroCaja.class);
        verify(retiroCajaRepository).save(captor.capture());
        assertThat(captor.getValue().getTipo()).isEqualTo(TipoMovimientoCaja.APORTE);
    }

    @Test
    void registrarRetiro_sinTipo_guardaComoRetiro() {
        // Compatibilidad: si no se manda tipo, se asume RETIRO (comportamiento de siempre).
        Caja cajaAbierta = cajaAbierta(7L, "5000", 50);
        when(cajaRepository.findByUsuarioIdAndFechaCierreIsNull(USUARIO_ID)).thenReturn(Optional.of(cajaAbierta));
        when(compraRepository.findAllByCajaId(7L)).thenReturn(List.of());
        when(retiroCajaRepository.findAllByCajaIdOrderByFechaAsc(7L)).thenReturn(List.of());
        when(ingresoEntradasRepository.findAllByCajaIdOrderByFechaAsc(7L)).thenReturn(List.of());

        service.registrarRetiro(USUARIO_ID, new BigDecimal("2000"), "motivo", null, null, null);

        ArgumentCaptor<RetiroCaja> captor = ArgumentCaptor.forClass(RetiroCaja.class);
        verify(retiroCajaRepository).save(captor.capture());
        assertThat(captor.getValue().getTipo()).isEqualTo(TipoMovimientoCaja.RETIRO);
    }

    @Test
    void registrarRetiro_conClaveYaProcesada_noDuplicaYDevuelveLoGuardado() {
        // El caso que hace segura la cola offline: la petición original llegó y se guardó, pero
        // la respuesta se perdió en el corte. El reintento trae la misma clave y no cobra dos veces.
        Caja cajaAbierta = cajaAbierta(7L, "5000", 50);
        RetiroCaja yaGuardado = RetiroCaja.builder()
                .caja(cajaAbierta)
                .monto(new BigDecimal("2000"))
                .motivo("resguardo")
                .tipo(TipoMovimientoCaja.RETIRO)
                .fecha(LocalDateTime.now())
                .idempotencyKey("clave-1")
                .build();
        when(retiroCajaRepository.findByIdempotencyKey("clave-1")).thenReturn(Optional.of(yaGuardado));
        when(compraRepository.findAllByCajaId(7L)).thenReturn(List.of());
        when(retiroCajaRepository.findAllByCajaIdOrderByFechaAsc(7L)).thenReturn(List.of(yaGuardado));
        when(ingresoEntradasRepository.findAllByCajaIdOrderByFechaAsc(7L)).thenReturn(List.of());

        CajaResponseDTO dto = service.registrarRetiro(USUARIO_ID, new BigDecimal("2000"), "resguardo",
                TipoMovimientoCaja.RETIRO, "clave-1", null);

        assertThat(dto.getId()).isEqualTo(7L);
        verify(retiroCajaRepository, never()).save(any());
    }

    @Test
    void registrarRetiro_conFechaOriginal_guardaEsaFechaYNoLaDeSincronizacion() {
        // Un retiro hecho a las 14:00 sin señal y sincronizado a las 18:00 tiene que quedar
        // registrado a las 14:00, o el detalle de caja y los reportes por hora quedan mal.
        LocalDateTime cuandoPasoDeVerdad = LocalDateTime.of(2026, 8, 10, 14, 0);
        Caja cajaAbierta = cajaAbierta(7L, "5000", 50);
        when(retiroCajaRepository.findByIdempotencyKey("clave-2")).thenReturn(Optional.empty());
        when(cajaRepository.findByUsuarioIdAndFechaCierreIsNull(USUARIO_ID)).thenReturn(Optional.of(cajaAbierta));
        when(compraRepository.findAllByCajaId(7L)).thenReturn(List.of());
        when(retiroCajaRepository.findAllByCajaIdOrderByFechaAsc(7L)).thenReturn(List.of());
        when(ingresoEntradasRepository.findAllByCajaIdOrderByFechaAsc(7L)).thenReturn(List.of());

        service.registrarRetiro(USUARIO_ID, new BigDecimal("2000"), "resguardo", TipoMovimientoCaja.RETIRO,
                "clave-2", cuandoPasoDeVerdad);

        ArgumentCaptor<RetiroCaja> captor = ArgumentCaptor.forClass(RetiroCaja.class);
        verify(retiroCajaRepository).save(captor.capture());
        assertThat(captor.getValue().getFecha()).isEqualTo(cuandoPasoDeVerdad);
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo("clave-2");
    }

    @Test
    void registrarIngresoEntradas_conClaveYaProcesada_noDuplica() {
        Caja cajaAbierta = cajaAbierta(7L, "5000", 50);
        IngresoEntradas yaGuardado = IngresoEntradas.builder()
                .caja(cajaAbierta)
                .cantidad(20)
                .fecha(LocalDateTime.now())
                .idempotencyKey("clave-3")
                .build();
        when(ingresoEntradasRepository.findByIdempotencyKey("clave-3")).thenReturn(Optional.of(yaGuardado));
        when(compraRepository.findAllByCajaId(7L)).thenReturn(List.of());
        when(retiroCajaRepository.findAllByCajaIdOrderByFechaAsc(7L)).thenReturn(List.of());
        when(ingresoEntradasRepository.findAllByCajaIdOrderByFechaAsc(7L)).thenReturn(List.of(yaGuardado));

        CajaResponseDTO dto = service.registrarIngresoEntradas(USUARIO_ID, 20, null, null, "clave-3", null);

        assertThat(dto.getId()).isEqualTo(7L);
        verify(ingresoEntradasRepository, never()).save(any());
    }

    @Test
    void cerrar_sumaVariosCierresDePosnetDelMismoTipo_yCalculaLaDiferenciaContraLoVendido() {
        // Simula el posnet reiniciado a mitad de turno: dos cierres de TARJETA que se suman.
        Caja cajaAbierta = cajaAbierta(7L, "5000", 50);
        when(cajaRepository.findByUsuarioIdAndFechaCierreIsNull(USUARIO_ID)).thenReturn(Optional.of(cajaAbierta));
        when(compraRepository.findAllByCajaId(7L)).thenReturn(List.of(
                compraConMontoFormaEstado("40000", FormaPago.TARJETA, EstadoCompra.VENDIDO_EN_PUERTA),
                compraConMontoFormaEstado("15000", FormaPago.MERCADO_PAGO_QR, EstadoCompra.VENDIDO_EN_PUERTA)));
        when(retiroCajaRepository.findAllByCajaIdOrderByFechaAsc(7L)).thenReturn(List.of());
        when(cajaRepository.save(any(Caja.class))).thenAnswer(inv -> inv.getArgument(0));

        CierrePosnet mitad1 = cierrePosnet(FormaPago.TARJETA, "25000");
        CierrePosnet mitad2 = cierrePosnet(FormaPago.TARJETA, "16000");
        CierrePosnet qr = cierrePosnet(FormaPago.MERCADO_PAGO_QR, "15000");
        when(cierrePosnetRepository.findAllByCajaIdOrderByIdAsc(7L)).thenReturn(List.of(mitad1, mitad2, qr));

        CierrePosnetRequestDTO reqMitad1 = cierreRequest(FormaPago.TARJETA, "25000");
        CierrePosnetRequestDTO reqMitad2 = cierreRequest(FormaPago.TARJETA, "16000");
        CierrePosnetRequestDTO reqQr = cierreRequest(FormaPago.MERCADO_PAGO_QR, "15000");

        var respuesta = service.cerrar(USUARIO_ID, List.of(), List.of(reqMitad1, reqMitad2, reqQr), 50, null, null);

        assertThat(respuesta.getTotalVentasTarjeta()).isEqualByComparingTo("40000");
        assertThat(respuesta.getTotalCerradoTarjeta()).isEqualByComparingTo("41000");
        assertThat(respuesta.getDiferenciaTarjeta()).isEqualByComparingTo("1000");
        assertThat(respuesta.getTotalVentasQr()).isEqualByComparingTo("15000");
        assertThat(respuesta.getTotalCerradoQr()).isEqualByComparingTo("15000");
        assertThat(respuesta.getDiferenciaQr()).isEqualByComparingTo("0");
    }

    @Test
    void cerrar_conCierresPosnetCombinadosYSeparados_rechaza() {
        Caja cajaAbierta = cajaAbierta(7L, "5000", 50);
        when(cajaRepository.findByUsuarioIdAndFechaCierreIsNull(USUARIO_ID)).thenReturn(Optional.of(cajaAbierta));

        CierrePosnetRequestDTO separado = cierreRequest(FormaPago.TARJETA, "1000");
        CierrePosnetRequestDTO combinado = new CierrePosnetRequestDTO();
        combinado.setMonto(new BigDecimal("2000"));

        assertThatThrownBy(() -> service.cerrar(USUARIO_ID, List.of(), List.of(separado, combinado), 50, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cerrar_conCierrePosnetCombinado_sumaTarjetaYQrJuntosYDejaLosSeparadosEnNull() {
        Caja cajaAbierta = cajaAbierta(7L, "5000", 50);
        when(cajaRepository.findByUsuarioIdAndFechaCierreIsNull(USUARIO_ID)).thenReturn(Optional.of(cajaAbierta));
        when(compraRepository.findAllByCajaId(7L)).thenReturn(List.of(
                compraConMontoFormaEstado("40000", FormaPago.TARJETA, EstadoCompra.VENDIDO_EN_PUERTA),
                compraConMontoFormaEstado("15000", FormaPago.MERCADO_PAGO_QR, EstadoCompra.VENDIDO_EN_PUERTA)));
        when(retiroCajaRepository.findAllByCajaIdOrderByFechaAsc(7L)).thenReturn(List.of());
        when(cajaRepository.save(any(Caja.class))).thenAnswer(inv -> inv.getArgument(0));

        CierrePosnet combinado = CierrePosnet.builder().formaPago(null).monto(new BigDecimal("54000")).build();
        when(cierrePosnetRepository.findAllByCajaIdOrderByIdAsc(7L)).thenReturn(List.of(combinado));

        CierrePosnetRequestDTO reqCombinado = new CierrePosnetRequestDTO();
        reqCombinado.setMonto(new BigDecimal("54000"));

        var respuesta = service.cerrar(USUARIO_ID, List.of(), List.of(reqCombinado), 50, null, null);

        assertThat(respuesta.getTotalVentasPosnet()).isEqualByComparingTo("55000");
        assertThat(respuesta.getTotalCerradoPosnet()).isEqualByComparingTo("54000");
        assertThat(respuesta.getDiferenciaPosnet()).isEqualByComparingTo("-1000");
        assertThat(respuesta.getTotalVentasTarjeta()).isNull();
        assertThat(respuesta.getTotalVentasQr()).isNull();
        assertThat(respuesta.getTotalCerradoTarjeta()).isNull();
        assertThat(respuesta.getTotalCerradoQr()).isNull();
    }

    @Test
    void cerrar_calculaLaDiferenciaDeEntradasFisicas_soloContandoLosTiposQueEntreganEntrada() {
        Caja cajaAbierta = cajaAbierta(7L, "5000", 100);
        when(cajaRepository.findByUsuarioIdAndFechaCierreIsNull(USUARIO_ID)).thenReturn(Optional.of(cajaAbierta));

        TipoEntrada general = new TipoEntrada();
        general.setEntregaEntrada(true);
        TipoEntrada menor = new TipoEntrada();
        menor.setEntregaEntrada(false);

        CompraDetalle detalleGeneral = new CompraDetalle();
        detalleGeneral.setTipoEntrada(general);
        detalleGeneral.setCantidad(25);
        CompraDetalle detalleMenor = new CompraDetalle();
        detalleMenor.setTipoEntrada(menor);
        detalleMenor.setCantidad(10);

        Compra compra = compraConMontoFormaEstado("50000", FormaPago.EFECTIVO_BOLETERIA, EstadoCompra.VENDIDO_EN_PUERTA);
        compra.setDetalles(List.of(detalleGeneral, detalleMenor));
        when(compraRepository.findAllByCajaId(7L)).thenReturn(List.of(compra));
        when(retiroCajaRepository.findAllByCajaIdOrderByFechaAsc(7L)).thenReturn(List.of());
        when(cierrePosnetRepository.findAllByCajaIdOrderByIdAsc(7L)).thenReturn(List.of());
        when(cajaRepository.save(any(Caja.class))).thenAnswer(inv -> inv.getArgument(0));

        // Cortó 20 (menos de lo que el sistema dice que se entregó).
        var respuesta = service.cerrar(USUARIO_ID, List.of(conteo(1000, 55)), List.of(), 20, null, null);

        // esperadas = 25 (sólo el tipo que entrega entrada; el menor no cuenta, el inicial ya no influye)
        assertThat(respuesta.getEntradasFisicasEsperadas()).isEqualTo(25);
        // diferencia = 25 (esperadas) - 20 (cortadas) = 5: cortó menos de lo que las ventas
        // justificaban, así que sobran 5 entradas sin usar en el talonario (mismo criterio que
        // la diferencia de efectivo: positivo es sobrante).
        assertThat(respuesta.getDiferenciaEntradas()).isEqualTo(5);
    }

    @Test
    void cerrar_noUsaEntradasFisicasInicialNiIngresosParaLaDiferenciaDeEntradas() {
        // A diferencia del régimen viejo (comparaba contra lo que "debería quedar" en el
        // talonario), ahora sólo importa cuánto entregó el sistema vs. cuánto dice haber
        // cortado el boletero — inicial e ingresos ya no entran en esta cuenta.
        Caja cajaAbierta = cajaAbierta(7L, "5000", 100);
        when(cajaRepository.findByUsuarioIdAndFechaCierreIsNull(USUARIO_ID)).thenReturn(Optional.of(cajaAbierta));

        TipoEntrada general = new TipoEntrada();
        general.setEntregaEntrada(true);
        CompraDetalle detalleGeneral = new CompraDetalle();
        detalleGeneral.setTipoEntrada(general);
        detalleGeneral.setCantidad(30);

        Compra compra = compraConMontoFormaEstado("50000", FormaPago.EFECTIVO_BOLETERIA, EstadoCompra.VENDIDO_EN_PUERTA);
        compra.setDetalles(List.of(detalleGeneral));
        when(compraRepository.findAllByCajaId(7L)).thenReturn(List.of(compra));
        when(retiroCajaRepository.findAllByCajaIdOrderByFechaAsc(7L)).thenReturn(List.of());
        when(cierrePosnetRepository.findAllByCajaIdOrderByIdAsc(7L)).thenReturn(List.of());
        when(cajaRepository.save(any(Caja.class))).thenAnswer(inv -> inv.getArgument(0));

        var respuesta = service.cerrar(USUARIO_ID, List.of(conteo(1000, 60)), List.of(), 30, null, null);

        // esperadas = 30 (lo entregado), ignora los 100 iniciales; cortó exactamente eso.
        assertThat(respuesta.getEntradasFisicasEsperadas()).isEqualTo(30);
        assertThat(respuesta.getDiferenciaEntradas()).isEqualTo(0);
    }

    @Test
    void cerrar_conCajaSinEntradasFisicasInicialCargada_igualCalculaLaDiferencia() {
        // entradasFisicasInicial ya no interviene en el cálculo de entradas: aunque esta
        // caja no lo tenga cargado (turnos previos a ese campo), la diferencia de cortadas
        // se sigue calculando igual.
        Caja cajaVieja = cajaAbierta(7L, "5000", null);
        when(cajaRepository.findByUsuarioIdAndFechaCierreIsNull(USUARIO_ID)).thenReturn(Optional.of(cajaVieja));
        when(compraRepository.findAllByCajaId(7L)).thenReturn(List.of());
        when(retiroCajaRepository.findAllByCajaIdOrderByFechaAsc(7L)).thenReturn(List.of());
        when(cierrePosnetRepository.findAllByCajaIdOrderByIdAsc(7L)).thenReturn(List.of());
        when(cajaRepository.save(any(Caja.class))).thenAnswer(inv -> inv.getArgument(0));

        var respuesta = service.cerrar(USUARIO_ID, List.of(), List.of(), 30, null, null);

        // esperadas = 0 (no hubo ventas que entregaran entrada); cortó 30 sin que ninguna venta
        // lo justifique = faltan 30 en el talonario (diferencia = 0 - 30 = -30).
        assertThat(respuesta.getEntradasFisicasEsperadas()).isEqualTo(0);
        assertThat(respuesta.getDiferenciaEntradas()).isEqualTo(-30);
    }

    @Test
    void cerrar_cuentaLasEntradasVendidasPorTipo_ignorandoExtrasYCompraCancelada() {
        Caja cajaAbierta = cajaAbierta(7L, "5000", 50);
        when(cajaRepository.findByUsuarioIdAndFechaCierreIsNull(USUARIO_ID)).thenReturn(Optional.of(cajaAbierta));

        TipoEntrada general = new TipoEntrada();
        general.setNombre("General");
        general.setTipo(org.example.laserranitaentradas.model.entity.Tipo.ENTRADA);
        TipoEntrada nino = new TipoEntrada();
        nino.setNombre("Niño");
        nino.setTipo(org.example.laserranitaentradas.model.entity.Tipo.ENTRADA);
        TipoEntrada almuerzo = new TipoEntrada();
        almuerzo.setNombre("Almuerzo");
        almuerzo.setTipo(org.example.laserranitaentradas.model.entity.Tipo.EXTRA);

        CompraDetalle detalleGeneral = new CompraDetalle();
        detalleGeneral.setTipoEntrada(general);
        detalleGeneral.setCantidad(3);
        CompraDetalle detalleNino = new CompraDetalle();
        detalleNino.setTipoEntrada(nino);
        detalleNino.setCantidad(2);
        CompraDetalle detalleAlmuerzo = new CompraDetalle();
        detalleAlmuerzo.setTipoEntrada(almuerzo);
        detalleAlmuerzo.setCantidad(5);

        Compra ventaValida = compraConMontoFormaEstado("50000", FormaPago.EFECTIVO_BOLETERIA, EstadoCompra.VENDIDO_EN_PUERTA);
        ventaValida.setDetalles(List.of(detalleGeneral, detalleNino, detalleAlmuerzo));

        CompraDetalle detalleGeneralCancelado = new CompraDetalle();
        detalleGeneralCancelado.setTipoEntrada(general);
        detalleGeneralCancelado.setCantidad(10);
        Compra ventaCancelada = compraConMontoFormaEstado("100000", FormaPago.EFECTIVO_BOLETERIA, EstadoCompra.CANCELADO);
        ventaCancelada.setDetalles(List.of(detalleGeneralCancelado));

        when(compraRepository.findAllByCajaId(7L)).thenReturn(List.of(ventaValida, ventaCancelada));
        when(retiroCajaRepository.findAllByCajaIdOrderByFechaAsc(7L)).thenReturn(List.of());
        when(cierrePosnetRepository.findAllByCajaIdOrderByIdAsc(7L)).thenReturn(List.of());
        when(cajaRepository.save(any(Caja.class))).thenAnswer(inv -> inv.getArgument(0));

        var respuesta = service.cerrar(USUARIO_ID, List.of(conteo(1000, 50)), List.of(), 50, null, null);

        assertThat(respuesta.getTotalEntradasVendidas()).isEqualTo(5);
        assertThat(respuesta.getEntradasVendidasPorTipo()).hasSize(2);
        assertThat(respuesta.getEntradasVendidasPorTipo())
                .anySatisfy(e -> {
                    assertThat(e.getNombreTipo()).isEqualTo("General");
                    assertThat(e.getCantidad()).isEqualTo(3);
                });
    }

    @Test
    void getActual_conVentaEnDolares_exponeHuboVentaDolaresAunqueLaCajaSigaAbierta() {
        // Es sólo un booleano (no un monto): a diferencia de los totales esperados, esto se
        // ve incluso con la caja ABIERTA, porque el frontend lo necesita para saber si
        // mostrar el campo de dólares contados al cerrar.
        Caja cajaAbierta = cajaAbierta(7L, "5000", 50);
        when(cajaRepository.findByUsuarioIdAndFechaCierreIsNull(2L)).thenReturn(Optional.of(cajaAbierta));
        when(compraRepository.findAllByCajaId(7L)).thenReturn(List.of(
                compraDolares("50000", "1200", "50", EstadoCompra.VENDIDO_EN_PUERTA)));
        when(retiroCajaRepository.findAllByCajaIdOrderByFechaAsc(7L)).thenReturn(List.of());
        when(ingresoEntradasRepository.findAllByCajaIdOrderByFechaAsc(7L)).thenReturn(List.of());

        var respuesta = service.getActual(2L);

        assertThat(respuesta.getHuboVentaDolares()).isTrue();
        // Sigue ABIERTA: los montos esperados siguen ocultos, dólares esperado incluido.
        assertThat(respuesta.getDolaresEsperado()).isNull();
    }

    @Test
    void getActual_sinVentaEnDolares_huboVentaDolaresEsFalse() {
        Caja cajaAbierta = cajaAbierta(7L, "5000", 50);
        when(cajaRepository.findByUsuarioIdAndFechaCierreIsNull(2L)).thenReturn(Optional.of(cajaAbierta));
        when(compraRepository.findAllByCajaId(7L)).thenReturn(List.of(
                compraConMontoFormaEstado("50000", FormaPago.EFECTIVO_BOLETERIA, EstadoCompra.VENDIDO_EN_PUERTA)));
        when(retiroCajaRepository.findAllByCajaIdOrderByFechaAsc(7L)).thenReturn(List.of());
        when(ingresoEntradasRepository.findAllByCajaIdOrderByFechaAsc(7L)).thenReturn(List.of());

        var respuesta = service.getActual(2L);

        assertThat(respuesta.getHuboVentaDolares()).isFalse();
    }

    @Test
    void cerrar_conVentaEnDolares_sinDolaresContado_rechaza() {
        Caja cajaAbierta = cajaAbierta(7L, "5000", 50);
        when(cajaRepository.findByUsuarioIdAndFechaCierreIsNull(USUARIO_ID)).thenReturn(Optional.of(cajaAbierta));
        when(compraRepository.findAllByCajaId(7L)).thenReturn(List.of(
                compraDolares("50000", "1200", "60", EstadoCompra.VENDIDO_EN_PUERTA)));

        assertThatThrownBy(() -> service.cerrar(USUARIO_ID, List.of(), List.of(), 50, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cerrar_conVentasEnDolares_sumaLosDolaresRecibidosYRestaElVueltoDelEfectivoEsperado() {
        // El pago en dólares sigue siendo EFECTIVO_BOLETERIA, pero no entra nada en pesos al
        // cajón (entran dólares, contados aparte): lo único que sale del cajón en pesos es el
        // vuelto. totalVentasEfectivo (revenue) sí suma el precio de lista igual que cualquier
        // venta; efectivoEsperado (lo que tiene que haber físicamente) no.
        Caja cajaAbierta = cajaAbierta(7L, "5000", 50);
        when(cajaRepository.findByUsuarioIdAndFechaCierreIsNull(USUARIO_ID)).thenReturn(Optional.of(cajaAbierta));

        // Venta en pesos: entran 3000 pesos limpios.
        Compra ventaPesos = compraConMontoFormaEstado("3000", FormaPago.EFECTIVO_BOLETERIA, EstadoCompra.VENDIDO_EN_PUERTA);
        // Venta en dólares: total 1000, cliente entrega 2 dólares a 1000 cada uno = 2000,
        // vuelto = 2000 - 1000 = 1000 pesos que salen del cajón.
        Compra ventaDolares = compraDolares("1000", "1000", "2", EstadoCompra.VENDIDO_EN_PUERTA);
        // Cancelada: no debe contar ni para el esperado en pesos ni en dólares.
        Compra ventaCancelada = compraDolares("5000", "1000", "10", EstadoCompra.CANCELADO);

        when(compraRepository.findAllByCajaId(7L)).thenReturn(List.of(ventaPesos, ventaDolares, ventaCancelada));
        when(retiroCajaRepository.findAllByCajaIdOrderByFechaAsc(7L)).thenReturn(List.of());
        when(cierrePosnetRepository.findAllByCajaIdOrderByIdAsc(7L)).thenReturn(List.of());
        when(cajaRepository.save(any(Caja.class))).thenAnswer(inv -> inv.getArgument(0));

        // El boletero contó 3 dólares físicos (1 de más).
        var respuesta = service.cerrar(USUARIO_ID, List.of(), List.of(), 50, null, new BigDecimal("3"));

        assertThat(respuesta.getTotalVentasEfectivo()).isEqualByComparingTo("4000"); // 3000 + 1000, revenue de lista
        assertThat(respuesta.getEfectivoEsperado()).isEqualByComparingTo("7000"); // 5000 + 3000 (pesos) - 1000 (vuelto)
        assertThat(respuesta.getDolaresEsperado()).isEqualByComparingTo("2");
        assertThat(respuesta.getDolaresContado()).isEqualByComparingTo("3");
        assertThat(respuesta.getDiferenciaDolares()).isEqualByComparingTo("1");
    }

    @Test
    void cerrar_cajaSinVentasEnDolares_noExigeNiExponeDolaresContado() {
        Caja cajaAbierta = cajaAbierta(7L, "5000", 50);
        when(cajaRepository.findByUsuarioIdAndFechaCierreIsNull(USUARIO_ID)).thenReturn(Optional.of(cajaAbierta));
        when(compraRepository.findAllByCajaId(7L)).thenReturn(List.of());
        when(retiroCajaRepository.findAllByCajaIdOrderByFechaAsc(7L)).thenReturn(List.of());
        when(cierrePosnetRepository.findAllByCajaIdOrderByIdAsc(7L)).thenReturn(List.of());
        when(cajaRepository.save(any(Caja.class))).thenAnswer(inv -> inv.getArgument(0));

        var respuesta = service.cerrar(USUARIO_ID, List.of(), List.of(), 50, null, null);

        assertThat(respuesta.getHuboVentaDolares()).isFalse();
        assertThat(respuesta.getDolaresContado()).isNull();
        assertThat(respuesta.getDolaresEsperado()).isNull();
        assertThat(respuesta.getDiferenciaDolares()).isNull();
    }

    @Test
    void cerrarComoAdmin_enCajaAjena_laCierraSinChequearDueno() {
        Caja cajaDeOtro = cajaAbierta(7L, "5000", 50);
        cajaDeOtro.setUsuario(usuarioConId(99L));
        when(cajaRepository.findById(7L)).thenReturn(Optional.of(cajaDeOtro));
        when(compraRepository.findAllByCajaId(7L)).thenReturn(List.of());
        when(retiroCajaRepository.findAllByCajaIdOrderByFechaAsc(7L)).thenReturn(List.of());
        when(cajaRepository.save(any(Caja.class))).thenAnswer(inv -> inv.getArgument(0));

        var respuesta = service.cerrarComoAdmin(7L, List.of(), List.of(), 30, null, null);

        assertThat(respuesta.getEstado()).isEqualTo("CERRADA");
        assertThat(cajaDeOtro.getUsuario().getId()).isEqualTo(99L);
    }

    @Test
    void cerrarComoAdmin_deCajaYaCerrada_rechaza() {
        Caja cajaCerrada = cajaCerradaDeUsuario(7L, 99L);
        when(cajaRepository.findById(7L)).thenReturn(Optional.of(cajaCerrada));

        assertThatThrownBy(() -> service.cerrarComoAdmin(7L, List.of(), List.of(), 30, null, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cerrarComoAdmin_deCajaInexistente_rechaza() {
        when(cajaRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cerrarComoAdmin(7L, List.of(), List.of(), 30, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void corregirCierre_deOtroUsuario_permite() {
        // corregirCierre ya no valida dueño en el service: queda gateado ADMIN-only en
        // SecurityConfig, así que cualquier caja se puede corregir sin importar quién la abrió
        // (mismo criterio que getDetalle).
        Caja cajaCerrada = cajaCerradaDeUsuario(7L, 99L);
        when(cajaRepository.findById(7L)).thenReturn(Optional.of(cajaCerrada));
        when(compraRepository.findAllByCajaId(7L)).thenReturn(List.of());
        when(retiroCajaRepository.findAllByCajaIdOrderByFechaAsc(7L)).thenReturn(List.of());
        when(cierrePosnetRepository.findAllByCajaIdOrderByIdAsc(7L)).thenReturn(List.of());
        when(cajaRepository.save(any(Caja.class))).thenAnswer(inv -> inv.getArgument(0));

        var respuesta = service.corregirCierre(7L, List.of(conteo(100, 1)), List.of(), 50, null, null);

        assertThat(respuesta.getId()).isEqualTo(7L);
    }

    @Test
    void corregirCierre_deCajaTodaviaAbierta_rechaza() {
        Caja cajaAbierta = cajaAbierta(7L, "5000", 50); // sin fechaCierre
        cajaAbierta.setUsuario(usuarioConId(USUARIO_ID));
        when(cajaRepository.findById(7L)).thenReturn(Optional.of(cajaAbierta));

        assertThatThrownBy(() -> service.corregirCierre(7L, List.of(conteo(100, 1)), List.of(), 50, null, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void corregirCierre_deCajaInexistente_rechaza() {
        when(cajaRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.corregirCierre(7L, List.of(), List.of(), 50, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void corregirCierre_recalculaMontoContadoYReemplazaLosCierresDePosnet() {
        Caja cajaCerrada = cajaCerradaDeUsuario(7L, USUARIO_ID);
        cajaCerrada.setMontoContado(new BigDecimal("999")); // valor viejo, mal cargado
        when(cajaRepository.findById(7L)).thenReturn(Optional.of(cajaCerrada));
        when(compraRepository.findAllByCajaId(7L)).thenReturn(List.of());
        when(retiroCajaRepository.findAllByCajaIdOrderByFechaAsc(7L)).thenReturn(List.of());
        when(cierrePosnetRepository.findAllByCajaIdOrderByIdAsc(7L)).thenReturn(List.of());
        when(cajaRepository.save(any(Caja.class))).thenAnswer(inv -> inv.getArgument(0));

        // Corrección: eran 500 billetes de 100 (no 999 como había quedado mal cargado antes).
        var respuesta = service.corregirCierre(7L, List.of(conteo(100, 500)), List.of(), 40, null, null);

        assertThat(respuesta.getMontoContado()).isEqualByComparingTo("50000");
        verify(cierrePosnetRepository).deleteAllByCajaId(7L);
    }

    @Test
    void reabrir_conCajaCerrada_leSacaLaFechaDeCierreYLaGuarda() {
        Caja cajaCerrada = cajaCerradaDeUsuario(7L, USUARIO_ID);
        when(cajaRepository.findById(7L)).thenReturn(Optional.of(cajaCerrada));
        when(cajaRepository.save(any(Caja.class))).thenAnswer(inv -> inv.getArgument(0));

        Caja resultado = service.reabrir(7L);

        assertThat(resultado.getFechaCierre()).isNull();
        verify(cajaRepository).save(cajaCerrada);
    }

    @Test
    void reabrir_deCajaInexistente_rechaza() {
        when(cajaRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reabrir(7L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reabrir_deCajaTodaviaAbierta_rechaza() {
        Caja cajaAbierta = cajaAbierta(7L, "5000", 50); // sin fechaCierre
        when(cajaRepository.findById(7L)).thenReturn(Optional.of(cajaAbierta));

        assertThatThrownBy(() -> service.reabrir(7L)).isInstanceOf(IllegalStateException.class);
        verify(cajaRepository, never()).save(any());
    }

    @Test
    void recerrarConElUltimoConteo_reutilizaElConteoYaGuardadoEnLaCaja() {
        // Simula una caja recién reabierta con reabrir(): fechaCierre null, pero el conteo de
        // cuando se cerró la primera vez todavía está ahí (reabrir no lo toca).
        Caja cajaReabierta = cajaAbierta(7L, "5000", 50);
        cajaReabierta.setUsuario(usuarioConId(USUARIO_ID));
        cajaReabierta.setEntradasFisicasCortadas(40);
        cajaReabierta.getConteoEfectivo().add(new org.example.laserranitaentradas.model.entity.ConteoDenominacion(100, 500));
        when(cajaRepository.findById(7L)).thenReturn(Optional.of(cajaReabierta));
        when(cajaRepository.save(any(Caja.class))).thenAnswer(inv -> inv.getArgument(0));
        when(compraRepository.findAllByCajaId(7L)).thenReturn(List.of());
        when(retiroCajaRepository.findAllByCajaIdOrderByFechaAsc(7L)).thenReturn(List.of());
        when(cierrePosnetRepository.findAllByCajaIdOrderByIdAsc(7L)).thenReturn(List.of());

        var respuesta = service.recerrarConElUltimoConteo(7L);

        verify(cierrePosnetRepository).deleteAllByCajaId(7L);
        assertThat(respuesta.getEstado()).isEqualTo("CERRADA");
        assertThat(respuesta.getMontoContado()).isEqualByComparingTo("50000");
    }

    @Test
    void recerrarConElUltimoConteo_deCajaInexistente_rechaza() {
        when(cajaRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recerrarConElUltimoConteo(7L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recerrarConElUltimoConteo_deCajaYaCerrada_rechaza() {
        Caja cajaCerrada = cajaCerradaDeUsuario(7L, USUARIO_ID);
        when(cajaRepository.findById(7L)).thenReturn(Optional.of(cajaCerrada));

        assertThatThrownBy(() -> service.recerrarConElUltimoConteo(7L)).isInstanceOf(IllegalStateException.class);
        verify(cierrePosnetRepository, never()).deleteAllByCajaId(any());
    }

    @Test
    void getDetalle_deCajaInexistente_rechaza() {
        when(cajaRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetalle(7L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getDetalle_devuelveLaCajaSinImportarQuienLaAbrio() {
        // A diferencia de corregirCierre, getDetalle no valida dueño: lo usa el admin para
        // revisar cualquier caja, no sólo las propias.
        Caja cajaCerrada = cajaCerradaDeUsuario(7L, 99L);
        when(cajaRepository.findById(7L)).thenReturn(Optional.of(cajaCerrada));
        when(compraRepository.findAllByCajaId(7L)).thenReturn(List.of());
        when(retiroCajaRepository.findAllByCajaIdOrderByFechaAsc(7L)).thenReturn(List.of());
        when(cierrePosnetRepository.findAllByCajaIdOrderByIdAsc(7L)).thenReturn(List.of());

        var respuesta = service.getDetalle(7L);

        assertThat(respuesta.getId()).isEqualTo(7L);
        assertThat(respuesta.getEstado()).isEqualTo("CERRADA");
    }

    @Test
    void getCajasAbiertas_devuelveSoloLasQueSiguenAbiertas_deCualquierUsuario() {
        Usuario usuario1 = new Usuario();
        usuario1.setNombre("Marta");
        usuario1.setApellido("Gómez");
        Caja abierta1 = cajaAbierta(7L, "5000", 10);
        abierta1.setUsuario(usuario1);

        Usuario usuario2 = new Usuario();
        usuario2.setNombre("Juan");
        usuario2.setApellido("Pérez");
        Caja abierta2 = cajaAbierta(8L, "3000", 20);
        abierta2.setUsuario(usuario2);

        when(cajaRepository.findAllByFechaCierreIsNullOrderByFechaAperturaAsc()).thenReturn(List.of(abierta1, abierta2));

        TipoEntrada general = new TipoEntrada();
        general.setNombre("General");
        general.setTipo(org.example.laserranitaentradas.model.entity.Tipo.ENTRADA);
        CompraDetalle detalle = new CompraDetalle();
        detalle.setTipoEntrada(general);
        detalle.setCantidad(4);

        Compra ventaEfectivo = compraConMontoFormaEstado("40000", FormaPago.EFECTIVO_BOLETERIA, EstadoCompra.VENDIDO_EN_PUERTA);
        ventaEfectivo.setDetalles(List.of(detalle));
        Compra ventaTarjeta = compraConMontoFormaEstado("15000", FormaPago.TARJETA, EstadoCompra.VENDIDO_EN_PUERTA);
        ventaTarjeta.setDetalles(List.of());
        when(compraRepository.findAllByCajaId(7L)).thenReturn(List.of(ventaEfectivo, ventaTarjeta));
        when(compraRepository.findAllByCajaId(8L)).thenReturn(List.of());

        var respuesta = service.getCajasAbiertas();

        assertThat(respuesta).hasSize(2);
        assertThat(respuesta).anySatisfy(c -> {
            assertThat(c.getUsuarioNombre()).isEqualTo("Marta Gómez");
            assertThat(c.getMontoInicial()).isEqualByComparingTo("5000");
            assertThat(c.getTotalVendido()).isEqualByComparingTo("55000");
            assertThat(c.getTotalEntradasVendidas()).isEqualTo(4);
        });
        assertThat(respuesta).anySatisfy(c -> {
            assertThat(c.getUsuarioNombre()).isEqualTo("Juan Pérez");
            assertThat(c.getTotalVendido()).isEqualByComparingTo("0");
            assertThat(c.getTotalEntradasVendidas()).isEqualTo(0);
        });
    }

    private Usuario usuarioConId(Long id) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        return usuario;
    }

    private Caja cajaCerradaDeUsuario(Long id, Long usuarioId) {
        Caja caja = cajaAbierta(id, "5000", 50);
        caja.setUsuario(usuarioConId(usuarioId));
        caja.setFechaCierre(java.time.LocalDateTime.now());
        return caja;
    }

    private Caja cajaAbierta(Long id, String montoInicial, Integer entradasFisicasInicial) {
        Caja caja = new Caja();
        caja.setId(id);
        caja.setMontoInicial(new BigDecimal(montoInicial));
        caja.setEntradasFisicasInicial(entradasFisicasInicial);
        return caja;
    }

    private ConteoDenominacionDTO conteo(int denominacion, int cantidad) {
        ConteoDenominacionDTO dto = new ConteoDenominacionDTO();
        dto.setDenominacion(denominacion);
        dto.setCantidad(cantidad);
        return dto;
    }

    private CierrePosnetRequestDTO cierreRequest(FormaPago formaPago, String monto) {
        CierrePosnetRequestDTO dto = new CierrePosnetRequestDTO();
        dto.setFormaPago(formaPago);
        dto.setMonto(new BigDecimal(monto));
        return dto;
    }

    private CierrePosnet cierrePosnet(FormaPago formaPago, String monto) {
        return CierrePosnet.builder().formaPago(formaPago).monto(new BigDecimal(monto)).build();
    }

    private Compra compraConMontoFormaEstado(String monto, FormaPago formaPago, EstadoCompra estado) {
        Compra compra = new Compra();
        compra.setMontoTotal(new BigDecimal(monto));
        compra.setFormaPago(formaPago);
        compra.setEstado(estado);
        compra.setDetalles(List.of());
        compra.setFechaCreacion(java.time.LocalDateTime.now());
        return compra;
    }

    /** Venta en efectivo pagada en dólares: sigue siendo EFECTIVO_BOLETERIA, sólo cambia la moneda física. */
    private Compra compraDolares(String monto, String cotizacion, String dolaresRecibidos, EstadoCompra estado) {
        Compra compra = compraConMontoFormaEstado(monto, FormaPago.EFECTIVO_BOLETERIA, estado);
        compra.setCotizacionDolar(new BigDecimal(cotizacion));
        compra.setDolaresRecibidos(new BigDecimal(dolaresRecibidos));
        return compra;
    }
}
