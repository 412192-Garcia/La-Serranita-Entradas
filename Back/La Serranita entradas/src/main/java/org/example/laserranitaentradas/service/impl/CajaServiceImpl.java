package org.example.laserranitaentradas.service.impl;

import jakarta.transaction.Transactional;
import org.example.laserranitaentradas.model.dto.CajaResponseDTO;
import org.example.laserranitaentradas.model.dto.CierrePosnetRequestDTO;
import org.example.laserranitaentradas.model.dto.CierrePosnetResponseDTO;
import org.example.laserranitaentradas.model.dto.ConteoDenominacionDTO;
import org.example.laserranitaentradas.model.dto.IngresoEntradasResponseDTO;
import org.example.laserranitaentradas.model.dto.OperacionCajaDTO;
import org.example.laserranitaentradas.model.dto.RetiroCajaResponseDTO;
import org.example.laserranitaentradas.model.entity.Caja;
import org.example.laserranitaentradas.model.entity.CierrePosnet;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.CompraDetalle;
import org.example.laserranitaentradas.model.entity.ConteoDenominacion;
import org.example.laserranitaentradas.model.entity.EstadoCompra;
import org.example.laserranitaentradas.model.entity.FormaPago;
import org.example.laserranitaentradas.model.entity.IngresoEntradas;
import org.example.laserranitaentradas.model.entity.RetiroCaja;
import org.example.laserranitaentradas.model.entity.Usuario;
import org.example.laserranitaentradas.repository.CajaRepository;
import org.example.laserranitaentradas.repository.CierrePosnetRepository;
import org.example.laserranitaentradas.repository.CompraRepository;
import org.example.laserranitaentradas.repository.IngresoEntradasRepository;
import org.example.laserranitaentradas.repository.RetiroCajaRepository;
import org.example.laserranitaentradas.service.CajaService;
import org.example.laserranitaentradas.service.UsuarioService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CajaServiceImpl implements CajaService {

    private static final Set<Integer> DENOMINACIONES_VALIDAS = Set.of(100, 200, 500, 1000, 2000, 10000, 20000);
    private static final Set<FormaPago> FORMAS_PAGO_POSNET = Set.of(FormaPago.TARJETA, FormaPago.MERCADO_PAGO_QR);

    private final CajaRepository cajaRepository;
    private final RetiroCajaRepository retiroCajaRepository;
    private final CierrePosnetRepository cierrePosnetRepository;
    private final IngresoEntradasRepository ingresoEntradasRepository;
    private final CompraRepository compraRepository;
    private final UsuarioService usuarioService;

    public CajaServiceImpl(CajaRepository cajaRepository,
                            RetiroCajaRepository retiroCajaRepository,
                            CierrePosnetRepository cierrePosnetRepository,
                            IngresoEntradasRepository ingresoEntradasRepository,
                            CompraRepository compraRepository,
                            UsuarioService usuarioService) {
        this.cajaRepository = cajaRepository;
        this.retiroCajaRepository = retiroCajaRepository;
        this.cierrePosnetRepository = cierrePosnetRepository;
        this.ingresoEntradasRepository = ingresoEntradasRepository;
        this.compraRepository = compraRepository;
        this.usuarioService = usuarioService;
    }

    @Override
    public CajaResponseDTO getActual(Long usuarioId) {
        return cajaRepository.findByUsuarioIdAndFechaCierreIsNull(usuarioId)
                .map(this::toDto)
                .orElse(null);
    }

    @Override
    public Caja getAbiertaOrThrow(Long usuarioId) {
        return cajaRepository.findByUsuarioIdAndFechaCierreIsNull(usuarioId)
                .orElseThrow(() -> new IllegalStateException("No hay una caja abierta: abrí la caja antes de cobrar."));
    }

    @Transactional
    @Override
    public CajaResponseDTO abrir(Long usuarioId, BigDecimal montoInicial, Integer entradasFisicasInicial) {
        if (montoInicial == null || montoInicial.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El monto inicial no puede ser negativo");
        }
        if (entradasFisicasInicial == null || entradasFisicasInicial < 0) {
            throw new IllegalArgumentException("Indicá con cuántas entradas físicas arranca el turno");
        }
        if (cajaRepository.findByUsuarioIdAndFechaCierreIsNull(usuarioId).isPresent()) {
            throw new IllegalStateException("Ya hay una caja abierta para este usuario");
        }
        Usuario usuario = usuarioService.obtenerUsuarioPorId(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado para id: " + usuarioId));

        Caja caja = Caja.builder()
                .usuario(usuario)
                .fechaApertura(LocalDateTime.now())
                .montoInicial(montoInicial)
                .entradasFisicasInicial(entradasFisicasInicial)
                .build();

        return toDto(cajaRepository.save(caja));
    }

    @Transactional
    @Override
    public CajaResponseDTO registrarRetiro(Long usuarioId, BigDecimal monto, String motivo) {
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto del retiro tiene que ser mayor a cero");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("Indicá el motivo del retiro");
        }

        Caja caja = getAbiertaOrThrow(usuarioId);

        RetiroCaja retiro = RetiroCaja.builder()
                .caja(caja)
                .monto(monto)
                .motivo(motivo.trim())
                .fecha(LocalDateTime.now())
                .build();
        retiroCajaRepository.save(retiro);

        return toDto(caja);
    }

    @Transactional
    @Override
    public CajaResponseDTO registrarIngresoEntradas(Long usuarioId, Integer cantidad) {
        if (cantidad == null || cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad de entradas tiene que ser mayor a cero");
        }

        Caja caja = getAbiertaOrThrow(usuarioId);

        IngresoEntradas ingreso = IngresoEntradas.builder()
                .caja(caja)
                .cantidad(cantidad)
                .fecha(LocalDateTime.now())
                .build();
        ingresoEntradasRepository.save(ingreso);

        return toDto(caja);
    }

    @Transactional
    @Override
    public CajaResponseDTO cerrar(Long usuarioId, List<ConteoDenominacionDTO> conteoEfectivo,
                                   List<CierrePosnetRequestDTO> cierresPosnet, Integer entradasFisicasFinal) {
        Caja caja = getAbiertaOrThrow(usuarioId);

        List<ConteoDenominacion> conteo = validarYMapearConteo(conteoEfectivo);
        BigDecimal montoContado = conteo.stream()
                .map(c -> BigDecimal.valueOf(c.getDenominacion()).multiply(BigDecimal.valueOf(c.getCantidad())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<CierrePosnetRequestDTO> cierres = cierresPosnet == null ? List.of() : cierresPosnet;
        for (CierrePosnetRequestDTO c : cierres) {
            if (c.getFormaPago() == null || !FORMAS_PAGO_POSNET.contains(c.getFormaPago())) {
                throw new IllegalArgumentException("El cierre de posnet sólo admite Tarjeta o QR");
            }
            if (c.getMonto() == null || c.getMonto().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("El monto del cierre de posnet tiene que ser mayor a cero");
            }
        }
        if (entradasFisicasFinal == null || entradasFisicasFinal < 0) {
            throw new IllegalArgumentException("Indicá con cuántas entradas físicas termina el turno");
        }

        BigDecimal totalVentasEfectivo = sumVentasPorFormaPago(caja.getId(), FormaPago.EFECTIVO_BOLETERIA);
        BigDecimal totalRetiros = sumRetiros(caja.getId());
        BigDecimal montoEsperado = caja.getMontoInicial().add(totalVentasEfectivo).subtract(totalRetiros);

        LocalDateTime ahora = LocalDateTime.now();
        caja.setFechaCierre(ahora);
        caja.setMontoContado(montoContado);
        caja.setMontoEsperado(montoEsperado);
        caja.setDiferencia(montoContado.subtract(montoEsperado));
        caja.getConteoEfectivo().clear();
        caja.getConteoEfectivo().addAll(conteo);
        caja.setEntradasFisicasFinal(entradasFisicasFinal);

        Caja guardada = cajaRepository.save(caja);

        for (CierrePosnetRequestDTO c : cierres) {
            cierrePosnetRepository.save(CierrePosnet.builder()
                    .caja(guardada)
                    .formaPago(c.getFormaPago())
                    .monto(c.getMonto())
                    .nota(c.getNota())
                    .fecha(ahora)
                    .build());
        }

        return toDto(guardada);
    }

    private List<ConteoDenominacion> validarYMapearConteo(List<ConteoDenominacionDTO> conteoEfectivo) {
        List<ConteoDenominacion> resultado = new ArrayList<>();
        if (conteoEfectivo == null) return resultado;
        for (ConteoDenominacionDTO c : conteoEfectivo) {
            if (c.getDenominacion() == null || !DENOMINACIONES_VALIDAS.contains(c.getDenominacion())) {
                throw new IllegalArgumentException("Denominación de billete inválida: " + c.getDenominacion());
            }
            int cantidad = c.getCantidad() == null ? 0 : c.getCantidad();
            if (cantidad < 0) {
                throw new IllegalArgumentException("La cantidad de billetes no puede ser negativa");
            }
            resultado.add(new ConteoDenominacion(c.getDenominacion(), cantidad));
        }
        return resultado;
    }

    private BigDecimal sumVentasPorFormaPago(Long cajaId, FormaPago formaPago) {
        return compraRepository.findAllByCajaId(cajaId).stream()
                .filter(c -> c.getFormaPago() == formaPago && c.getEstado() != EstadoCompra.CANCELADO)
                .map(Compra::getMontoTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumRetiros(Long cajaId) {
        return retiroCajaRepository.findAllByCajaIdOrderByFechaAsc(cajaId).stream()
                .map(RetiroCaja::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumCierres(List<CierrePosnet> cierres, FormaPago formaPago) {
        return cierres.stream()
                .filter(c -> c.getFormaPago() == formaPago)
                .map(CierrePosnet::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Null si la caja no tiene cargado con cuántas entradas físicas arrancó (turnos previos a este campo). */
    private Integer calcularEntradasEsperadas(Caja caja) {
        if (caja.getEntradasFisicasInicial() == null) return null;
        int entregadas = compraRepository.findAllByCajaId(caja.getId()).stream()
                .filter(c -> c.getEstado() != EstadoCompra.CANCELADO)
                .flatMap(c -> c.getDetalles().stream())
                .filter(d -> d.getTipoEntrada() != null && Boolean.TRUE.equals(d.getTipoEntrada().getEntregaEntrada()))
                .mapToInt(CompraDetalle::getCantidad)
                .sum();
        return caja.getEntradasFisicasInicial() + sumIngresosEntradas(caja.getId()) - entregadas;
    }

    private int sumIngresosEntradas(Long cajaId) {
        return ingresoEntradasRepository.findAllByCajaIdOrderByFechaAsc(cajaId).stream()
                .mapToInt(IngresoEntradas::getCantidad)
                .sum();
    }

    private List<OperacionCajaDTO> construirOperaciones(Long cajaId) {
        List<OperacionCajaDTO> operaciones = new ArrayList<>();

        for (Compra compra : compraRepository.findAllByCajaId(cajaId)) {
            if (compra.getEstado() == EstadoCompra.CANCELADO) continue;
            String detalle = compra.getDetalles().stream()
                    .map(d -> d.getCantidad() + "x " + (d.getTipoEntrada() != null ? d.getTipoEntrada().getNombre() : "?"))
                    .collect(Collectors.joining(", "));
            operaciones.add(OperacionCajaDTO.builder()
                    .tipo("VENTA")
                    .fecha(compra.getFechaValidacion() != null ? compra.getFechaValidacion() : compra.getFechaCreacion())
                    .monto(compra.getMontoTotal())
                    .formaPago(compra.getFormaPago())
                    .detalle(detalle)
                    .build());
        }

        for (RetiroCaja retiro : retiroCajaRepository.findAllByCajaIdOrderByFechaAsc(cajaId)) {
            operaciones.add(OperacionCajaDTO.builder()
                    .tipo("RETIRO")
                    .fecha(retiro.getFecha())
                    .monto(retiro.getMonto())
                    .detalle(retiro.getMotivo())
                    .build());
        }

        for (IngresoEntradas ingreso : ingresoEntradasRepository.findAllByCajaIdOrderByFechaAsc(cajaId)) {
            operaciones.add(OperacionCajaDTO.builder()
                    .tipo("INGRESO_ENTRADAS")
                    .fecha(ingreso.getFecha())
                    .monto(null)
                    .detalle("+" + ingreso.getCantidad() + " entradas")
                    .build());
        }

        operaciones.sort(Comparator.comparing(OperacionCajaDTO::getFecha, Comparator.nullsLast(Comparator.naturalOrder())));
        return operaciones;
    }

    private CajaResponseDTO toDto(Caja caja) {
        // Mientras la caja sigue abierta, ningún total "esperado" se expone: si el boletero
        // pudiera verlos antes de cerrar, alcanzaría con anotar esos mismos números para que
        // el cierre le dé perfecto aunque haya plata de menos. Recién se revelan al cerrar.
        boolean cerrada = caja.getFechaCierre() != null;
        BigDecimal totalRetiros = sumRetiros(caja.getId());

        BigDecimal totalVentasEfectivo = null;
        BigDecimal efectivoEsperado = null;
        BigDecimal totalVentasTarjeta = null;
        BigDecimal totalVentasQr = null;
        BigDecimal totalCerradoTarjeta = null;
        BigDecimal totalCerradoQr = null;
        BigDecimal diferenciaTarjeta = null;
        BigDecimal diferenciaQr = null;
        List<CierrePosnetResponseDTO> cierresDto = List.of();
        Integer entradasFisicasEsperadas = null;
        Integer diferenciaEntradas = null;
        List<OperacionCajaDTO> operaciones = null;

        if (cerrada) {
            totalVentasEfectivo = sumVentasPorFormaPago(caja.getId(), FormaPago.EFECTIVO_BOLETERIA);
            efectivoEsperado = caja.getMontoInicial().add(totalVentasEfectivo).subtract(totalRetiros);

            totalVentasTarjeta = sumVentasPorFormaPago(caja.getId(), FormaPago.TARJETA);
            totalVentasQr = sumVentasPorFormaPago(caja.getId(), FormaPago.MERCADO_PAGO_QR);

            List<CierrePosnet> cierres = cierrePosnetRepository.findAllByCajaIdOrderByIdAsc(caja.getId());
            totalCerradoTarjeta = sumCierres(cierres, FormaPago.TARJETA);
            totalCerradoQr = sumCierres(cierres, FormaPago.MERCADO_PAGO_QR);
            diferenciaTarjeta = totalCerradoTarjeta.subtract(totalVentasTarjeta);
            diferenciaQr = totalCerradoQr.subtract(totalVentasQr);
            cierresDto = cierres.stream()
                    .map(c -> CierrePosnetResponseDTO.builder()
                            .id(c.getId())
                            .formaPago(c.getFormaPago())
                            .monto(c.getMonto())
                            .nota(c.getNota())
                            .fecha(c.getFecha())
                            .build())
                    .toList();

            entradasFisicasEsperadas = calcularEntradasEsperadas(caja);
            if (entradasFisicasEsperadas != null && caja.getEntradasFisicasFinal() != null) {
                diferenciaEntradas = caja.getEntradasFisicasFinal() - entradasFisicasEsperadas;
            }

            operaciones = construirOperaciones(caja.getId());
        }

        List<RetiroCajaResponseDTO> retiros = retiroCajaRepository.findAllByCajaIdOrderByFechaAsc(caja.getId()).stream()
                .map(r -> RetiroCajaResponseDTO.builder()
                        .id(r.getId())
                        .monto(r.getMonto())
                        .motivo(r.getMotivo())
                        .fecha(r.getFecha())
                        .build())
                .toList();

        List<IngresoEntradasResponseDTO> ingresosEntradas = ingresoEntradasRepository.findAllByCajaIdOrderByFechaAsc(caja.getId()).stream()
                .map(i -> IngresoEntradasResponseDTO.builder()
                        .id(i.getId())
                        .cantidad(i.getCantidad())
                        .fecha(i.getFecha())
                        .build())
                .toList();
        int totalIngresosEntradas = ingresosEntradas.stream().mapToInt(IngresoEntradasResponseDTO::getCantidad).sum();

        List<ConteoDenominacionDTO> conteoDto = caja.getConteoEfectivo().stream()
                .map(c -> {
                    ConteoDenominacionDTO dto = new ConteoDenominacionDTO();
                    dto.setDenominacion(c.getDenominacion());
                    dto.setCantidad(c.getCantidad());
                    return dto;
                })
                .toList();

        return CajaResponseDTO.builder()
                .id(caja.getId())
                .estado(cerrada ? "CERRADA" : "ABIERTA")
                .fechaApertura(caja.getFechaApertura())
                .montoInicial(caja.getMontoInicial())
                .fechaCierre(caja.getFechaCierre())
                .totalVentasEfectivo(totalVentasEfectivo)
                .totalRetiros(totalRetiros)
                .efectivoEsperado(efectivoEsperado)
                .montoContado(caja.getMontoContado())
                .diferencia(caja.getDiferencia())
                .retiros(retiros)
                .conteoEfectivo(conteoDto)
                .totalVentasTarjeta(totalVentasTarjeta)
                .totalVentasQr(totalVentasQr)
                .totalCerradoTarjeta(totalCerradoTarjeta)
                .totalCerradoQr(totalCerradoQr)
                .diferenciaTarjeta(diferenciaTarjeta)
                .diferenciaQr(diferenciaQr)
                .cierresPosnet(cierresDto)
                .entradasFisicasInicial(caja.getEntradasFisicasInicial())
                .entradasFisicasFinal(caja.getEntradasFisicasFinal())
                .entradasFisicasEsperadas(entradasFisicasEsperadas)
                .diferenciaEntradas(diferenciaEntradas)
                .totalIngresosEntradas(totalIngresosEntradas)
                .ingresosEntradas(ingresosEntradas)
                .operaciones(operaciones)
                .build();
    }
}
