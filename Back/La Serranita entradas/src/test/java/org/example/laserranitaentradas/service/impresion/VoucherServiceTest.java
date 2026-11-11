package org.example.laserranitaentradas.service.impresion;

import org.example.laserranitaentradas.model.dto.VoucherManualRequestDTO;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
                "VOUCHER CANTINA", "$ 10.000", "Para consumir en la cantina", "Juan Pérez", "261108-3-2", "261108-3",
                LocalDateTime.of(2026, 11, 8, 11, 30), "Ana"));
        String texto = new String(bytes, Charset.forName("CP850"));

        assertThat(texto).contains("VOUCHER CANTINA", "$ 10.000", "Voucher  261108-3-2", "Juan Pérez", "Válido sólo el 08/11/2026");
    }

    @Test
    void ticketDelVoucher_parteElDetalleEnPalabrasEnteras() {
        byte[] bytes = new TicketEscPosGenerator().generarVoucher(new TicketEscPosGenerator.DatosVoucher(
                "VOUCHER CANTINA", "$ 10.000",
                "Para consumir en la cantina\n3 Pase General sin usar y una descripción bastante más larga",
                null, "261108-3-2", "261108-3", LocalDateTime.of(2026, 11, 8, 11, 30), null));
        String texto = new String(bytes, Charset.forName("CP850"));

        assertThat(texto).contains("Para consumir en la cantina\n"
                + "3 Pase General sin usar y una descripción\nbastante más larga\n");
    }

    private static VoucherManualRequestDTO manual(TipoVoucher tipo, String descripcion, Integer cantidad, String monto) {
        VoucherManualRequestDTO r = new VoucherManualRequestDTO();
        r.setTipo(tipo);
        r.setDescripcion(descripcion);
        r.setCantidad(cantidad);
        r.setMonto(monto == null ? null : new BigDecimal(monto));
        r.setTitular("  Juan   Pérez ");
        return r;
    }

    private static VoucherRepository repoQueGuarda() {
        VoucherRepository repo = mock(VoucherRepository.class);
        when(repo.save(any(Voucher.class))).thenAnswer(inv -> {
            Voucher v = inv.getArgument(0);
            assertThat(v.getCodigo()).hasSizeLessThanOrEqualTo(30);
            v.setId(7L);
            return v;
        });
        return repo;
    }

    @Test
    void emitirManual_extra_quedaSinCompraYSeImprime() {
        ImpresionService impresion = mock(ImpresionService.class);
        VoucherService service = new VoucherService(repoQueGuarda(), impresion);

        Voucher v = service.emitirManual(manual(TipoVoucher.EXTRA, "Menú Almuerzo Parque", 2, null), null);

        assertThat(v.getCompra()).isNull();
        assertThat(v.getCodigo()).isEqualTo("M-7");
        assertThat(v.getTitular()).isEqualTo("Juan Pérez");
        assertThat(v.getCantidad()).isEqualTo(2);
        verify(impresion).imprimirVoucher(7L, null);
    }

    @Test
    void emitirManual_cantina_redondeaAPesosYElMotivoEsOpcional() {
        VoucherService service = new VoucherService(repoQueGuarda(), mock(ImpresionService.class));

        Voucher v = service.emitirManual(manual(TipoVoucher.CANTINA, "  ", null, "15000.6"), null);

        assertThat(v.getMonto()).isEqualByComparingTo("15001");
        assertThat(v.getDescripcion()).isEmpty();
    }

    @Test
    void emitirManual_rechazaDatosIncompletos() {
        VoucherService service = new VoucherService(repoQueGuarda(), mock(ImpresionService.class));

        assertThatThrownBy(() -> service.emitirManual(manual(null, "x", 1, null), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.emitirManual(manual(TipoVoucher.EXTRA, "", 1, null), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.emitirManual(manual(TipoVoucher.EXTRA, "Tobogán", 0, null), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.emitirManual(manual(TipoVoucher.CANTINA, null, null, "0"), null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ticketDelVoucherManual_sinReserva() {
        byte[] bytes = new TicketEscPosGenerator().generarVoucher(new TicketEscPosGenerator.DatosVoucher(
                "VOUCHER", "1 x Tobogán", null, null, "M-7", null, LocalDateTime.of(2026, 11, 8, 11, 30), "Ana"));
        String texto = new String(bytes, Charset.forName("CP850"));

        assertThat(texto).contains("Voucher  M-7\n").doesNotContain("Reserva");
    }
}
