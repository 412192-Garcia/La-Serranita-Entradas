package org.example.laserranitaentradas.service.impresion;

import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.CompraDetalle;
import org.example.laserranitaentradas.model.entity.Tipo;
import org.example.laserranitaentradas.model.entity.TipoVoucher;
import org.example.laserranitaentradas.model.entity.Usuario;
import org.example.laserranitaentradas.model.entity.Voucher;
import org.example.laserranitaentradas.repository.VoucherRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Vouchers que se entregan al validar una anticipada: los extras y la plata de los que no vinieron. */
@Service
public class VoucherService {

    private static final Logger log = LoggerFactory.getLogger(VoucherService.class);

    private final VoucherRepository voucherRepository;
    private final ImpresionService impresionService;

    public VoucherService(VoucherRepository voucherRepository, ImpresionService impresionService) {
        this.voucherRepository = voucherRepository;
        this.impresionService = impresionService;
    }

    /**
     * Un voucher por cada extra de la compra y, si hubo ausentes, uno de cantina por lo que valían sus
     * entradas. Se imprimen después del commit. No hace nada si la compra ya tiene vouchers vigentes.
     */
    @Transactional
    public List<Voucher> emitir(Compra compra, Usuario emisor, String impresora) {
        List<Voucher> previos = voucherRepository.findByCompraIdOrderByIdAsc(compra.getId());
        if (previos.stream().anyMatch(v -> !v.isAnulado())) return List.of();

        LocalDateTime ahora = LocalDateTime.now();
        long numero = previos.size();
        List<Voucher> nuevos = new ArrayList<>();
        for (CompraDetalle d : compra.getDetalles()) {
            if (d.getTipoEntrada() == null || d.getTipoEntrada().getTipo() != Tipo.EXTRA || d.getCantidad() <= 0) continue;
            nuevos.add(Voucher.builder()
                    .codigo(compra.getCodigoReserva() + "-" + (++numero))
                    .compra(compra)
                    .tipo(TipoVoucher.EXTRA)
                    .descripcion(d.getTipoEntrada().getNombre())
                    .cantidad(d.getCantidad())
                    .emitidoPor(emisor)
                    .fechaEmision(ahora)
                    .build());
        }
        BigDecimal montoCantina = montoAusentes(compra);
        if (montoCantina.signum() > 0) {
            nuevos.add(Voucher.builder()
                    .codigo(compra.getCodigoReserva() + "-" + (++numero))
                    .compra(compra)
                    .tipo(TipoVoucher.CANTINA)
                    .descripcion(descripcionAusentes(compra))
                    .monto(montoCantina)
                    .emitidoPor(emisor)
                    .fechaEmision(ahora)
                    .build());
        }
        if (nuevos.isEmpty()) return List.of();
        List<Voucher> guardados = voucherRepository.saveAll(nuevos);
        imprimirAlConfirmar(guardados.stream().map(Voucher::getId).toList(), impresora);
        return guardados;
    }

    /** Se deshizo la validación: los papeles dejan de valer y no se imprimen. */
    @Transactional
    public void anularDeCompra(Long compraId) {
        for (Voucher v : voucherRepository.findByCompraIdOrderByIdAsc(compraId)) {
            v.setAnulado(true);
        }
    }

    /** Vuelve a mandar a la ticketera los vouchers vigentes de la compra. */
    @Transactional(readOnly = true)
    public int reimprimir(Long compraId, String impresora) {
        List<Long> ids = voucherRepository.findByCompraIdOrderByIdAsc(compraId).stream()
                .filter(v -> !v.isAnulado()).map(Voucher::getId).toList();
        if (ids.isEmpty()) throw new IllegalStateException("Esta reserva no tiene vouchers para imprimir");
        imprimirAlConfirmar(ids, impresora);
        return ids.size();
    }

    @Transactional(readOnly = true)
    public List<Voucher> deCompra(Long compraId) {
        return voucherRepository.findByCompraIdOrderByIdAsc(compraId);
    }

    /**
     * Lo que se pagó por las entradas de los ausentes: el precio de cada una con el mismo descuento que tuvo
     * la compra entera, redondeado a pesos.
     */
    public static BigDecimal montoAusentes(Compra compra) {
        BigDecimal lista = BigDecimal.ZERO;
        BigDecimal listaAusentes = BigDecimal.ZERO;
        for (CompraDetalle d : compra.getDetalles()) {
            BigDecimal precio = precioDe(d);
            lista = lista.add(precio.multiply(BigDecimal.valueOf(d.getCantidad())));
            int ausentes = d.getCantidadAusente() == null ? 0 : d.getCantidadAusente();
            listaAusentes = listaAusentes.add(precio.multiply(BigDecimal.valueOf(ausentes)));
        }
        if (listaAusentes.signum() == 0 || lista.signum() == 0) return BigDecimal.ZERO;
        BigDecimal pagado = compra.getMontoTotal().min(lista);
        return listaAusentes.multiply(pagado).divide(lista, 0, RoundingMode.HALF_UP);
    }

    private static BigDecimal precioDe(CompraDetalle d) {
        if (d.getPrecioUnitario() != null) return d.getPrecioUnitario();
        if (d.getTipoEntrada() != null && d.getTipoEntrada().getPrecio() != null) return d.getTipoEntrada().getPrecio();
        return BigDecimal.ZERO;
    }

    private static String descripcionAusentes(Compra compra) {
        List<String> partes = new ArrayList<>();
        for (CompraDetalle d : compra.getDetalles()) {
            if (d.getCantidadAusente() != null && d.getCantidadAusente() > 0 && d.getTipoEntrada() != null) {
                partes.add(d.getCantidadAusente() + " " + d.getTipoEntrada().getNombre() + " sin usar");
            }
        }
        return String.join(", ", partes);
    }

    private void imprimirAlConfirmar(List<Long> ids, String impresora) {
        Runnable imprimir = () -> {
            for (Long id : ids) {
                try {
                    impresionService.imprimirVoucher(id, impresora);
                } catch (Exception e) {
                    log.warn("No se pudo mandar a imprimir el voucher ID {}: {}", id, e.getMessage());
                }
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // En otro hilo: acá la transacción ya terminó pero sigue atada al hilo.
                    CompletableFuture.runAsync(imprimir);
                }
            });
        } else {
            imprimir.run();
        }
    }
}
