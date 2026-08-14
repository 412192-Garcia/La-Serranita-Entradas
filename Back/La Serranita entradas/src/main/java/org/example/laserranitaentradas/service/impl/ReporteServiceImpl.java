package org.example.laserranitaentradas.service.impl;

import org.example.laserranitaentradas.model.dto.AfluenciaDiariaDTO;
import org.example.laserranitaentradas.model.dto.CajaResumenReporteDTO;
import org.example.laserranitaentradas.model.dto.ComprasPorEstadoDTO;
import org.example.laserranitaentradas.model.dto.DesgloseTipoEntradaDTO;
import org.example.laserranitaentradas.model.dto.RecaudacionPorFormaPagoDTO;
import org.example.laserranitaentradas.model.dto.ReporteResumenDTO;
import org.example.laserranitaentradas.model.dto.TipoListadoCompra;
import org.example.laserranitaentradas.model.dto.UsoPromocionDTO;
import org.example.laserranitaentradas.model.dto.VentaArticuloVarioDTO;
import org.example.laserranitaentradas.model.dto.VentasDolaresDTO;
import org.example.laserranitaentradas.model.dto.VentasPorHoraDTO;
import org.example.laserranitaentradas.model.dto.VentasPorOrigenDTO;
import org.example.laserranitaentradas.model.entity.ArticuloVario;
import org.example.laserranitaentradas.model.entity.Caja;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.CompraDetalle;
import org.example.laserranitaentradas.model.entity.EstadoCompra;
import org.example.laserranitaentradas.model.entity.FormaPago;
import org.example.laserranitaentradas.model.entity.Promocion;
import org.example.laserranitaentradas.model.entity.Tipo;
import org.example.laserranitaentradas.model.entity.TipoEntrada;
import org.example.laserranitaentradas.model.entity.TipoMovimientoCaja;
import org.example.laserranitaentradas.repository.CajaRepository;
import org.example.laserranitaentradas.repository.CompraRepository;
import org.example.laserranitaentradas.repository.RetiroCajaRepository;
import org.example.laserranitaentradas.repository.TipoEntradaRepository;
import org.example.laserranitaentradas.service.ReporteService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
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
    private static final Set<EstadoCompra> ESTADOS_COBRADOS =
            EnumSet.of(EstadoCompra.APROBADO, EstadoCompra.USADO, EstadoCompra.VENDIDO_EN_PUERTA);
    /** "Vendidas" para afluencia: incluye también lo reservado en efectivo, todavía no cobrado pero ya comprometido. */
    private static final Set<EstadoCompra> ESTADOS_VENDIDOS =
            EnumSet.of(EstadoCompra.APROBADO, EstadoCompra.USADO, EstadoCompra.RESERVADO_EFECTIVO, EstadoCompra.VENDIDO_EN_PUERTA);
    /** Gente que realmente entró: el check-in por DNI y la venta en puerta valen igual. */
    private static final Set<EstadoCompra> ESTADOS_INGRESADOS =
            EnumSet.of(EstadoCompra.USADO, EstadoCompra.VENDIDO_EN_PUERTA);

    private final CompraRepository compraRepository;
    private final TipoEntradaRepository tipoEntradaRepository;
    private final CajaRepository cajaRepository;
    private final RetiroCajaRepository retiroCajaRepository;

    public ReporteServiceImpl(CompraRepository compraRepository, TipoEntradaRepository tipoEntradaRepository,
                              CajaRepository cajaRepository, RetiroCajaRepository retiroCajaRepository) {
        this.compraRepository = compraRepository;
        this.tipoEntradaRepository = tipoEntradaRepository;
        this.cajaRepository = cajaRepository;
        this.retiroCajaRepository = retiroCajaRepository;
    }

    @Override
    public ReporteResumenDTO generarResumen(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null || hasta.isBefore(desde)) {
            throw new IllegalArgumentException("El rango de fechas es inválido");
        }

        List<Compra> compras = compraRepository.findAllByFechaVisitaBetween(desde, hasta);

        BigDecimal recaudacionTotal = BigDecimal.ZERO;
        long cantidadCompras = 0;

        Map<LocalDate, Long> vendidosAnticipadaPorDia = new HashMap<>();
        Map<LocalDate, Long> validadosAnticipadaPorDia = new HashMap<>();
        Map<LocalDate, Long> vendidosBoleteriaPorDia = new HashMap<>();

        Map<Long, TipoEntrada> tiposPorId = new HashMap<>();
        Map<Long, Long> cantidadAnticipadaPorTipo = new HashMap<>();
        Map<Long, BigDecimal> montoAnticipadaPorTipo = new HashMap<>();
        Map<Long, Long> cantidadBoleteriaPorTipo = new HashMap<>();
        Map<Long, BigDecimal> montoBoleteriaPorTipo = new HashMap<>();

        Map<Long, TipoEntrada> extrasPorId = new HashMap<>();
        Map<Long, Long> cantidadAnticipadaPorExtra = new HashMap<>();
        Map<Long, BigDecimal> montoAnticipadaPorExtra = new HashMap<>();
        Map<Long, Long> cantidadBoleteriaPorExtra = new HashMap<>();
        Map<Long, BigDecimal> montoBoleteriaPorExtra = new HashMap<>();

        Map<FormaPago, Long> cantidadPorFormaPago = new EnumMap<>(FormaPago.class);
        Map<FormaPago, BigDecimal> montoPorFormaPago = new EnumMap<>(FormaPago.class);

        Map<EstadoCompra, Long> cantidadPorEstado = new EnumMap<>(EstadoCompra.class);
        for (EstadoCompra estado : EstadoCompra.values()) {
            cantidadPorEstado.put(estado, 0L);
        }

        Map<Integer, Long> cantidadComprasAnticipadaPorHora = new HashMap<>();
        Map<Integer, Long> cantidadPasesAnticipadaPorHora = new HashMap<>();
        Map<Integer, Long> cantidadComprasBoleteriaPorHora = new HashMap<>();
        Map<Integer, Long> cantidadPasesBoleteriaPorHora = new HashMap<>();

        Map<TipoListadoCompra, Long> cantidadPorOrigen = new EnumMap<>(TipoListadoCompra.class);
        Map<TipoListadoCompra, BigDecimal> montoPorOrigen = new EnumMap<>(TipoListadoCompra.class);
        BigDecimal totalDescuentos = BigDecimal.ZERO;
        long cantidadComprasConDescuento = 0;
        /** Gente que realmente cruzó la puerta: venta en puerta (siempre) + reservas anticipadas ya validadas por DNI. */
        long personasIngresadas = 0;

        Map<Long, ArticuloVario> articulosPorId = new HashMap<>();
        Map<Long, Long> cantidadPorArticulo = new HashMap<>();
        Map<Long, BigDecimal> montoPorArticulo = new HashMap<>();
        long cantidadArticulosSinCatalogo = 0;
        BigDecimal montoArticulosSinCatalogo = BigDecimal.ZERO;

        Map<Long, Promocion> promocionesPorId = new HashMap<>();
        Map<Long, Long> cantidadPorPromocion = new HashMap<>();
        Map<Long, BigDecimal> descuentoPorPromocion = new HashMap<>();

        long cantidadVentasDolares = 0;
        BigDecimal totalDolaresRecibidos = BigDecimal.ZERO;
        BigDecimal totalEquivalenteArsDolares = BigDecimal.ZERO;
        BigDecimal sumaCotizacionPonderada = BigDecimal.ZERO;

        for (Compra compra : compras) {
            boolean cobrada = ESTADOS_COBRADOS.contains(compra.getEstado());
            boolean vendida = ESTADOS_VENDIDOS.contains(compra.getEstado());
            boolean validada = ESTADOS_INGRESADOS.contains(compra.getEstado());
            // La venta de puerta es lo único que nunca fue una reserva anticipada.
            boolean esBoleteria = compra.getEstado() == EstadoCompra.VENDIDO_EN_PUERTA;

            cantidadPorEstado.merge(compra.getEstado(), 1L, Long::sum);

            if (cobrada) {
                recaudacionTotal = recaudacionTotal.add(compra.getMontoTotal());
                cantidadCompras++;
                if (compra.getDescuentoAplicado() != null && compra.getDescuentoAplicado().compareTo(BigDecimal.ZERO) > 0) {
                    totalDescuentos = totalDescuentos.add(compra.getDescuentoAplicado());
                    cantidadComprasConDescuento++;
                }
                cantidadPorFormaPago.merge(compra.getFormaPago(), 1L, Long::sum);
                montoPorFormaPago.merge(compra.getFormaPago(), compra.getMontoTotal(), BigDecimal::add);

                if (compra.getPromocion() != null) {
                    Promocion promo = compra.getPromocion();
                    promocionesPorId.putIfAbsent(promo.getId(), promo);
                    cantidadPorPromocion.merge(promo.getId(), 1L, Long::sum);
                    BigDecimal descuentoPromo = compra.getDescuentoAplicado() != null ? compra.getDescuentoAplicado() : BigDecimal.ZERO;
                    descuentoPorPromocion.merge(promo.getId(), descuentoPromo, BigDecimal::add);
                }
                if (compra.getDolaresRecibidos() != null) {
                    cantidadVentasDolares++;
                    totalDolaresRecibidos = totalDolaresRecibidos.add(compra.getDolaresRecibidos());
                    totalEquivalenteArsDolares = totalEquivalenteArsDolares.add(compra.getMontoTotal());
                    if (compra.getCotizacionDolar() != null) {
                        sumaCotizacionPonderada = sumaCotizacionPonderada.add(
                                compra.getCotizacionDolar().multiply(compra.getDolaresRecibidos()));
                    }
                }

                TipoListadoCompra origen = esBoleteria ? TipoListadoCompra.BOLETERIA : TipoListadoCompra.ANTICIPADA;
                cantidadPorOrigen.merge(origen, 1L, Long::sum);
                montoPorOrigen.merge(origen, compra.getMontoTotal(), BigDecimal::add);

                int hora = compra.getFechaCreacion().getHour();
                long pasesEntradaCompra = compra.getDetalles().stream()
                        .filter(d -> d.getTipoEntrada() != null && d.getTipoEntrada().getTipo() == Tipo.ENTRADA)
                        .mapToLong(CompraDetalle::getCantidad)
                        .sum();
                if (esBoleteria) {
                    cantidadComprasBoleteriaPorHora.merge(hora, 1L, Long::sum);
                    cantidadPasesBoleteriaPorHora.merge(hora, pasesEntradaCompra, Long::sum);
                } else {
                    cantidadComprasAnticipadaPorHora.merge(hora, 1L, Long::sum);
                    cantidadPasesAnticipadaPorHora.merge(hora, pasesEntradaCompra, Long::sum);
                }
            }

            if (vendida) {
                long pasesEntrada = compra.getDetalles().stream()
                        .filter(d -> d.getTipoEntrada() != null && d.getTipoEntrada().getTipo() == Tipo.ENTRADA)
                        .mapToLong(CompraDetalle::getCantidad)
                        .sum();
                if (esBoleteria) {
                    vendidosBoleteriaPorDia.merge(compra.getFechaVisita(), pasesEntrada, Long::sum);
                    // La venta de puerta implica ingreso inmediato: no hay validación separada.
                    personasIngresadas += pasesEntrada;
                } else {
                    vendidosAnticipadaPorDia.merge(compra.getFechaVisita(), pasesEntrada, Long::sum);
                    if (validada) {
                        validadosAnticipadaPorDia.merge(compra.getFechaVisita(), pasesEntrada, Long::sum);
                        personasIngresadas += pasesEntrada;
                    }
                }
            }

            if (cobrada) {
                for (CompraDetalle detalle : compra.getDetalles()) {
                    // Las líneas de artículo vario (venta en puerta) no tienen tipoEntrada: no
                    // entran en el desglose por tipo/extra, van al reporte de artículos varios aparte.
                    if (detalle.getTipoEntrada() == null) {
                        BigDecimal montoLinea = detalle.getPrecioUnitario() != null
                                ? detalle.getPrecioUnitario().multiply(BigDecimal.valueOf(detalle.getCantidad()))
                                : BigDecimal.ZERO;
                        if (detalle.getArticuloVario() != null) {
                            ArticuloVario articulo = detalle.getArticuloVario();
                            articulosPorId.putIfAbsent(articulo.getId(), articulo);
                            cantidadPorArticulo.merge(articulo.getId(), (long) detalle.getCantidad(), Long::sum);
                            montoPorArticulo.merge(articulo.getId(), montoLinea, BigDecimal::add);
                        } else {
                            // Línea suelta que el cajero tipeó sin cargarla al catálogo (descripcionLibre).
                            cantidadArticulosSinCatalogo += detalle.getCantidad();
                            montoArticulosSinCatalogo = montoArticulosSinCatalogo.add(montoLinea);
                        }
                        continue;
                    }
                    TipoEntrada tipo = detalle.getTipoEntrada();
                    BigDecimal monto = tipo.getPrecio().multiply(BigDecimal.valueOf(detalle.getCantidad()));
                    if (tipo.getTipo() == Tipo.ENTRADA) {
                        tiposPorId.putIfAbsent(tipo.getId(), tipo);
                        if (esBoleteria) {
                            cantidadBoleteriaPorTipo.merge(tipo.getId(), (long) detalle.getCantidad(), Long::sum);
                            montoBoleteriaPorTipo.merge(tipo.getId(), monto, BigDecimal::add);
                        } else {
                            cantidadAnticipadaPorTipo.merge(tipo.getId(), (long) detalle.getCantidad(), Long::sum);
                            montoAnticipadaPorTipo.merge(tipo.getId(), monto, BigDecimal::add);
                        }
                    } else {
                        extrasPorId.putIfAbsent(tipo.getId(), tipo);
                        if (esBoleteria) {
                            cantidadBoleteriaPorExtra.merge(tipo.getId(), (long) detalle.getCantidad(), Long::sum);
                            montoBoleteriaPorExtra.merge(tipo.getId(), monto, BigDecimal::add);
                        } else {
                            cantidadAnticipadaPorExtra.merge(tipo.getId(), (long) detalle.getCantidad(), Long::sum);
                            montoAnticipadaPorExtra.merge(tipo.getId(), monto, BigDecimal::add);
                        }
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
                cantidadAnticipadaPorTipo.putIfAbsent(tipo.getId(), 0L);
                montoAnticipadaPorTipo.putIfAbsent(tipo.getId(), BigDecimal.ZERO);
                cantidadBoleteriaPorTipo.putIfAbsent(tipo.getId(), 0L);
                montoBoleteriaPorTipo.putIfAbsent(tipo.getId(), BigDecimal.ZERO);
            } else {
                extrasPorId.putIfAbsent(tipo.getId(), tipo);
                cantidadAnticipadaPorExtra.putIfAbsent(tipo.getId(), 0L);
                montoAnticipadaPorExtra.putIfAbsent(tipo.getId(), BigDecimal.ZERO);
                cantidadBoleteriaPorExtra.putIfAbsent(tipo.getId(), 0L);
                montoBoleteriaPorExtra.putIfAbsent(tipo.getId(), BigDecimal.ZERO);
            }
        }

        List<AfluenciaDiariaDTO> afluenciaDiaria = new ArrayList<>();
        for (LocalDate fecha = desde; !fecha.isAfter(hasta); fecha = fecha.plusDays(1)) {
            afluenciaDiaria.add(new AfluenciaDiariaDTO(
                    fecha,
                    vendidosAnticipadaPorDia.getOrDefault(fecha, 0L),
                    validadosAnticipadaPorDia.getOrDefault(fecha, 0L),
                    vendidosBoleteriaPorDia.getOrDefault(fecha, 0L)));
        }

        List<DesgloseTipoEntradaDTO> desglosePorTipo = ordenarPorNombre(tiposPorId,
                cantidadAnticipadaPorTipo, montoAnticipadaPorTipo, cantidadBoleteriaPorTipo, montoBoleteriaPorTipo);
        List<DesgloseTipoEntradaDTO> desgloseExtras = ordenarPorNombre(extrasPorId,
                cantidadAnticipadaPorExtra, montoAnticipadaPorExtra, cantidadBoleteriaPorExtra, montoBoleteriaPorExtra);

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
                    cantidadComprasAnticipadaPorHora.getOrDefault(hora, 0L),
                    cantidadPasesAnticipadaPorHora.getOrDefault(hora, 0L),
                    cantidadComprasBoleteriaPorHora.getOrDefault(hora, 0L),
                    cantidadPasesBoleteriaPorHora.getOrDefault(hora, 0L)));
        }

        List<VentasPorOrigenDTO> ventasPorOrigen = new ArrayList<>();
        for (TipoListadoCompra origen : TipoListadoCompra.values()) {
            ventasPorOrigen.add(new VentasPorOrigenDTO(
                    origen,
                    origen == TipoListadoCompra.BOLETERIA ? "Boletería (venta de puerta)" : "Reserva anticipada",
                    cantidadPorOrigen.getOrDefault(origen, 0L),
                    montoPorOrigen.getOrDefault(origen, BigDecimal.ZERO)));
        }

        // Las cajas se filtran por cuándo se cerraron, no por fechaVisita: es un concepto
        // de turno de trabajo, no de día de visita del parque.
        List<Caja> cajasCerradas = cajaRepository.findAllByFechaCierreBetweenOrderByFechaCierreAsc(
                desde.atStartOfDay(), hasta.atTime(LocalTime.MAX));

        List<CajaResumenReporteDTO> cajas = new ArrayList<>();
        BigDecimal totalRetirosCajas = BigDecimal.ZERO;
        BigDecimal totalFaltantesCajas = BigDecimal.ZERO;
        BigDecimal totalSobrantesCajas = BigDecimal.ZERO;

        for (Caja caja : cajasCerradas) {
            BigDecimal retirosCaja = retiroCajaRepository.findAllByCajaIdOrderByFechaAsc(caja.getId()).stream()
                    .map(r -> r.getTipo() == TipoMovimientoCaja.APORTE ? r.getMonto().negate() : r.getMonto())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            totalRetirosCajas = totalRetirosCajas.add(retirosCaja);

            BigDecimal diferencia = caja.getDiferencia() != null ? caja.getDiferencia() : BigDecimal.ZERO;
            if (diferencia.compareTo(BigDecimal.ZERO) < 0) {
                totalFaltantesCajas = totalFaltantesCajas.add(diferencia.abs());
            } else {
                totalSobrantesCajas = totalSobrantesCajas.add(diferencia);
            }

            cajas.add(new CajaResumenReporteDTO(
                    caja.getId(),
                    caja.getUsuario().getNombre() + " " + caja.getUsuario().getApellido(),
                    caja.getFechaApertura(),
                    caja.getFechaCierre(),
                    caja.getMontoInicial(),
                    retirosCaja,
                    caja.getMontoEsperado(),
                    caja.getMontoContado(),
                    diferencia));
        }

        List<VentaArticuloVarioDTO> ventasArticulosVarios = new ArrayList<>(articulosPorId.values().stream()
                .sorted((a, b) -> a.getNombre().compareToIgnoreCase(b.getNombre()))
                .map(articulo -> new VentaArticuloVarioDTO(articulo.getId(), articulo.getNombre(),
                        cantidadPorArticulo.get(articulo.getId()), montoPorArticulo.get(articulo.getId())))
                .toList());
        if (cantidadArticulosSinCatalogo > 0) {
            ventasArticulosVarios.add(new VentaArticuloVarioDTO(
                    null, "Sin catálogo (texto libre)", cantidadArticulosSinCatalogo, montoArticulosSinCatalogo));
        }

        List<UsoPromocionDTO> usoPromociones = promocionesPorId.values().stream()
                .sorted((a, b) -> a.getNombre().compareToIgnoreCase(b.getNombre()))
                .map(promo -> new UsoPromocionDTO(promo.getId(), promo.getNombre(),
                        cantidadPorPromocion.get(promo.getId()), descuentoPorPromocion.get(promo.getId())))
                .toList();

        VentasDolaresDTO ventasDolares = new VentasDolaresDTO(
                cantidadVentasDolares,
                totalDolaresRecibidos,
                totalEquivalenteArsDolares,
                totalDolaresRecibidos.compareTo(BigDecimal.ZERO) > 0
                        ? sumaCotizacionPonderada.divide(totalDolaresRecibidos, 2, RoundingMode.HALF_UP)
                        : null);

        return new ReporteResumenDTO(desde, hasta, recaudacionTotal, cantidadCompras, personasIngresadas, afluenciaDiaria,
                desglosePorTipo, recaudacionPorFormaPago, comprasPorEstado, desgloseExtras, ventasPorHora,
                ventasPorOrigen, totalDescuentos, cantidadComprasConDescuento,
                cajas, totalRetirosCajas, totalFaltantesCajas, totalSobrantesCajas,
                ventasArticulosVarios, usoPromociones, ventasDolares);
    }

    private List<DesgloseTipoEntradaDTO> ordenarPorNombre(Map<Long, TipoEntrada> tiposPorId,
                                                           Map<Long, Long> cantidadAnticipadaPorTipo,
                                                           Map<Long, BigDecimal> montoAnticipadaPorTipo,
                                                           Map<Long, Long> cantidadBoleteriaPorTipo,
                                                           Map<Long, BigDecimal> montoBoleteriaPorTipo) {
        return tiposPorId.values().stream()
                .sorted((a, b) -> a.getNombre().compareToIgnoreCase(b.getNombre()))
                .map(tipo -> new DesgloseTipoEntradaDTO(
                        tipo.getId(),
                        tipo.getNombre(),
                        cantidadAnticipadaPorTipo.get(tipo.getId()),
                        montoAnticipadaPorTipo.get(tipo.getId()),
                        cantidadBoleteriaPorTipo.get(tipo.getId()),
                        montoBoleteriaPorTipo.get(tipo.getId())))
                .toList();
    }
}
