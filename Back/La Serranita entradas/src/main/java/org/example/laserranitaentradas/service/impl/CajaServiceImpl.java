package org.example.laserranitaentradas.service.impl;

import jakarta.transaction.Transactional;
import org.example.laserranitaentradas.model.dto.AjusteCajaRequestDTO;
import org.example.laserranitaentradas.model.dto.AjusteCajaResponseDTO;
import org.example.laserranitaentradas.model.dto.CajaAbiertaDTO;
import org.example.laserranitaentradas.model.dto.CajaDetalleAbiertaDTO;
import org.example.laserranitaentradas.model.dto.CajaResponseDTO;
import org.example.laserranitaentradas.model.dto.CajaResumenReporteDTO;
import org.example.laserranitaentradas.model.dto.CajasCerradasResponseDTO;
import org.example.laserranitaentradas.model.dto.CierrePosnetRequestDTO;
import org.example.laserranitaentradas.model.dto.CierrePosnetResponseDTO;
import org.example.laserranitaentradas.model.dto.ConteoDenominacionDTO;
import org.example.laserranitaentradas.model.dto.EntradasPorTipoDTO;
import org.example.laserranitaentradas.model.dto.IngresoEntradasResponseDTO;
import org.example.laserranitaentradas.model.dto.OperacionCajaDTO;
import org.example.laserranitaentradas.model.dto.RetiroCajaResponseDTO;
import org.example.laserranitaentradas.model.dto.SegmentoEntradaDTO;
import org.example.laserranitaentradas.model.entity.AjusteCaja;
import org.example.laserranitaentradas.model.entity.Caja;
import org.example.laserranitaentradas.model.entity.CierrePosnet;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.CompraDetalle;
import org.example.laserranitaentradas.model.entity.ConteoDenominacion;
import org.example.laserranitaentradas.model.entity.EstadoCompra;
import org.example.laserranitaentradas.model.entity.EstadoFactura;
import org.example.laserranitaentradas.model.entity.FormaPago;
import org.example.laserranitaentradas.model.entity.IngresoEntradas;
import org.example.laserranitaentradas.model.entity.RetiroCaja;
import org.example.laserranitaentradas.model.entity.Tipo;
import org.example.laserranitaentradas.model.entity.TipoEntrada;
import org.example.laserranitaentradas.model.entity.TipoMovimientoCaja;
import org.example.laserranitaentradas.model.entity.TipoMovimientoEntradas;
import org.example.laserranitaentradas.model.entity.Usuario;
import org.example.laserranitaentradas.repository.AjusteCajaRepository;
import org.example.laserranitaentradas.repository.CajaRepository;
import org.example.laserranitaentradas.repository.CajaSpecifications;
import org.example.laserranitaentradas.repository.CierrePosnetRepository;
import org.example.laserranitaentradas.repository.CompraRepository;
import org.example.laserranitaentradas.repository.FacturaRepository;
import org.example.laserranitaentradas.repository.IngresoEntradasRepository;
import org.example.laserranitaentradas.repository.RetiroCajaRepository;
import org.example.laserranitaentradas.service.CajaService;
import org.example.laserranitaentradas.service.TipoEntradaService;
import org.example.laserranitaentradas.service.UsuarioService;
import org.example.laserranitaentradas.service.afip.WsfeService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class CajaServiceImpl implements CajaService {

    private static final Set<Integer> DENOMINACIONES_VALIDAS = Set.of(100, 200, 500, 1000, 2000, 10000, 20000);
    private static final Set<FormaPago> FORMAS_PAGO_POSNET = Set.of(FormaPago.TARJETA, FormaPago.MERCADO_PAGO_QR);
    /** Formas de pago de boletería entre las que un ajuste manual puede traspasar monto. */
    private static final Set<FormaPago> FORMAS_PAGO_AJUSTABLES =
            Set.of(FormaPago.EFECTIVO_BOLETERIA, FormaPago.TARJETA, FormaPago.MERCADO_PAGO_QR);

    private final CajaRepository cajaRepository;
    private final RetiroCajaRepository retiroCajaRepository;
    private final CierrePosnetRepository cierrePosnetRepository;
    private final IngresoEntradasRepository ingresoEntradasRepository;
    private final CompraRepository compraRepository;
    private final AjusteCajaRepository ajusteCajaRepository;
    private final FacturaRepository facturaRepository;
    private final TipoEntradaService tipoEntradaService;
    private final UsuarioService usuarioService;

    public CajaServiceImpl(CajaRepository cajaRepository,
                            RetiroCajaRepository retiroCajaRepository,
                            CierrePosnetRepository cierrePosnetRepository,
                            IngresoEntradasRepository ingresoEntradasRepository,
                            CompraRepository compraRepository,
                            AjusteCajaRepository ajusteCajaRepository,
                            FacturaRepository facturaRepository,
                            TipoEntradaService tipoEntradaService,
                            UsuarioService usuarioService) {
        this.cajaRepository = cajaRepository;
        this.retiroCajaRepository = retiroCajaRepository;
        this.cierrePosnetRepository = cierrePosnetRepository;
        this.ingresoEntradasRepository = ingresoEntradasRepository;
        this.compraRepository = compraRepository;
        this.ajusteCajaRepository = ajusteCajaRepository;
        this.facturaRepository = facturaRepository;
        this.tipoEntradaService = tipoEntradaService;
        this.usuarioService = usuarioService;
    }

    /**
     * La caja sin cerrar del usuario abierta HOY. Las de días anteriores son atrasadas y no se mezclan con
     * las ventas de hoy.
     */
    private Optional<Caja> getCajaOperativaHoy(Long usuarioId) {
        LocalDateTime inicioDeHoy = LocalDate.now().atStartOfDay();
        return cajaRepository.findAllByUsuarioIdAndFechaCierreIsNull(usuarioId).stream()
                .filter(c -> c.getFechaApertura() != null && !c.getFechaApertura().isBefore(inicioDeHoy))
                .findFirst();
    }

    @Override
    public CajaResponseDTO getActual(Long usuarioId) {
        return getCajaOperativaHoy(usuarioId)
                .map(this::toDto)
                .orElse(null);
    }

    @Override
    public Caja getAbiertaOrThrow(Long usuarioId) {
        return getCajaOperativaHoy(usuarioId)
                .orElseThrow(() -> new IllegalStateException("No hay una caja abierta: abrí la caja antes de cobrar."));
    }

    @Override
    public Optional<Caja> getAbierta(Long usuarioId) {
        return getCajaOperativaHoy(usuarioId);
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
        if (getCajaOperativaHoy(usuarioId).isPresent()) {
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
    public CajaResponseDTO abrirSinControl(Long usuarioId) {
        if (getCajaOperativaHoy(usuarioId).isPresent()) {
            throw new IllegalStateException("Ya hay una caja abierta para este usuario");
        }
        Usuario usuario = usuarioService.obtenerUsuarioPorId(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado para id: " + usuarioId));

        // entradasFisicasInicial null a propósito: null es "sin control de entradas físicas".
        Caja caja = Caja.builder()
                .usuario(usuario)
                .fechaApertura(LocalDateTime.now())
                .montoInicial(BigDecimal.ZERO)
                .controlOmitido(true)
                .build();

        return toDto(cajaRepository.save(caja));
    }

    @Transactional
    @Override
    public void cerrarCajasSinControlAtrasadas() {
        cajaRepository.findAllByFechaCierreIsNullAndControlOmitidoTrue().forEach(this::cerrarSinControl);
    }

    @Transactional
    @Override
    public CajaResponseDTO cerrarSinControlPorId(Long cajaId) {
        Caja caja = cajaRepository.findById(cajaId)
                .orElseThrow(() -> new IllegalArgumentException("Caja no encontrada para id: " + cajaId));
        if (caja.getFechaCierre() != null) {
            throw new IllegalStateException("Esta caja ya está cerrada");
        }
        if (!Boolean.TRUE.equals(caja.getControlOmitido())) {
            // Una caja normal cerrada sin conteo pasa a "sin control" para no registrar un faltante
            // inventado.
            caja.setControlOmitido(true);
            org.example.laserranitaentradas.monitoreo.AuditoriaContexto.detalle(
                    "Caja con control cerrada sin conteo (efectivo inicial $" + caja.getMontoInicial().stripTrailingZeros().toPlainString() + ")");
        }
        cerrarSinControl(caja);
        return toDto(caja);
    }

    /** Sin conteo: montoContado = montoEsperado, diferencia 0. */
    private void cerrarSinControl(Caja caja) {
        List<Compra> compras = compraRepository.findAllByCajaId(caja.getId());
        List<RetiroCaja> movimientos = retiroCajaRepository.findAllByCajaIdOrderByFechaAsc(caja.getId());
        List<AjusteCaja> ajustes = ajusteCajaRepository.findAllByCajaIdOrderByFechaAsc(caja.getId());
        BigDecimal montoEsperado = calcularMontoEsperado(caja, compras, movimientos, ajustes);

        caja.setFechaCierre(LocalDateTime.now());
        caja.setMontoEsperado(montoEsperado);
        caja.setMontoContado(montoEsperado);
        caja.setDiferencia(BigDecimal.ZERO);
        caja.setCambioContado(BigDecimal.ZERO);
        caja.setEntradasFisicasRestantes(null);
        // Sin control no se exige el conteo de dólares.
        cajaRepository.save(caja);
    }

    @Transactional
    @Override
    public CajaResponseDTO registrarRetiro(Long usuarioId, BigDecimal monto, String motivo, TipoMovimientoCaja tipo,
                                            String idempotencyKey, LocalDateTime fechaOriginal) {
        // Si la operación ya se procesó (reintento), devolver lo guardado en vez de duplicar.
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<RetiroCaja> yaRegistrado = retiroCajaRepository.findByIdempotencyKey(idempotencyKey);
            if (yaRegistrado.isPresent()) {
                return toDto(yaRegistrado.get().getCaja());
            }
        }
        validarMovimiento(monto, motivo);
        Caja caja = getAbiertaOrThrow(usuarioId);
        return guardarMovimiento(caja, monto, motivo, tipo, idempotencyKey, fechaOriginal);
    }

    @Transactional
    @Override
    public CajaResponseDTO registrarRetiroComoAdmin(Long cajaId, BigDecimal monto, String motivo, TipoMovimientoCaja tipo) {
        validarMovimiento(monto, motivo);
        Caja caja = cajaRepository.findById(cajaId)
                .orElseThrow(() -> new IllegalArgumentException("Caja no encontrada para id: " + cajaId));
        if (caja.getFechaCierre() != null) {
            throw new IllegalStateException("Esta caja ya está cerrada");
        }
        return guardarMovimiento(caja, monto, motivo, tipo, null, null);
    }

    private void validarMovimiento(BigDecimal monto, String motivo) {
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto tiene que ser mayor a cero");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("Indicá el motivo");
        }
    }

    /** Guarda un retiro o un aporte sobre una caja ya resuelta y validada (propia o ajena vía admin). */
    private CajaResponseDTO guardarMovimiento(Caja caja, BigDecimal monto, String motivo, TipoMovimientoCaja tipo,
                                               String idempotencyKey, LocalDateTime fechaOriginal) {
        RetiroCaja movimiento = RetiroCaja.builder()
                .caja(caja)
                .monto(monto)
                .motivo(motivo.trim())
                .tipo(tipo == null ? TipoMovimientoCaja.RETIRO : tipo)
                .fecha(fechaOriginal == null ? LocalDateTime.now() : fechaOriginal)
                .idempotencyKey(idempotencyKey)
                .build();
        retiroCajaRepository.save(movimiento);

        return toDto(caja);
    }

    @Transactional
    @Override
    public CajaResponseDTO registrarIngresoEntradas(Long usuarioId, Integer cantidad, String motivo, TipoMovimientoEntradas tipo,
                                                     String idempotencyKey, LocalDateTime fechaOriginal) {
        // Ver registrarRetiro: un reintento con la misma clave no vuelve a sumar entradas.
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<IngresoEntradas> yaRegistrado = ingresoEntradasRepository.findByIdempotencyKey(idempotencyKey);
            if (yaRegistrado.isPresent()) {
                return toDto(yaRegistrado.get().getCaja());
            }
        }
        if (cantidad == null || cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad de entradas tiene que ser mayor a cero");
        }
        Caja caja = getAbiertaOrThrow(usuarioId);
        guardarIngresoEntradas(caja, cantidad, motivo, tipo, idempotencyKey, fechaOriginal);
        return toDto(caja);
    }

    @Transactional
    @Override
    public CajaResponseDTO registrarIngresoEntradasComoAdmin(Long cajaId, Integer cantidad, String motivo, TipoMovimientoEntradas tipo) {
        if (cantidad == null || cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad de entradas tiene que ser mayor a cero");
        }
        Caja caja = cajaRepository.findById(cajaId)
                .orElseThrow(() -> new IllegalArgumentException("Caja no encontrada para id: " + cajaId));
        if (caja.getFechaCierre() != null) {
            throw new IllegalStateException("Esta caja ya está cerrada");
        }
        guardarIngresoEntradas(caja, cantidad, motivo, tipo, null, null);
        return toDto(caja);
    }

    /** Guarda un ingreso o retiro de entradas ya validado. */
    private void guardarIngresoEntradas(Caja caja, Integer cantidad, String motivo, TipoMovimientoEntradas tipo,
                                         String idempotencyKey, LocalDateTime fechaOriginal) {
        TipoMovimientoEntradas tipoFinal = tipo == null ? TipoMovimientoEntradas.INGRESO : tipo;

        // Un retiro puede dejar el conteo en negativo: el front ya pide confirmación.
        IngresoEntradas ingreso = IngresoEntradas.builder()
                .caja(caja)
                .cantidad(cantidad)
                .motivo(motivo == null ? null : motivo.trim())
                .tipo(tipoFinal)
                .fecha(fechaOriginal == null ? LocalDateTime.now() : fechaOriginal)
                .idempotencyKey(idempotencyKey)
                .build();
        ingresoEntradasRepository.save(ingreso);
    }

    @Transactional
    @Override
    public CajaResponseDTO cerrar(Long usuarioId, List<ConteoDenominacionDTO> conteoEfectivo,
                                   List<CierrePosnetRequestDTO> cierresPosnet, Integer entradasFisicasRestantes,
                                   BigDecimal cambioContado, BigDecimal dolaresContado) {
        Caja caja = getAbiertaOrThrow(usuarioId);
        return cerrarCaja(caja, conteoEfectivo, cierresPosnet, entradasFisicasRestantes, cambioContado, dolaresContado);
    }

    @Transactional
    @Override
    public CajaResponseDTO cerrarComoAdmin(Long cajaId, List<ConteoDenominacionDTO> conteoEfectivo,
                                            List<CierrePosnetRequestDTO> cierresPosnet, Integer entradasFisicasRestantes,
                                            BigDecimal cambioContado, BigDecimal dolaresContado) {
        Caja caja = cajaRepository.findById(cajaId)
                .orElseThrow(() -> new IllegalArgumentException("Caja no encontrada para id: " + cajaId));
        if (caja.getFechaCierre() != null) {
            throw new IllegalStateException("Esta caja ya está cerrada");
        }
        return cerrarCaja(caja, conteoEfectivo, cierresPosnet, entradasFisicasRestantes, cambioContado, dolaresContado);
    }

    /** El cálculo real de cerrar, sobre una caja ya resuelta (propia o, vía admin, ajena). */
    private CajaResponseDTO cerrarCaja(Caja caja, List<ConteoDenominacionDTO> conteoEfectivo,
                                        List<CierrePosnetRequestDTO> cierresPosnet, Integer entradasFisicasRestantes,
                                        BigDecimal cambioContado, BigDecimal dolaresContado) {
        List<ConteoDenominacion> conteo = validarYMapearConteo(conteoEfectivo);
        List<CierrePosnetRequestDTO> cierres = validarCierresPosnet(cierresPosnet);
        if (entradasFisicasRestantes == null || entradasFisicasRestantes < 0) {
            throw new IllegalArgumentException("Indicá cuántas entradas quedan en el talonario");
        }
        List<Compra> compras = compraRepository.findAllByCajaId(caja.getId());
        BigDecimal montoContado = calcularMontoContado(conteo, cambioContado);
        BigDecimal dolaresContadoValidado = validarDolaresContado(compras, dolaresContado);
        BigDecimal montoEsperado = calcularMontoEsperado(caja, compras,
                retiroCajaRepository.findAllByCajaIdOrderByFechaAsc(caja.getId()),
                ajusteCajaRepository.findAllByCajaIdOrderByFechaAsc(caja.getId()));

        LocalDateTime ahora = LocalDateTime.now();
        caja.setFechaCierre(ahora);
        caja.setMontoContado(montoContado);
        caja.setMontoEsperado(montoEsperado);
        caja.setDiferencia(montoContado.subtract(montoEsperado));
        caja.setCambioContado(cambioContado == null ? BigDecimal.ZERO : cambioContado);
        caja.getConteoEfectivo().clear();
        caja.getConteoEfectivo().addAll(conteo);
        caja.setEntradasFisicasRestantes(entradasFisicasRestantes);
        caja.setDolaresContado(dolaresContadoValidado);

        Caja guardada = cajaRepository.save(caja);
        guardarCierresPosnet(guardada, cierres, ahora);

        return toDto(guardada);
    }

    /**
     * Inicial + efectivo real (impactoEfectivoArs) − retiros netos + ajustes manuales hacia/desde efectivo.
     */
    private BigDecimal calcularMontoEsperado(Caja caja) {
        return calcularMontoEsperado(caja,
                compraRepository.findAllByCajaId(caja.getId()),
                retiroCajaRepository.findAllByCajaIdOrderByFechaAsc(caja.getId()),
                ajusteCajaRepository.findAllByCajaIdOrderByFechaAsc(caja.getId()));
    }

    private BigDecimal calcularMontoEsperado(Caja caja, List<Compra> compras, List<RetiroCaja> retiros, List<AjusteCaja> ajustes) {
        BigDecimal impactoEfectivoArs = sumImpactoEfectivoArs(compras);
        BigDecimal totalRetiros = sumRetiros(retiros);
        BigDecimal ajusteEfectivo = sumAjustesNeto(ajustes, FormaPago.EFECTIVO_BOLETERIA);
        return caja.getMontoInicial().add(impactoEfectivoArs).subtract(totalRetiros).add(ajusteEfectivo);
    }

    /** Neto de los ajustes de una forma de pago: destino menos origen. */
    private BigDecimal sumAjustesNeto(Long cajaId, FormaPago forma) {
        return sumAjustesNeto(ajusteCajaRepository.findAllByCajaIdOrderByFechaAsc(cajaId), forma);
    }

    private BigDecimal sumAjustesNeto(List<AjusteCaja> ajustes, FormaPago forma) {
        BigDecimal neto = BigDecimal.ZERO;
        for (AjusteCaja a : ajustes) {
            if (a.getFormaDestino() == forma) neto = neto.add(a.getMonto());
            if (a.getFormaOrigen() == forma) neto = neto.subtract(a.getMonto());
        }
        return neto;
    }

    /** +1 si el ajuste agrega ventas, −1 si las quita, 0 si sólo las reubica o es un monto suelto. */
    private int signoAjuste(AjusteCaja a) {
        boolean origen = a.getFormaOrigen() != null;
        boolean destino = a.getFormaDestino() != null;
        if (destino && !origen) return 1;
        if (origen && !destino) return -1;
        return 0;
    }

    private Map<Long, TipoEntrada> tiposEntradaPorId() {
        return tipoEntradaService.getAll().stream()
                .collect(Collectors.toMap(TipoEntrada::getId, t -> t, (a, b) -> a));
    }

    /** Pases que suman o restan los ajustes, sólo de los tipos que pasan el filtro. */
    private int impactoAjustesEntradas(List<AjusteCaja> ajustes, Map<Long, TipoEntrada> tiposPorId,
                                        Predicate<TipoEntrada> filtro) {
        int total = 0;
        for (AjusteCaja a : ajustes) {
            int signo = signoAjuste(a);
            if (signo == 0) continue;
            int pasesPorVenta = 0;
            for (Map.Entry<Long, Integer> linea : a.getLineas().entrySet()) {
                TipoEntrada tipo = tiposPorId.get(linea.getKey());
                if (tipo != null && filtro.test(tipo)) pasesPorVenta += linea.getValue();
            }
            total += signo * a.getCantidadVentas() * pasesPorVenta;
        }
        return total;
    }

    /** Saca la caja de listados, KPIs, ranking y reporte. Irreversible desde la app. */
    @Transactional
    @Override
    public CajaResponseDTO deshabilitarCaja(Long cajaId) {
        Caja caja = cajaRepository.findById(cajaId)
                .orElseThrow(() -> new IllegalArgumentException("Caja no encontrada para id: " + cajaId));
        if (caja.getFechaCierre() == null) {
            throw new IllegalStateException("Sólo se puede deshabilitar una caja ya cerrada");
        }
        if (!caja.estaHabilitada()) {
            throw new IllegalStateException("Esta caja ya está deshabilitada");
        }
        caja.setHabilitada(false);
        // BaseEntity estampa usuarioModificacion/fechaModificacion (queda quién y cuándo).
        return toDto(cajaRepository.save(caja));
    }

    @Transactional
    @Override
    public CajaResponseDTO corregirCaja(Long cajaId, List<ConteoDenominacionDTO> conteoEfectivo,
                                         List<CierrePosnetRequestDTO> cierresPosnet, Integer entradasFisicasRestantes,
                                         BigDecimal cambioContado, BigDecimal dolaresContado,
                                         List<AjusteCajaRequestDTO> ajustes) {
        Caja caja = cajaRepository.findById(cajaId)
                .orElseThrow(() -> new IllegalArgumentException("Caja no encontrada para id: " + cajaId));
        if (caja.getFechaCierre() == null) {
            throw new IllegalStateException("Esta caja todavía está abierta: cerrala primero con /cerrar");
        }

        List<ConteoDenominacion> conteo = validarYMapearConteo(conteoEfectivo);
        List<CierrePosnetRequestDTO> cierres = validarCierresPosnet(cierresPosnet);
        if (entradasFisicasRestantes == null || entradasFisicasRestantes < 0) {
            throw new IllegalArgumentException("Indicá cuántas entradas quedan en el talonario");
        }
        BigDecimal contadoAntes = caja.getMontoContado();
        BigDecimal diferenciaAntes = caja.getDiferencia();
        Integer entradasAntes = caja.getEntradasFisicasRestantes();
        org.example.laserranitaentradas.monitoreo.AuditoriaContexto.referencia("de " + caja.getUsuario().getNombre()
                + " del " + caja.getFechaApertura().toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        if (ajustes != null && !ajustes.isEmpty()) {
            org.example.laserranitaentradas.monitoreo.AuditoriaContexto.detalle(ajustes.size() + " ajuste(s) entre formas de pago");
        }

        LocalDateTime ahora = LocalDateTime.now();

        // 1) Traspasos primero, así calcularMontoEsperado ya los ve.
        if (ajustes != null && !ajustes.isEmpty()) {
            for (AjusteCajaRequestDTO dto : ajustes) {
                validarAjuste(dto);
                ajusteCajaRepository.save(AjusteCaja.builder()
                        .caja(caja)
                        .formaOrigen(dto.getFormaOrigen())
                        .formaDestino(dto.getFormaDestino())
                        .monto(dto.getMonto())
                        .cantidadVentas(dto.getCantidadVentas() == null ? 0 : Math.max(0, dto.getCantidadVentas()))
                        .detalle(dto.getDetalle() == null || dto.getDetalle().isBlank() ? null : dto.getDetalle().trim())
                        .nota(dto.getNota() == null || dto.getNota().isBlank() ? null : dto.getNota().trim())
                        .fecha(ahora)
                        .comprasMovidas(dto.getComprasMovidas() == null ? new ArrayList<>() : new ArrayList<>(dto.getComprasMovidas()))
                        .lineas(dto.getLineas() == null ? new java.util.HashMap<>() : new java.util.HashMap<>(dto.getLineas()))
                        .build());
            }
            ajusteCajaRepository.flush();
        }

        // 2) Recuento del cierre.
        List<Compra> compras = compraRepository.findAllByCajaId(caja.getId());
        BigDecimal montoContado = calcularMontoContado(conteo, cambioContado);
        BigDecimal dolaresContadoValidado = validarDolaresContado(compras, dolaresContado);
        BigDecimal montoEsperado = calcularMontoEsperado(caja, compras,
                retiroCajaRepository.findAllByCajaIdOrderByFechaAsc(caja.getId()),
                ajusteCajaRepository.findAllByCajaIdOrderByFechaAsc(caja.getId()));

        caja.setMontoContado(montoContado);
        caja.setMontoEsperado(montoEsperado);
        caja.setDiferencia(montoContado.subtract(montoEsperado));
        caja.setCambioContado(cambioContado == null ? BigDecimal.ZERO : cambioContado);
        caja.getConteoEfectivo().clear();
        caja.getConteoEfectivo().addAll(conteo);
        caja.setEntradasFisicasRestantes(entradasFisicasRestantes);
        caja.setDolaresContado(dolaresContadoValidado);

        Caja guardada = cajaRepository.save(caja);
        cierrePosnetRepository.deleteAllByCajaId(caja.getId());
        guardarCierresPosnet(guardada, cierres, ahora);
        org.example.laserranitaentradas.monitoreo.AuditoriaContexto.cambio("Efectivo contado", contadoAntes, guardada.getMontoContado());
        org.example.laserranitaentradas.monitoreo.AuditoriaContexto.cambio("Diferencia", diferenciaAntes, guardada.getDiferencia());
        org.example.laserranitaentradas.monitoreo.AuditoriaContexto.cambio("Entradas que quedan", entradasAntes, guardada.getEntradasFisicasRestantes());

        return toDto(guardada);
    }

    @Transactional
    @Override
    public CajaResponseDTO eliminarAjuste(Long cajaId, Long ajusteId) {
        Caja caja = cajaRepository.findById(cajaId)
                .orElseThrow(() -> new IllegalArgumentException("Caja no encontrada para id: " + cajaId));
        AjusteCaja ajuste = ajusteCajaRepository.findByIdAndCajaId(ajusteId, cajaId)
                .orElseThrow(() -> new IllegalArgumentException("Ajuste no encontrado para id: " + ajusteId));
        ajusteCajaRepository.delete(ajuste);
        ajusteCajaRepository.flush();

        recalcularEfectivoTrasAjuste(caja);
        return toDto(cajaRepository.save(caja));
    }

    private void validarAjuste(AjusteCajaRequestDTO dto) {
        if (dto.getFormaOrigen() == null && dto.getFormaDestino() == null) {
            throw new IllegalArgumentException("Indicá al menos una forma de pago (de dónde sale y/o a dónde va el ajuste)");
        }
        if (dto.getFormaOrigen() != null && !FORMAS_PAGO_AJUSTABLES.contains(dto.getFormaOrigen())) {
            throw new IllegalArgumentException("El ajuste sólo admite Efectivo, Tarjeta o QR");
        }
        if (dto.getFormaDestino() != null && !FORMAS_PAGO_AJUSTABLES.contains(dto.getFormaDestino())) {
            throw new IllegalArgumentException("El ajuste sólo admite Efectivo, Tarjeta o QR");
        }
        if (dto.getFormaOrigen() != null && dto.getFormaOrigen() == dto.getFormaDestino()) {
            throw new IllegalArgumentException("El origen y el destino del ajuste no pueden ser la misma forma de pago");
        }
        if (dto.getMonto() == null || dto.getMonto().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto del ajuste tiene que ser mayor a cero");
        }
    }

    /**
     * Recalcula montoEsperado y diferencia de efectivo con los ajustes guardados. Lo de posnet se recalcula
     * siempre en toDto.
     */
    private void recalcularEfectivoTrasAjuste(Caja caja) {
        if (caja.getMontoContado() == null) return;
        BigDecimal montoEsperado = calcularMontoEsperado(caja);
        caja.setMontoEsperado(montoEsperado);
        caja.setDiferencia(caja.getMontoContado().subtract(montoEsperado));
    }

    @Transactional
    @Override
    public Caja reabrir(Long cajaId) {
        Caja caja = cajaRepository.findById(cajaId)
                .orElseThrow(() -> new IllegalArgumentException("Caja no encontrada para id: " + cajaId));
        if (caja.getFechaCierre() == null) {
            throw new IllegalStateException("Esta caja está abierta: no hace falta reabrirla, ya se puede reintentar directamente.");
        }
        if (!caja.estaHabilitada()) {
            throw new IllegalStateException("Esta caja está deshabilitada: no se puede reabrir.");
        }
        // El conteo queda guardado para que recerrarConElUltimoConteo lo reuse.
        caja.setFechaCierre(null);
        return cajaRepository.save(caja);
    }

    @Transactional
    @Override
    public CajaResponseDTO recerrarConElUltimoConteo(Long cajaId) {
        Caja caja = cajaRepository.findById(cajaId)
                .orElseThrow(() -> new IllegalArgumentException("Caja no encontrada para id: " + cajaId));
        if (caja.getFechaCierre() != null) {
            throw new IllegalStateException("Esta caja ya está cerrada");
        }
        List<ConteoDenominacionDTO> conteo = caja.getConteoEfectivo().stream()
                .map(c -> {
                    ConteoDenominacionDTO dto = new ConteoDenominacionDTO();
                    dto.setDenominacion(c.getDenominacion());
                    dto.setCantidad(c.getCantidad());
                    return dto;
                })
                .toList();
        List<CierrePosnetRequestDTO> posnet = cierrePosnetRepository.findAllByCajaIdOrderByIdAsc(cajaId).stream()
                .map(c -> {
                    CierrePosnetRequestDTO dto = new CierrePosnetRequestDTO();
                    dto.setFormaPago(c.getFormaPago());
                    dto.setMonto(c.getMonto());
                    dto.setNota(c.getNota());
                    return dto;
                })
                .toList();
        // Borrar los cierres de posnet viejos antes de volver a guardarlos, si no se duplican.
        cierrePosnetRepository.deleteAllByCajaId(cajaId);
        return cerrarCaja(caja, conteo, posnet, caja.getEntradasFisicasRestantes(), caja.getCambioContado(), caja.getDolaresContado());
    }

    @Override
    public CajaResponseDTO getDetalle(Long cajaId) {
        Caja caja = cajaRepository.findById(cajaId)
                .orElseThrow(() -> new IllegalArgumentException("Caja no encontrada para id: " + cajaId));
        return toDto(caja);
    }

    @Override
    public CajaDetalleAbiertaDTO getOperacionesCaja(Long cajaId) {
        Caja caja = cajaRepository.findById(cajaId)
                .orElseThrow(() -> new IllegalArgumentException("Caja no encontrada para id: " + cajaId));

        // Todo lo de la caja se trae una vez y se reparte a cada cálculo.
        List<Compra> compras = compraRepository.findAllByCajaId(cajaId);
        List<AjusteCaja> ajustes = ajusteCajaRepository.findAllByCajaIdOrderByFechaAsc(cajaId);
        List<RetiroCaja> retiros = retiroCajaRepository.findAllByCajaIdOrderByFechaAsc(cajaId);
        List<IngresoEntradas> ingresosEntradas = ingresoEntradasRepository.findAllByCajaIdOrderByFechaAsc(cajaId);
        List<Compra> anticipadasValidadas = compraRepository.findAnticipadasValidadasPorUsuarioYFecha(
                caja.getUsuario().getId(), caja.getFechaApertura(), LocalDateTime.now());
        int entradasAnticipadasEntregadas = calcularEntradasEntregadasAnticipadas(anticipadasValidadas);

        Integer entradasFisicasRestantes = null;
        if (caja.getEntradasFisicasInicial() != null) {
            int ingresosNetos = ingresosEntradas.stream()
                    .mapToInt(i -> i.getTipo() == TipoMovimientoEntradas.RETIRO ? -i.getCantidad() : i.getCantidad())
                    .sum();
            entradasFisicasRestantes = caja.getEntradasFisicasInicial() + ingresosNetos
                    - calcularEntradasEntregadas(compras, ajustes) - entradasAnticipadasEntregadas;
        }

        ResumenFacturacion facturacion = resumenFacturacion(cajaId);

        return CajaDetalleAbiertaDTO.builder()
                .operaciones(construirOperaciones(compras, retiros, ingresosEntradas, anticipadasValidadas))
                .totalVentasEfectivo(sumVentasPorFormaPago(compras, FormaPago.EFECTIVO_BOLETERIA))
                .totalVentasTarjeta(sumVentasPorFormaPago(compras, FormaPago.TARJETA))
                .totalVentasQr(sumVentasPorFormaPago(compras, FormaPago.MERCADO_PAGO_QR))
                .totalEntradasPagas(contarEntradasPagas(compras, ajustes) + contarUnidadesEntradaPaga(anticipadasValidadas))
                .entradasPagasAnticipadas(contarEntradasPagasAnticipadas(compras))
                .entradasPagasValidadas(contarUnidadesEntradaPaga(anticipadasValidadas))
                .entradasVendidasPorTipo(contarEntradasPorTipo(compras, ajustes, anticipadasValidadas))
                .huboVentaDolares(huboVentaDolares(compras))
                .entradasAnticipadasEntregadas(entradasAnticipadasEntregadas)
                .entradasFisicasRestantes(entradasFisicasRestantes)
                .totalFacturado(facturacion.totalFacturado())
                .facturasSinEmitir(facturacion.facturasSinEmitir())
                .montoSinEmitir(facturacion.montoSinEmitir())
                .build();
    }

    /**
     * "usuarioNombre" ordena por nombre y apellido; "totalRetiros" cae al default. Default: fechaApertura, el
     * día del turno.
     */
    private static Sort ordenCajasCerradas(String ordenarPor, String direccion) {
        Sort.Direction sentido = "ASC".equalsIgnoreCase(direccion) ? Sort.Direction.ASC : Sort.Direction.DESC;
        if ("usuarioNombre".equals(ordenarPor)) {
            return Sort.by(sentido, "usuario.nombre").and(Sort.by(sentido, "usuario.apellido"));
        }
        Set<String> ordenables = Set.of("fechaApertura", "fechaCierre", "montoInicial", "montoEsperado", "montoContado", "diferencia");
        String campo = ordenables.contains(ordenarPor) ? ordenarPor : "fechaApertura";
        return Sort.by(sentido, campo);
    }

    @Override
    public CajasCerradasResponseDTO getCajasCerradas(String usuarioNombre, String ordenarPor, String direccion,
                                                       int page, int size) {
        int tamanioPagina = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(Math.max(page, 0), tamanioPagina, ordenCajasCerradas(ordenarPor, direccion));

        Specification<Caja> spec = Specification.allOf(
                CajaSpecifications.cerrada(),
                CajaSpecifications.deUsuarioNombre(usuarioNombre),
                CajaSpecifications.habilitada()
        );
        Page<Caja> pagina = cajaRepository.findAll(spec, pageable);

        // Retiros y posnet sólo para las filas de la página.
        List<Caja> filas = pagina.getContent();
        Map<Long, BigDecimal> difPosnet = diferenciaPosnetPorCaja(filas.stream().map(Caja::getId).toList());

        List<CajaResumenReporteDTO> contenido = filas.stream()
                .map(caja -> new CajaResumenReporteDTO(
                        caja.getId(),
                        caja.getUsuario().getNombre() + " " + caja.getUsuario().getApellido(),
                        caja.getFechaApertura(),
                        caja.getFechaCierre(),
                        caja.getMontoInicial(),
                        sumRetiros(caja.getId()),
                        caja.getMontoEsperado(),
                        caja.getMontoContado(),
                        caja.getDiferencia(),
                        difPosnet.getOrDefault(caja.getId(), BigDecimal.ZERO),
                        caja.getControlOmitido()
                ))
                .toList();

        return CajasCerradasResponseDTO.builder()
                .content(contenido)
                .totalElements(pagina.getTotalElements())
                .totalPages(pagina.getTotalPages())
                .number(pagina.getNumber())
                .size(pagina.getSize())
                .build();
    }

    @Override
    public List<String> getBoleterosConCajasCerradas() {
        return cajaRepository.findNombresBoleterosConCajasCerradas();
    }

    @Override
    public List<CajaAbiertaDTO> getCajasAbiertas() {
        return cajaRepository.findAllByFechaCierreIsNullOrderByFechaAperturaAsc().stream()
                .map(caja -> {
                    // Acá sí se ve lo vendido: lo mira el admin, no el boletero. Los dólares ya entran como
                    // EFECTIVO_BOLETERIA.
                    List<Compra> compras = compraRepository.findAllByCajaId(caja.getId());
                    List<AjusteCaja> ajustes = ajusteCajaRepository.findAllByCajaIdOrderByFechaAsc(caja.getId());
                    BigDecimal totalVendido = sumVentasPorFormaPago(compras, FormaPago.EFECTIVO_BOLETERIA)
                            .add(sumVentasPorFormaPago(compras, FormaPago.TARJETA))
                            .add(sumVentasPorFormaPago(compras, FormaPago.MERCADO_PAGO_QR));
                    return CajaAbiertaDTO.builder()
                            .id(caja.getId())
                            .usuarioNombre(caja.getUsuario().getNombre() + " " + caja.getUsuario().getApellido())
                            .fechaApertura(caja.getFechaApertura())
                            .montoInicial(caja.getMontoInicial())
                            .totalVendido(totalVendido)
                            .totalEntradasPagas(contarEntradasPagas(compras, ajustes))
                            .personasIngresadas(contarEntradasTotales(compras, ajustes))
                            .controlOmitido(caja.getControlOmitido())
                            .build();
                })
                .toList();
    }

    @Override
    public List<Long> getIdsCajasAtrasadas() {
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime inicioDeHoy = ahora.toLocalDate().atStartOfDay();
        // Las sin control se cierran solas a las 00:05: hasta que pase ese margen no cuentan como atrasadas.
        boolean cierreAutomaticoPendiente = ahora.isBefore(inicioDeHoy.plus(MARGEN_CIERRE_AUTOMATICO));
        return cajaRepository.findAllByFechaCierreIsNullAndFechaAperturaBefore(inicioDeHoy).stream()
                .filter(c -> !(cierreAutomaticoPendiente && Boolean.TRUE.equals(c.getControlOmitido())))
                .map(Caja::getId)
                .toList();
    }

    /** Hasta qué hora se espera que el cierre automático de las 00:05 haya cerrado las cajas sin control. */
    private static final java.time.Duration MARGEN_CIERRE_AUTOMATICO = java.time.Duration.ofMinutes(30);

    private List<CierrePosnetRequestDTO> validarCierresPosnet(List<CierrePosnetRequestDTO> cierresPosnet) {
        List<CierrePosnetRequestDTO> cierres = cierresPosnet == null ? List.of() : cierresPosnet;
        boolean hayCombinados = cierres.stream().anyMatch(c -> c.getFormaPago() == null);
        boolean haySeparados = cierres.stream().anyMatch(c -> c.getFormaPago() != null);
        if (hayCombinados && haySeparados) {
            throw new IllegalArgumentException("No podés combinar cierres por separado y combinados en el mismo cierre");
        }
        for (CierrePosnetRequestDTO c : cierres) {
            if (c.getFormaPago() != null && !FORMAS_PAGO_POSNET.contains(c.getFormaPago())) {
                throw new IllegalArgumentException("El cierre de posnet sólo admite Tarjeta, QR, o combinado");
            }
            if (c.getMonto() == null || c.getMonto().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("El monto del cierre de posnet tiene que ser mayor a cero");
            }
        }
        return cierres;
    }

    private BigDecimal calcularMontoContado(List<ConteoDenominacion> conteo, BigDecimal cambioContado) {
        BigDecimal totalBilletes = conteo.stream()
                .map(c -> BigDecimal.valueOf(c.getDenominacion()).multiply(BigDecimal.valueOf(c.getCantidad())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return totalBilletes.add(cambioContado == null ? BigDecimal.ZERO : cambioContado);
    }

    private void guardarCierresPosnet(Caja caja, List<CierrePosnetRequestDTO> cierres, LocalDateTime fecha) {
        for (CierrePosnetRequestDTO c : cierres) {
            cierrePosnetRepository.save(CierrePosnet.builder()
                    .caja(caja)
                    .formaPago(c.getFormaPago())
                    .monto(c.getMonto())
                    .nota(c.getNota())
                    .fecha(fecha)
                    .build());
        }
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


    /** Con pago mixto, una compra aporta a la forma principal y a la secundaria (Compra.montoPorForma). */
    private BigDecimal sumVentasPorFormaPago(List<Compra> compras, FormaPago formaPago) {
        return compras.stream()
                .filter(c -> c.getEstado() != EstadoCompra.CANCELADO
                        && (c.getFormaPago() == formaPago || c.getFormaPagoSecundaria() == formaPago))
                .map(c -> c.montoPorForma(formaPago))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Ventas en efectivo pagadas en dólares (formaPago sigue siendo EFECTIVO_BOLETERIA), no canceladas. */
    private List<Compra> ventasEnDolares(List<Compra> compras) {
        return compras.stream()
                .filter(c -> c.getCotizacionDolar() != null && c.getEstado() != EstadoCompra.CANCELADO)
                .toList();
    }

    private boolean huboVentaDolares(List<Compra> compras) {
        return !ventasEnDolares(compras).isEmpty();
    }

    private BigDecimal sumDolaresEsperados(List<Compra> compras) {
        return ventasEnDolares(compras).stream()
                .map(Compra::getDolaresRecibidos)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Vuelto en pesos que hubo que darle al cliente en una venta puntual pagada en dólares. */
    private BigDecimal vueltoPesos(Compra compra) {
        return compra.getDolaresRecibidos().multiply(compra.getCotizacionDolar()).subtract(compra.getMontoTotal());
    }

    /**
     * Efecto en el cajón de pesos: en pesos entra la parte en efectivo; en dólares no entra nada en pesos
     * pero sale el vuelto, por eso se resta.
     */
    private BigDecimal impactoEfectivoArs(Compra compra) {
        if (compra.getCotizacionDolar() != null) return vueltoPesos(compra).negate();
        return compra.montoPorForma(FormaPago.EFECTIVO_BOLETERIA);
    }

    private BigDecimal sumImpactoEfectivoArs(List<Compra> compras) {
        return compras.stream()
                .filter(c -> c.getEstado() != EstadoCompra.CANCELADO
                        && (c.getFormaPago() == FormaPago.EFECTIVO_BOLETERIA || c.getFormaPagoSecundaria() == FormaPago.EFECTIVO_BOLETERIA))
                .map(this::impactoEfectivoArs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Null si no hubo ventas en dólares; si hubo, exige el conteo. */
    private BigDecimal validarDolaresContado(List<Compra> compras, BigDecimal dolaresContado) {
        if (!huboVentaDolares(compras)) return null;
        if (dolaresContado == null || dolaresContado.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Esta caja tuvo ventas en dólares: indicá cuántos dólares contaste");
        }
        return dolaresContado;
    }

    /** Neto: retiros suman, aportes restan (un aporte es, en la fórmula del esperado, un retiro negativo). */
    private BigDecimal sumRetiros(Long cajaId) {
        return sumRetiros(retiroCajaRepository.findAllByCajaIdOrderByFechaAsc(cajaId));
    }

    private BigDecimal sumRetiros(List<RetiroCaja> movimientos) {
        BigDecimal neto = BigDecimal.ZERO;
        for (RetiroCaja movimiento : movimientos) {
            neto = movimiento.getTipo() == TipoMovimientoCaja.APORTE
                    ? neto.subtract(movimiento.getMonto())
                    : neto.add(movimiento.getMonto());
        }
        return neto;
    }

    private BigDecimal sumCierres(List<CierrePosnet> cierres, FormaPago formaPago) {
        return cierres.stream()
                .filter(c -> c.getFormaPago() == formaPago)
                .map(CierrePosnet::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Formas de pago que van al "posnet" para la diferencia combinada de Tarjeta + QR. */
    private static final Set<FormaPago> FORMAS_POSNET_DIF = Set.of(FormaPago.TARJETA, FormaPago.MERCADO_PAGO_QR);

    @Override
    public Map<Long, BigDecimal> diferenciaPosnetPorCaja(List<Long> cajaIds) {
        if (cajaIds == null || cajaIds.isEmpty()) return Map.of();
        Map<Long, BigDecimal> cerrado = agruparPorCaja(cierrePosnetRepository.sumMontoPorCaja(cajaIds));
        Map<Long, BigDecimal> vendido = agruparPorCaja(
                compraRepository.sumMontoPorCajaYFormas(cajaIds, FORMAS_POSNET_DIF, EstadoCompra.CANCELADO));
        Map<Long, BigDecimal> ajusteNeto = agruparPorCaja(ajusteCajaRepository.netoPorCajaYFormas(cajaIds, FORMAS_POSNET_DIF));

        Map<Long, BigDecimal> resultado = new java.util.HashMap<>();
        for (Long id : cajaIds) {
            BigDecimal dif = cerrado.getOrDefault(id, BigDecimal.ZERO)
                    .subtract(vendido.getOrDefault(id, BigDecimal.ZERO))
                    .subtract(ajusteNeto.getOrDefault(id, BigDecimal.ZERO));
            resultado.put(id, dif);
        }
        return resultado;
    }

    /** Filas [idCaja, valor] de un GROUP BY a Map; el valor puede venir Long o BigDecimal. */
    private static Map<Long, BigDecimal> agruparPorCaja(List<Object[]> filas) {
        Map<Long, BigDecimal> m = new java.util.HashMap<>();
        for (Object[] fila : filas) {
            if (fila[0] == null) continue;
            Long id = ((Number) fila[0]).longValue();
            BigDecimal valor = fila[1] == null ? BigDecimal.ZERO : new BigDecimal(fila[1].toString());
            m.put(id, valor);
        }
        return m;
    }

    /**
     * Entradas del talonario entregadas según el sistema (tipos con entregaEntrada, sin canceladas) más los
     * ajustes.
     */
    private int calcularEntradasEntregadas(List<Compra> compras, List<AjusteCaja> ajustes) {
        int base = compras.stream()
                .filter(c -> c.getEstado() != EstadoCompra.CANCELADO)
                .flatMap(c -> c.getDetalles().stream())
                .filter(d -> d.getTipoEntrada() != null && Boolean.TRUE.equals(d.getTipoEntrada().getEntregaEntrada()))
                .mapToInt(CompraDetalle::getCantidad)
                .sum();
        return base + impactoAjustesEntradas(ajustes, tiposEntradaPorId(),
                t -> Boolean.TRUE.equals(t.getEntregaEntrada()));
    }

    /** Anticipadas validadas en el turno: no pasan por caja pero también consumen talonario. */
    private int calcularEntradasEntregadasAnticipadas(List<Compra> anticipadasValidadas) {
        return anticipadasValidadas.stream()
                .flatMap(c -> c.getDetalles().stream())
                .filter(d -> d.getTipoEntrada() != null && Boolean.TRUE.equals(d.getTipoEntrada().getEntregaEntrada()))
                .mapToInt(CompraDetalle::getCantidad)
                .sum();
    }

    /** Unidades de entradas con precio > 0, sin canceladas. */
    private int contarUnidadesEntradaPaga(List<Compra> compras) {
        return compras.stream()
                .filter(c -> c.getEstado() != EstadoCompra.CANCELADO)
                .flatMap(c -> c.getDetalles().stream())
                .filter(d -> d.getTipoEntrada() != null
                        && d.getTipoEntrada().getTipo() == Tipo.ENTRADA
                        && d.getTipoEntrada().getPrecio() != null
                        && d.getTipoEntrada().getPrecio().compareTo(BigDecimal.ZERO) > 0)
                .mapToInt(CompraDetalle::getCantidad)
                .sum();
    }

    /** Unidades de entradas con precio > 0, sin canceladas, más los ajustes. */
    private int contarEntradasPagas(List<Compra> compras, List<AjusteCaja> ajustes) {
        return contarUnidadesEntradaPaga(compras) + impactoAjustesEntradas(ajustes, tiposEntradaPorId(),
                t -> t.getTipo() == Tipo.ENTRADA && t.getPrecio() != null && t.getPrecio().compareTo(BigDecimal.ZERO) > 0);
    }

    /**
     * Las entradas pagas que son reservas cobradas en esta caja. Sin ajustes: esos quedan del lado de la
     * caja.
     */
    private int contarEntradasPagasAnticipadas(List<Compra> compras) {
        return compras.stream()
                .filter(c -> c.getEstado() == EstadoCompra.USADO)
                .flatMap(c -> c.getDetalles().stream())
                .filter(d -> d.getTipoEntrada() != null
                        && d.getTipoEntrada().getTipo() == Tipo.ENTRADA
                        && d.getTipoEntrada().getPrecio() != null
                        && d.getTipoEntrada().getPrecio().compareTo(BigDecimal.ZERO) > 0)
                .mapToInt(CompraDetalle::getCantidad)
                .sum();
    }

    /** Como contarEntradasPagas pero también las gratis: la gente que entró por la caja. */
    private int contarEntradasTotales(List<Compra> compras, List<AjusteCaja> ajustes) {
        int base = compras.stream()
                .filter(c -> c.getEstado() != EstadoCompra.CANCELADO)
                .flatMap(c -> c.getDetalles().stream())
                .filter(d -> d.getTipoEntrada() != null && d.getTipoEntrada().getTipo() == Tipo.ENTRADA)
                .mapToInt(CompraDetalle::getCantidad)
                .sum();
        return base + impactoAjustesEntradas(ajustes, tiposEntradaPorId(), t -> t.getTipo() == Tipo.ENTRADA);
    }

    /** Unidades por tipo de entrada con ajustes, incluidas las anticipadas validadas sin cobro. */
    private List<EntradasPorTipoDTO> contarEntradasPorTipo(List<Compra> compras, List<AjusteCaja> ajustes,
                                                            List<Compra> anticipadasValidadas) {
        Map<String, Integer> cantidadPorTipo = new LinkedHashMap<>();
        for (Compra compra : Stream.concat(compras.stream(), anticipadasValidadas.stream()).toList()) {
            if (compra.getEstado() == EstadoCompra.CANCELADO) continue;
            for (CompraDetalle detalle : compra.getDetalles()) {
                if (detalle.getTipoEntrada() != null && detalle.getTipoEntrada().getTipo() == Tipo.ENTRADA) {
                    cantidadPorTipo.merge(detalle.getTipoEntrada().getNombre(), detalle.getCantidad(), Integer::sum);
                }
            }
        }
        Map<Long, TipoEntrada> tiposPorId = tiposEntradaPorId();
        for (AjusteCaja a : ajustes) {
            int signo = signoAjuste(a);
            if (signo == 0) continue;
            for (Map.Entry<Long, Integer> linea : a.getLineas().entrySet()) {
                TipoEntrada tipo = tiposPorId.get(linea.getKey());
                if (tipo != null && tipo.getTipo() == Tipo.ENTRADA) {
                    cantidadPorTipo.merge(tipo.getNombre(), signo * a.getCantidadVentas() * linea.getValue(), Integer::sum);
                }
            }
        }
        return cantidadPorTipo.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .map(e -> EntradasPorTipoDTO.builder().nombreTipo(e.getKey()).cantidad(e.getValue()).build())
                .sorted(Comparator.comparing(EntradasPorTipoDTO::getCantidad, Comparator.reverseOrder()))
                .toList();
    }

    /**
     * Parte del monto que no es entrada: artículos varios y tipos EXTRA. segmentosEntrada lo resta antes de
     * repartir.
     */
    private BigDecimal montoArticulos(Compra compra) {
        return compra.getDetalles().stream()
                .map(this::montoLineaNoEntrada)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** El precio congelado de la línea, o el actual del tipo en compras viejas. */
    private BigDecimal precioListaDe(CompraDetalle d) {
        return d.getPrecioUnitario() != null ? d.getPrecioUnitario() : d.getTipoEntrada().getPrecio();
    }

    private BigDecimal montoLineaNoEntrada(CompraDetalle d) {
        if (d.getTipoEntrada() != null) {
            if (d.getTipoEntrada().getTipo() != Tipo.EXTRA || d.getTipoEntrada().getPrecio() == null) {
                return BigDecimal.ZERO;
            }
            return precioListaDe(d).multiply(BigDecimal.valueOf(d.getCantidad()));
        }
        if (d.getPrecioUnitario() == null) return BigDecimal.ZERO;
        return d.getPrecioUnitario().multiply(BigDecimal.valueOf(d.getCantidad()));
    }

    /**
     * Un segmento por línea de entrada paga, con el monto repartido proporcional al precio de lista; la
     * última absorbe el redondeo.
     */
    private List<SegmentoEntradaDTO> segmentosEntrada(Compra compra) {
        List<CompraDetalle> lineas = compra.getDetalles().stream()
                .filter(d -> d.getTipoEntrada() != null
                        && d.getTipoEntrada().getTipo() == Tipo.ENTRADA
                        && d.getTipoEntrada().getPrecio() != null
                        && d.getTipoEntrada().getPrecio().compareTo(BigDecimal.ZERO) > 0)
                .toList();
        if (lineas.isEmpty()) return List.of();

        BigDecimal montoEntradas = compra.getMontoTotal().subtract(montoArticulos(compra));
        BigDecimal sumaLista = lineas.stream()
                .map(d -> precioListaDe(d).multiply(BigDecimal.valueOf(d.getCantidad())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Descuento igual para todos los segmentos: el % de la promo, o el monto si fue fijo o manual.
        BigDecimal descuentoPorcentaje = compra.getPromocion() != null
                ? compra.getPromocion().getPorcentajeDescuento() : null;
        BigDecimal descuentoMonto = null;
        if (descuentoPorcentaje == null
                && compra.getDescuentoAplicado() != null
                && compra.getDescuentoAplicado().signum() > 0) {
            descuentoMonto = compra.getDescuentoAplicado();
        }

        List<SegmentoEntradaDTO> out = new ArrayList<>();
        BigDecimal acumulado = BigDecimal.ZERO;
        for (int i = 0; i < lineas.size(); i++) {
            CompraDetalle d = lineas.get(i);
            BigDecimal monto;
            if (i == lineas.size() - 1) {
                monto = montoEntradas.subtract(acumulado);
            } else if (sumaLista.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal lista = precioListaDe(d).multiply(BigDecimal.valueOf(d.getCantidad()));
                monto = montoEntradas.multiply(lista).divide(sumaLista, 2, java.math.RoundingMode.HALF_UP);
                acumulado = acumulado.add(monto);
            } else {
                monto = BigDecimal.ZERO;
            }
            out.add(SegmentoEntradaDTO.builder()
                    .tipoEntradaId(d.getTipoEntrada().getId())
                    .tipoNombre(d.getTipoEntrada().getNombre())
                    .cantidad(d.getCantidad())
                    .monto(monto)
                    .descuentoPorcentaje(descuentoPorcentaje)
                    .descuentoMonto(descuentoMonto)
                    .build());
        }
        return out;
    }

    /** Nombre a mostrar para una línea de compra: entrada, artículo de catálogo, o descripción libre. */
    private String nombreDetalle(CompraDetalle d) {
        String nombre;
        if (d.getTipoEntrada() != null) {
            nombre = d.getTipoEntrada().getNombre();
        } else if (d.getArticuloVario() != null) {
            nombre = d.getArticuloVario().getNombre();
        } else {
            nombre = d.getDescripcionLibre() != null ? d.getDescripcionLibre() : "?";
        }
        return d.getCantidad() + "x " + nombre;
    }

    private List<OperacionCajaDTO> construirOperaciones(List<Compra> compras, List<RetiroCaja> movimientos,
                                                          List<IngresoEntradas> ingresos, List<Compra> anticipadasValidadas) {
        List<OperacionCajaDTO> operaciones = new ArrayList<>();

        for (Compra compra : compras) {
            if (compra.getEstado() == EstadoCompra.CANCELADO) continue;
            String detalle = compra.getDetalles().stream()
                    .map(this::nombreDetalle)
                    .collect(Collectors.joining(", "));
            operaciones.add(OperacionCajaDTO.builder()
                    .tipo("VENTA")
                    .fecha(compra.getFechaValidacion() != null ? compra.getFechaValidacion() : compra.getFechaCreacion())
                    .monto(compra.getMontoTotal())
                    .montoArticulos(montoArticulos(compra))
                    .segmentosEntrada(segmentosEntrada(compra))
                    .formaPago(compra.getFormaPago())
                    .pagoEnDolares(compra.getCotizacionDolar() != null)
                    .detalle(detalle)
                    .compraId(compra.getId())
                    // Acá sólo llega USADO desde una reserva cobrada en esta caja; una venta de puerta es
                    // VENDIDO_EN_PUERTA.
                    .anticipada(compra.getEstado() == EstadoCompra.USADO)
                    .build());
        }

        // Las anticipadas validadas no cobraron acá: van aparte y con compraId null para que no se editen
        // desde la caja.
        for (Compra anticipada : anticipadasValidadas) {
            String detalle = anticipada.getDetalles().stream()
                    .map(this::nombreDetalle)
                    .collect(Collectors.joining(", "));
            operaciones.add(OperacionCajaDTO.builder()
                    .tipo("ANTICIPADA_VALIDADA")
                    .fecha(anticipada.getFechaValidacion())
                    .monto(anticipada.getMontoTotal())
                    .montoArticulos(montoArticulos(anticipada))
                    .segmentosEntrada(segmentosEntrada(anticipada))
                    .formaPago(anticipada.getFormaPago())
                    .detalle(detalle)
                    .build());
        }

        for (RetiroCaja movimiento : movimientos) {
            operaciones.add(OperacionCajaDTO.builder()
                    .tipo(movimiento.getTipo() == TipoMovimientoCaja.APORTE ? "APORTE" : "RETIRO")
                    .fecha(movimiento.getFecha())
                    .monto(movimiento.getMonto())
                    .detalle(movimiento.getMotivo())
                    .build());
        }

        for (IngresoEntradas ingreso : ingresos) {
            boolean esRetiro = ingreso.getTipo() == TipoMovimientoEntradas.RETIRO;
            String detalle = (esRetiro ? "-" : "+") + ingreso.getCantidad() + " entradas"
                    + (ingreso.getMotivo() != null && !ingreso.getMotivo().isBlank() ? " — " + ingreso.getMotivo() : "");
            operaciones.add(OperacionCajaDTO.builder()
                    .tipo(esRetiro ? "RETIRO_ENTRADAS" : "INGRESO_ENTRADAS")
                    .fecha(ingreso.getFecha())
                    .monto(null)
                    .detalle(detalle)
                    .build());
        }

        operaciones.sort(Comparator.comparing(OperacionCajaDTO::getFecha, Comparator.nullsLast(Comparator.naturalOrder())));
        return operaciones;
    }

    private AjusteCajaResponseDTO toAjusteDto(AjusteCaja a) {
        return AjusteCajaResponseDTO.builder()
                .id(a.getId())
                .formaOrigen(a.getFormaOrigen())
                .formaDestino(a.getFormaDestino())
                .monto(a.getMonto())
                .cantidadVentas(a.getCantidadVentas())
                .detalle(a.getDetalle())
                .nota(a.getNota())
                .fecha(a.getFecha())
                .usuario(a.getUsuarioCreacion())
                .lineas(new java.util.HashMap<>(a.getLineas()))
                .build();
    }

    /** Facturado de una caja y lo que todavía espera CAE (ver resumenFacturacion). */
    private record ResumenFacturacion(BigDecimal totalFacturado, int facturasSinEmitir, BigDecimal montoSinEmitir) {}

    /** Facturado = B con CAE − notas de crédito con CAE. Las que esperan CAE o tienen error van aparte. */
    private ResumenFacturacion resumenFacturacion(Long cajaId) {
        BigDecimal totalFacturado = BigDecimal.ZERO;
        int facturasSinEmitir = 0;
        BigDecimal montoSinEmitir = BigDecimal.ZERO;
        for (Object[] fila : facturaRepository.totalesPorCaja(cajaId)) {
            int tipo = ((Number) fila[0]).intValue();
            EstadoFactura estado = (EstadoFactura) fila[1];
            int cantidad = ((Number) fila[2]).intValue();
            BigDecimal suma = new BigDecimal(fila[3].toString());
            if (estado == EstadoFactura.EMITIDA) {
                if (tipo == WsfeService.CBTE_TIPO_FACTURA_B) totalFacturado = totalFacturado.add(suma);
                else if (tipo == WsfeService.CBTE_TIPO_NOTA_CREDITO_B) totalFacturado = totalFacturado.subtract(suma);
            } else if (tipo == WsfeService.CBTE_TIPO_FACTURA_B
                    && (estado == EstadoFactura.PENDIENTE || estado == EstadoFactura.ERROR)) {
                facturasSinEmitir += cantidad;
                montoSinEmitir = montoSinEmitir.add(suma);
            }
        }
        return new ResumenFacturacion(totalFacturado, facturasSinEmitir, montoSinEmitir);
    }

    private CajaResponseDTO toDto(Caja caja) {
        // Con la caja abierta no se muestran los esperados, si no el boletero podría calcar el cierre.
        boolean cerrada = caja.getFechaCierre() != null;

        List<Compra> compras = compraRepository.findAllByCajaId(caja.getId());
        List<RetiroCaja> movimientos = retiroCajaRepository.findAllByCajaIdOrderByFechaAsc(caja.getId());
        List<IngresoEntradas> ingresosMovimientos = ingresoEntradasRepository.findAllByCajaIdOrderByFechaAsc(caja.getId());
        List<AjusteCaja> ajustes = ajusteCajaRepository.findAllByCajaIdOrderByFechaAsc(caja.getId());

        BigDecimal totalRetiros = sumRetiros(movimientos);

        BigDecimal totalVentasEfectivo = null;
        BigDecimal efectivoEsperado = null;
        BigDecimal totalVentasTarjeta = null;
        BigDecimal totalVentasQr = null;
        BigDecimal totalCerradoTarjeta = null;
        BigDecimal totalCerradoQr = null;
        BigDecimal diferenciaTarjeta = null;
        BigDecimal diferenciaQr = null;
        BigDecimal totalVentasPosnet = null;
        BigDecimal totalCerradoPosnet = null;
        BigDecimal diferenciaPosnet = null;
        List<CierrePosnetResponseDTO> cierresDto = List.of();
        List<AjusteCajaResponseDTO> ajustesDto = List.of();
        Integer entradasFisicasEsperadas = null;
        Integer diferenciaEntradas = null;
        Integer entradasAnticipadasEntregadas = null;
        List<OperacionCajaDTO> operaciones = null;
        Integer totalEntradasPagas = null;
        Integer entradasPagasAnticipadas = null;
        Integer entradasPagasValidadas = null;
        List<EntradasPorTipoDTO> entradasVendidasPorTipo = null;
        BigDecimal dolaresEsperado = null;
        BigDecimal diferenciaDolares = null;
        BigDecimal totalFacturado = null;
        Integer facturasSinEmitir = null;
        BigDecimal montoSinEmitir = null;

        // Es un booleano: se puede mostrar con la caja abierta.
        boolean huboVentaDolares = huboVentaDolares(compras);

        if (cerrada) {
            // totalVentasEfectivo es el precio de venta; efectivoEsperado son los pesos que tiene que haber
            // en el cajón (difieren si hubo dólares). Los ajustes manuales se aplican como neto por forma de
            // pago, sin cambiar el total.
            totalVentasEfectivo = sumVentasPorFormaPago(compras, FormaPago.EFECTIVO_BOLETERIA)
                    .add(sumAjustesNeto(ajustes, FormaPago.EFECTIVO_BOLETERIA));
            efectivoEsperado = calcularMontoEsperado(caja, compras, movimientos, ajustes);

            List<CierrePosnet> cierres = cierrePosnetRepository.findAllByCajaIdOrderByIdAsc(caja.getId());
            boolean combinado = cierres.stream().anyMatch(c -> c.getFormaPago() == null);

            // Tarjeta y QR siempre por separado, aunque el cierre del posnet sea combinado.
            totalVentasTarjeta = sumVentasPorFormaPago(compras, FormaPago.TARJETA)
                    .add(sumAjustesNeto(ajustes, FormaPago.TARJETA));
            totalVentasQr = sumVentasPorFormaPago(compras, FormaPago.MERCADO_PAGO_QR)
                    .add(sumAjustesNeto(ajustes, FormaPago.MERCADO_PAGO_QR));
            if (combinado) {
                // Cierre combinado: se compara Tarjeta + QR junto.
                totalVentasPosnet = totalVentasTarjeta.add(totalVentasQr);
                totalCerradoPosnet = sumCierres(cierres, null);
                diferenciaPosnet = totalCerradoPosnet.subtract(totalVentasPosnet);
            } else {
                totalCerradoTarjeta = sumCierres(cierres, FormaPago.TARJETA);
                totalCerradoQr = sumCierres(cierres, FormaPago.MERCADO_PAGO_QR);
                diferenciaTarjeta = totalCerradoTarjeta.subtract(totalVentasTarjeta);
                diferenciaQr = totalCerradoQr.subtract(totalVentasQr);
            }
            ajustesDto = ajustes.stream().map(this::toAjusteDto).toList();
            cierresDto = cierres.stream()
                    .map(c -> CierrePosnetResponseDTO.builder()
                            .id(c.getId())
                            .formaPago(c.getFormaPago())
                            .monto(c.getMonto())
                            .nota(c.getNota())
                            .fecha(c.getFecha())
                            .build())
                    .toList();

            // Talonario esperado = inicial + ingresos − entregadas. Null si la caja no tiene inicial.
            List<Compra> anticipadasValidadas = compraRepository.findAnticipadasValidadasPorUsuarioYFecha(
                    caja.getUsuario().getId(), caja.getFechaApertura(), caja.getFechaCierre());
            entradasAnticipadasEntregadas = calcularEntradasEntregadasAnticipadas(anticipadasValidadas);

            if (caja.getEntradasFisicasInicial() != null) {
                int ingresosNetos = ingresosMovimientos.stream()
                        .mapToInt(i -> i.getTipo() == TipoMovimientoEntradas.RETIRO ? -i.getCantidad() : i.getCantidad())
                        .sum();
                entradasFisicasEsperadas = caja.getEntradasFisicasInicial() + ingresosNetos
                        - calcularEntradasEntregadas(compras, ajustes) - entradasAnticipadasEntregadas;
                if (caja.getEntradasFisicasRestantes() != null) {
                    // restantes − esperadas: negativo = faltan, positivo = sobran.
                    diferenciaEntradas = caja.getEntradasFisicasRestantes() - entradasFisicasEsperadas;
                }
            }

            operaciones = construirOperaciones(compras, movimientos, ingresosMovimientos, anticipadasValidadas);

            entradasVendidasPorTipo = contarEntradasPorTipo(compras, ajustes, anticipadasValidadas);
            totalEntradasPagas = contarEntradasPagas(compras, ajustes) + contarUnidadesEntradaPaga(anticipadasValidadas);
            entradasPagasAnticipadas = contarEntradasPagasAnticipadas(compras);
            entradasPagasValidadas = contarUnidadesEntradaPaga(anticipadasValidadas);

            if (huboVentaDolares) {
                dolaresEsperado = sumDolaresEsperados(compras);
                if (caja.getDolaresContado() != null) {
                    diferenciaDolares = caja.getDolaresContado().subtract(dolaresEsperado);
                }
            }

            ResumenFacturacion facturacion = resumenFacturacion(caja.getId());
            totalFacturado = facturacion.totalFacturado();
            facturasSinEmitir = facturacion.facturasSinEmitir();
            montoSinEmitir = facturacion.montoSinEmitir();
        }

        List<RetiroCajaResponseDTO> retiros = movimientos.stream()
                .map(r -> RetiroCajaResponseDTO.builder()
                        .id(r.getId())
                        .monto(r.getMonto())
                        .motivo(r.getMotivo())
                        .tipo(r.getTipo())
                        .fecha(r.getFecha())
                        .build())
                .toList();

        List<IngresoEntradasResponseDTO> ingresosEntradas = ingresosMovimientos.stream()
                .map(i -> IngresoEntradasResponseDTO.builder()
                        .id(i.getId())
                        .cantidad(i.getCantidad())
                        .motivo(i.getMotivo())
                        .tipo(i.getTipo())
                        .fecha(i.getFecha())
                        .build())
                .toList();
        // Neto: INGRESO suma, RETIRO resta (mismo criterio que totalRetiros con RETIRO/APORTE de efectivo).
        int totalIngresosEntradas = ingresosEntradas.stream()
                .mapToInt(i -> i.getTipo() == TipoMovimientoEntradas.RETIRO ? -i.getCantidad() : i.getCantidad())
                .sum();

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
                .cambioContado(caja.getCambioContado())
                .retiros(retiros)
                .conteoEfectivo(conteoDto)
                .totalVentasTarjeta(totalVentasTarjeta)
                .totalVentasQr(totalVentasQr)
                .totalCerradoTarjeta(totalCerradoTarjeta)
                .totalCerradoQr(totalCerradoQr)
                .diferenciaTarjeta(diferenciaTarjeta)
                .diferenciaQr(diferenciaQr)
                .totalVentasPosnet(totalVentasPosnet)
                .totalCerradoPosnet(totalCerradoPosnet)
                .diferenciaPosnet(diferenciaPosnet)
                .cierresPosnet(cierresDto)
                .ajustes(ajustesDto)
                .entradasFisicasInicial(caja.getEntradasFisicasInicial())
                .entradasFisicasRestantes(caja.getEntradasFisicasRestantes())
                .entradasFisicasEsperadas(entradasFisicasEsperadas)
                .diferenciaEntradas(diferenciaEntradas)
                .entradasAnticipadasEntregadas(entradasAnticipadasEntregadas)
                .totalIngresosEntradas(totalIngresosEntradas)
                .ingresosEntradas(ingresosEntradas)
                .operaciones(operaciones)
                .totalEntradasPagas(totalEntradasPagas)
                .entradasPagasAnticipadas(entradasPagasAnticipadas)
                .entradasPagasValidadas(entradasPagasValidadas)
                .entradasVendidasPorTipo(entradasVendidasPorTipo)
                .huboVentaDolares(huboVentaDolares)
                .dolaresEsperado(dolaresEsperado)
                .dolaresContado(caja.getDolaresContado())
                .diferenciaDolares(diferenciaDolares)
                .habilitada(caja.estaHabilitada())
                .controlOmitido(caja.getControlOmitido())
                .totalFacturado(totalFacturado)
                .facturasSinEmitir(facturasSinEmitir)
                .montoSinEmitir(montoSinEmitir)
                .build();
    }
}
