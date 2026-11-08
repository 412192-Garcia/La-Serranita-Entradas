package org.example.laserranitaentradas.service.impresion;

import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.CompraDetalle;
import org.example.laserranitaentradas.model.entity.Tipo;
import org.example.laserranitaentradas.model.entity.TipoEntrada;
import org.example.laserranitaentradas.model.entity.TipoVoucher;
import org.example.laserranitaentradas.model.entity.Voucher;
import org.example.laserranitaentradas.repository.VoucherRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class VoucherServiceTest {

    private static final TipoEntrada GENERAL = TipoEntrada.builder().id(1L).nombre("General").tipo(Tipo.ENTRADA)
            .precio(new BigDecimal("10000")).build();
    private static final TipoEntrada ALMUERZO = TipoEntrada.builder().id(2L).nombre("Almuerzo").tipo(Tipo.EXTRA)
            .precio(new BigDecimal("5000")).build();

    private static Compra compra(BigDecimal montoTotal, int generales, Integer ausentes, int almuerzos) {
        List<CompraDetalle> detalles = new ArrayList<>();
        detalles.add(CompraDetalle.builder().id(10L).tipoEntrada(GENERAL).cantidad(generales).cantidadAusente(ausentes).build());
        if (almuerzos > 0) detalles.add(CompraDetalle.builder().id(11L).tipoEntrada(ALMUERZO).cantidad(almuerzos).build());
        return Compra.builder().id(5L).codigoReserva("261108-3").montoTotal(montoTotal).detalles(detalles).build();
    }

    @Test
    void montoAusentes_esLoQueSePagoPorEsasEntradas() {
        assertThat(VoucherService.montoAusentes(compra(new BigDecimal("100000"), 10, 1, 0))).isEqualByComparingTo("10000");
    }

    @Test
    void montoAusentes_aplicaElDescuentoDeLaCompra() {
        // Lista 100.000, pagó 90.000: cada entrada valió 9.000.
        assertThat(VoucherService.montoAusentes(compra(new BigDecimal("90000"), 10, 2, 0))).isEqualByComparingTo("18000");
    }

    @Test
    void montoAusentes_sinAusentesEsCero() {
        assertThat(VoucherService.montoAusentes(compra(new BigDecimal("100000"), 10, null, 0))).isZero();
    }

    @Test
    void emitir_unoPorExtraYUnoDeCantina() {
        VoucherRepository repo = mock(VoucherRepository.class);
        when(repo.findByCompraIdOrderByIdAsc(5L)).thenReturn(List.of());
        when(repo.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        VoucherService service = new VoucherService(repo, mock(ImpresionService.class));

        List<Voucher> emitidos = service.emitir(compra(new BigDecimal("105000"), 10, 1, 1), null, null);

        assertThat(emitidos).extracting(Voucher::getTipo).containsExactly(TipoVoucher.EXTRA, TipoVoucher.CANTINA);
        assertThat(emitidos.get(0).getCantidad()).isEqualTo(1);
        assertThat(emitidos.get(0).getCodigo()).isEqualTo("261108-3-1");
        assertThat(emitidos.get(1).getMonto()).isEqualByComparingTo("10000");
    }

    @Test
    void emitir_noRepiteSiYaHayVigentes() {
        VoucherRepository repo = mock(VoucherRepository.class);
        when(repo.findByCompraIdOrderByIdAsc(5L)).thenReturn(List.of(Voucher.builder().id(1L).build()));
        VoucherService service = new VoucherService(repo, mock(ImpresionService.class));

        assertThat(service.emitir(compra(new BigDecimal("105000"), 10, 1, 1), null, null)).isEmpty();
        verify(repo, never()).saveAll(anyList());
    }

    @Test
    void ticketDelVoucher_muestraElValorYElCodigo() {
        byte[] bytes = new TicketEscPosGenerator().generarVoucher(new TicketEscPosGenerator.DatosVoucher(
                "VOUCHER CANTINA", "$ 10.000", "Para consumir en la cantina", "261108-3-2", "261108-3",
                LocalDateTime.of(2026, 11, 8, 11, 30), "Ana"));
        String texto = new String(bytes, Charset.forName("CP850"));

        assertThat(texto).contains("VOUCHER CANTINA", "$ 10.000", "Voucher: 261108-3-2", "Válido sólo el 08/11/2026");
    }
}
