package org.example.laserranitaentradas.service.impl;

import org.example.laserranitaentradas.model.dto.AfluenciaDiariaDTO;
import org.example.laserranitaentradas.model.dto.ComprasPorEstadoDTO;
import org.example.laserranitaentradas.model.dto.DesgloseTipoEntradaDTO;
import org.example.laserranitaentradas.model.dto.RecaudacionPorFormaPagoDTO;
import org.example.laserranitaentradas.model.dto.ReporteResumenDTO;
import org.example.laserranitaentradas.model.dto.VentasPorHoraDTO;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.CompraDetalle;
import org.example.laserranitaentradas.model.entity.EstadoCompra;
import org.example.laserranitaentradas.model.entity.FormaPago;
import org.example.laserranitaentradas.model.entity.Tipo;
import org.example.laserranitaentradas.model.entity.TipoEntrada;
import org.example.laserranitaentradas.repository.CompraRepository;
import org.example.laserranitaentradas.repository.TipoEntradaRepository;
import org.example.laserranitaentradas.service.ReporteService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ReporteServiceImpl implements ReporteService {

    /** Lo efectivamente cobrado: reservas en efectivo pendientes de cobro no cuentan como recaudación. */
    private static final Set<EstadoCompra> ESTADOS_COBRADOS = EnumSet.of(EstadoCompra.APROBADO, EstadoCompra.USADO);
    /** "Vendidas" para afluencia: incluye también lo reservado en efectivo, todavía no cobrado pero ya comprometido. */
    private static final Set<EstadoCompra> ESTADOS_VENDIDOS =
            EnumSet.of(EstadoCompra.APROBADO, EstadoCompra.USADO, EstadoCompra.RESERVADO_EFECTIVO);

    private final CompraRepository compraRepository;
    private final TipoEntradaRepository tipoEntradaRepository;

    public ReporteServiceImpl(CompraRepository compraRepository, TipoEntradaRepository tipoEntradaRepository) {
        this.compraRepository = compraRepository;
        this.tipoEntradaRepository = tipoEntradaRepository;
    }

    @Override
    public ReporteResumenDTO generarResumen(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null || hasta.isBefore(desde)) {
            throw new IllegalArgumentException("El rango de fechas es inválido");
        }

        List<Compra> compras = compraRepository.findAllByFechaVisitaBetween(desde, hasta);

        BigDecimal recaudacionTotal = BigDecimal.ZERO;
        long cantidadCompras = 0;

        Map<LocalDate, Long> vendidosPorDia = new HashMap<>();
        Map<LocalDate, Long> validadosPorDia = new HashMap<>();

        Map<Long, TipoEntrada> tiposPorId = new HashMap<>();
        Map<Long, Long> cantidadPorTipo = new HashMap<>();
        Map<Long, BigDecimal> montoPorTipo = new HashMap<>();

        Map<Long, TipoEntrada> extrasPorId = new HashMap<>();
        Map<Long, Long> cantidadPorExtra = new HashMap<>();
        Map<Long, BigDecimal> montoPorExtra = new HashMap<>();

        Map<FormaPago, Long> cantidadPorFormaPago = new EnumMap<>(FormaPago.class);
        Map<FormaPago, BigDecimal> montoPorFormaPago = new EnumMap<>(FormaPago.class);

        Map<EstadoCompra, Long> cantidadPorEstado = new EnumMap<>(EstadoCompra.class);
        for (EstadoCompra estado : EstadoCompra.values()) {
            cantidadPorEstado.put(estado, 0L);
        }

        Map<Integer, Long> cantidadComprasPorHora = new HashMap<>();
        Map<Integer, Long> cantidadPasesPorHora = new HashMap<>();

        for (Compra compra : compras) {
            boolean cobrada = ESTADOS_COBRADOS.contains(compra.getEstado());
            boolean vendida = ESTADOS_VENDIDOS.contains(compra.getEstado());
            boolean validada = compra.getEstado() == EstadoCompra.USADO;

            cantidadPorEstado.merge(compra.getEstado(), 1L, Long::sum);

            if (cobrada) {
                recaudacionTotal = recaudacionTotal.add(compra.getMontoTotal());
                cantidadCompras++;
                cantidadPorFormaPago.merge(compra.getFormaPago(), 1L, Long::sum);
                montoPorFormaPago.merge(compra.getFormaPago(), compra.getMontoTotal(), BigDecimal::add);

                int hora = compra.getFechaCreacion().getHour();
                long pasesEntradaCompra = compra.getDetalles().stream()
                        .filter(d -> d.getTipoEntrada().getTipo() == Tipo.ENTRADA)
                        .mapToLong(CompraDetalle::getCantidad)
                        .sum();
                cantidadComprasPorHora.merge(hora, 1L, Long::sum);
                cantidadPasesPorHora.merge(hora, pasesEntradaCompra, Long::sum);
            }

            if (vendida) {
                long pasesEntrada = compra.getDetalles().stream()
                        .filter(d -> d.getTipoEntrada().getTipo() == Tipo.ENTRADA)
                        .mapToLong(CompraDetalle::getCantidad)
                        .sum();
                vendidosPorDia.merge(compra.getFechaVisita(), pasesEntrada, Long::sum);
                if (validada) {
                    validadosPorDia.merge(compra.getFechaVisita(), pasesEntrada, Long::sum);
                }
            }

            if (cobrada) {
                for (CompraDetalle detalle : compra.getDetalles()) {
                    TipoEntrada tipo = detalle.getTipoEntrada();
                    BigDecimal monto = tipo.getPrecio().multiply(BigDecimal.valueOf(detalle.getCantidad()));
                    if (tipo.getTipo() == Tipo.ENTRADA) {
                        tiposPorId.putIfAbsent(tipo.getId(), tipo);
                        cantidadPorTipo.merge(tipo.getId(), (long) detalle.getCantidad(), Long::sum);
                        montoPorTipo.merge(tipo.getId(), monto, BigDecimal::add);
                    } else {
                        extrasPorId.putIfAbsent(tipo.getId(), tipo);
                        cantidadPorExtra.merge(tipo.getId(), (long) detalle.getCantidad(), Long::sum);
                        montoPorExtra.merge(tipo.getId(), monto, BigDecimal::add);
                    }
                }
            }
        }

        // Los tipos activos se listan aunque no hayan tenido ventas en el rango, para que
        // el desglose no cambie de forma según lo que se vendió.
        for (TipoEntrada tipo : tipoEntradaRepository.findAll()) {
            if (!tipo.getActivo()) {
                continue;
            }
            if (tipo.getTipo() == Tipo.ENTRADA) {
                tiposPorId.putIfAbsent(tipo.getId(), tipo);
                cantidadPorTipo.putIfAbsent(tipo.getId(), 0L);
                montoPorTipo.putIfAbsent(tipo.getId(), BigDecimal.ZERO);
            } else {
                extrasPorId.putIfAbsent(tipo.getId(), tipo);
                cantidadPorExtra.putIfAbsent(tipo.getId(), 0L);
                montoPorExtra.putIfAbsent(tipo.getId(), BigDecimal.ZERO);
            }
        }

        List<AfluenciaDiariaDTO> afluenciaDiaria = new ArrayList<>();
        for (LocalDate fecha = desde; !fecha.isAfter(hasta); fecha = fecha.plusDays(1)) {
            afluenciaDiaria.add(new AfluenciaDiariaDTO(
                    fecha,
                    vendidosPorDia.getOrDefault(fecha, 0L),
                    validadosPorDia.getOrDefault(fecha, 0L)));
        }

        List<DesgloseTipoEntradaDTO> desglosePorTipo = ordenarPorNombre(tiposPorId, cantidadPorTipo, montoPorTipo);
        List<DesgloseTipoEntradaDTO> desgloseExtras = ordenarPorNombre(extrasPorId, cantidadPorExtra, montoPorExtra);

        List<RecaudacionPorFormaPagoDTO> recaudacionPorFormaPago = new ArrayList<>();
        for (FormaPago formaPago : FormaPago.values()) {
            recaudacionPorFormaPago.add(new RecaudacionPorFormaPagoDTO(
                    formaPago,
                    formaPago.getDescripcion(),
                    cantidadPorFormaPago.getOrDefault(formaPago, 0L),
                    montoPorFormaPago.getOrDefault(formaPago, BigDecimal.ZERO)));
        }

        List<ComprasPorEstadoDTO> comprasPorEstado = new ArrayList<>();
        for (EstadoCompra estado : EstadoCompra.values()) {
            comprasPorEstado.add(new ComprasPorEstadoDTO(estado, cantidadPorEstado.get(estado)));
        }

        List<VentasPorHoraDTO> ventasPorHora = new ArrayList<>();
        for (int hora = 0; hora < 24; hora++) {
            ventasPorHora.add(new VentasPorHoraDTO(hora,
                    cantidadComprasPorHora.getOrDefault(hora, 0L),
                    cantidadPasesPorHora.getOrDefault(hora, 0L)));
        }

        return new ReporteResumenDTO(desde, hasta, recaudacionTotal, cantidadCompras, afluenciaDiaria,
                desglosePorTipo, recaudacionPorFormaPago, comprasPorEstado, desgloseExtras, ventasPorHora);
    }

    private List<DesgloseTipoEntradaDTO> ordenarPorNombre(Map<Long, TipoEntrada> tiposPorId,
                                                           Map<Long, Long> cantidadPorTipo,
                                                           Map<Long, BigDecimal> montoPorTipo) {
        return tiposPorId.values().stream()
                .sorted((a, b) -> a.getNombre().compareToIgnoreCase(b.getNombre()))
                .map(tipo -> new DesgloseTipoEntradaDTO(
                        tipo.getId(),
                        tipo.getNombre(),
                        cantidadPorTipo.get(tipo.getId()),
                        montoPorTipo.get(tipo.getId())))
                .toList();
    }
}
