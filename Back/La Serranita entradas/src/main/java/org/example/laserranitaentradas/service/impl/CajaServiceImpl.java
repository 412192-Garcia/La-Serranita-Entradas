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
import org.example.laserranitaentradas.repository.IngresoEntradasRepository;
import org.example.laserranitaentradas.repository.RetiroCajaRepository;
import org.example.laserranitaentradas.service.CajaService;
import org.example.laserranitaentradas.service.TipoEntradaService;
import org.example.laserranitaentradas.service.UsuarioService;
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
    private final TipoEntradaService tipoEntradaService;
    private final UsuarioService usuarioService;

    public CajaServiceImpl(CajaRepository cajaRepository,
                            RetiroCajaRepository retiroCajaRepository,
                            CierrePosnetRepository cierrePosnetRepository,
                            IngresoEntradasRepository ingresoEntradasRepository,
                            CompraRepository compraRepository,
                            AjusteCajaRepository ajusteCajaRepository,
                            TipoEntradaService tipoEntradaService,
                            UsuarioService usuarioService) {
        this.cajaRepository = cajaRepository;
        this.retiroCajaRepository = retiroCajaRepository;
        this.cierrePosnetRepository = cierrePosnetRepository;
        this.ingresoEntradasRepository = ingresoEntradasRepository;
        this.compraRepository = compraRepository;
        this.ajusteCajaRepository = ajusteCajaRepository;
        this.tipoEntradaService = tipoEntradaService;
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
    public CajaResponseDTO registrarRetiro(Long usuarioId, BigDecimal monto, String motivo, TipoMovimientoCaja tipo,
                                            String idempotencyKey, LocalDateTime fechaOriginal) {
        // Antes que nada: si esta misma operación ya se procesó (reintento de algo cuya respuesta
        // se perdió en un corte), devolver lo guardado en vez de crear un movimiento duplicado.
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

    /** Guarda un ingreso o retiro de entradas físicas sobre una caja y cantidad ya validadas (propia o ajena vía admin). */
    private void guardarIngresoEntradas(Caja caja, Integer cantidad, String motivo, TipoMovimientoEntradas tipo,
                                         String idempotencyKey, LocalDateTime fechaOriginal) {
        TipoMovimientoEntradas tipoFinal = tipo == null ? TipoMovimientoEntradas.INGRESO : tipo;

        // Un retiro puede dejar el conteo en negativo (ej. el inicial estaba mal contado, o
        // entran entradas por otro lado que no pasan por acá): el frontend ya avisa y pide
        // confirmación antes de mandar esto (ver stockActual en el modal), así que del lado del
        // servidor no hace falta bloquearlo — sólo estorbaría a un caso legítimo.
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
                                   List<CierrePosnetRequestDTO> cierresPosnet, Integer entradasFisicasCortadas,
                                   BigDecimal cambioContado, BigDecimal dolaresContado) {
        Caja caja = getAbiertaOrThrow(usuarioId);
        return cerrarCaja(caja, conteoEfectivo, cierresPosnet, entradasFisicasCortadas, cambioContado, dolaresContado);
    }

    @Transactional
    @Override
    public CajaResponseDTO cerrarComoAdmin(Long cajaId, List<ConteoDenominacionDTO> conteoEfectivo,
                                            List<CierrePosnetRequestDTO> cierresPosnet, Integer entradasFisicasCortadas,
                                            BigDecimal cambioContado, BigDecimal dolaresContado) {
        Caja caja = cajaRepository.findById(cajaId)
                .orElseThrow(() -> new IllegalArgumentException("Caja no encontrada para id: " + cajaId));
        if (caja.getFechaCierre() != null) {
            throw new IllegalStateException("Esta caja ya está cerrada");
        }
        return cerrarCaja(caja, conteoEfectivo, cierresPosnet, entradasFisicasCortadas, cambioContado, dolaresContado);
    }

    /** El cálculo real de cerrar, sobre una caja ya resuelta (propia o, vía admin, ajena). */
    private CajaResponseDTO cerrarCaja(Caja caja, List<ConteoDenominacionDTO> conteoEfectivo,
                                        List<CierrePosnetRequestDTO> cierresPosnet, Integer entradasFisicasCortadas,
                                        BigDecimal cambioContado, BigDecimal dolaresContado) {
        List<ConteoDenominacion> conteo = validarYMapearConteo(conteoEfectivo);
        List<CierrePosnetRequestDTO> cierres = validarCierresPosnet(cierresPosnet);
        if (entradasFisicasCortadas == null || entradasFisicasCortadas < 0) {
            throw new IllegalArgumentException("Indicá cuántas entradas cortaste del talonario");
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
        caja.setEntradasFisicasCortadas(entradasFisicasCortadas);
        caja.setDolaresContado(dolaresContadoValidado);

        Caja guardada = cajaRepository.save(caja);
        guardarCierresPosnet(guardada, cierres, ahora);

        return toDto(guardada);
    }

    /**
     * Inicial + impacto real de efectivo (ver impactoEfectivoArs) − retiros netos (ver
     * sumRetiros) + el neto de los ajustes manuales que traspasaron monto hacia/desde efectivo
     * (ver registrarAjustes).
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

    /** Neto de los ajustes manuales para una forma de pago: lo que entró (destino) menos lo que salió (origen). */
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

    /**
     * +1 si el ajuste AGREGA ventas (sólo destino), −1 si las QUITA (sólo origen), 0 si sólo
     * las reubica entre formas (mismas ventas) o es un monto suelto. Se usa para saber cuánto
     * cambia el conteo de entradas / uso del talonario.
     */
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

    /**
     * Cuántos pases suman (o restan) los ajustes manuales a un conteo de entradas de esta caja,
     * contando sólo los tipos que pasan el filtro (ej. los que entregan entrada física, o los
     * que tienen precio > 0).
     */
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

    /**
     * Deshabilita una caja ya cerrada: la saca de todos los listados/KPIs de Cajas, del ranking
     * y del reporte, y hace que sus ventas dejen de sumar (ver ReporteServiceImpl). Irreversible
     * desde la app. El detalle por id la sigue devolviendo, con habilitada=false.
     */
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
    public CajaResponseDTO corregirCierre(Long cajaId, List<ConteoDenominacionDTO> conteoEfectivo,
                                           List<CierrePosnetRequestDTO> cierresPosnet, Integer entradasFisicasCortadas,
                                           BigDecimal cambioContado, BigDecimal dolaresContado) {
        Caja caja = cajaRepository.findById(cajaId)
                .orElseThrow(() -> new IllegalArgumentException("Caja no encontrada para id: " + cajaId));
        if (caja.getFechaCierre() == null) {
            throw new IllegalStateException("Esta caja todavía está abierta: cerrala primero con /cerrar");
        }

        List<ConteoDenominacion> conteo = validarYMapearConteo(conteoEfectivo);
        List<CierrePosnetRequestDTO> cierres = validarCierresPosnet(cierresPosnet);
        if (entradasFisicasCortadas == null || entradasFisicasCortadas < 0) {
            throw new IllegalArgumentException("Indicá cuántas entradas cortaste del talonario");
        }
        List<Compra> compras = compraRepository.findAllByCajaId(caja.getId());
        BigDecimal montoContado = calcularMontoContado(conteo, cambioContado);
        BigDecimal dolaresContadoValidado = validarDolaresContado(compras, dolaresContado);

        // El esperado se recalcula igual que al cerrar (no debería haber cambiado, pero
        // recalcularlo en vez de reusar el guardado evita que quede desactualizado si algo
        // sí cambió entre medio).
        BigDecimal montoEsperado = calcularMontoEsperado(caja, compras,
                retiroCajaRepository.findAllByCajaIdOrderByFechaAsc(caja.getId()),
                ajusteCajaRepository.findAllByCajaIdOrderByFechaAsc(caja.getId()));

        caja.setMontoContado(montoContado);
        caja.setMontoEsperado(montoEsperado);
        caja.setDiferencia(montoContado.subtract(montoEsperado));
        caja.setCambioContado(cambioContado == null ? BigDecimal.ZERO : cambioContado);
        caja.getConteoEfectivo().clear();
        caja.getConteoEfectivo().addAll(conteo);
        caja.setEntradasFisicasCortadas(entradasFisicasCortadas);
        caja.setDolaresContado(dolaresContadoValidado);

        Caja guardada = cajaRepository.save(caja);
        cierrePosnetRepository.deleteAllByCajaId(caja.getId());
        guardarCierresPosnet(guardada, cierres, LocalDateTime.now());

        return toDto(guardada);
    }

    @Transactional
    @Override
    public CajaResponseDTO registrarAjustes(Long cajaId, List<AjusteCajaRequestDTO> ajustes) {
        Caja caja = cajaRepository.findById(cajaId)
                .orElseThrow(() -> new IllegalArgumentException("Caja no encontrada para id: " + cajaId));
        if (caja.getFechaCierre() == null) {
            throw new IllegalStateException("Esta caja todavía está abierta: los ajustes se cargan sobre un cierre ya hecho");
        }
        if (ajustes == null || ajustes.isEmpty()) {
            throw new IllegalArgumentException("No se mandó ningún ajuste");
        }

        LocalDateTime ahora = LocalDateTime.now();
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

        recalcularEfectivoTrasAjuste(caja);
        return toDto(cajaRepository.save(caja));
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
     * Recalcula y persiste montoEsperado/diferencia de efectivo con los ajustes ya guardados,
     * para que el listado de cajas cerradas, el ranking y sumFaltantes/sobrantes queden al día
     * (mismo criterio que corregirCierre). Los esperados/diferencias de posnet no se guardan en
     * la Caja — se recalculan siempre en toDto.
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
        // El conteo (denominaciones, posnet, entradas cortadas, cambio, dólares) queda tal cual
        // estaba en la caja — no se toca acá — así que recerrarConElUltimoConteo lo puede releer
        // después sin que nadie tenga que volver a cargarlo.
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
        // cerrarCaja vuelve a guardar los cierres de posnet tal cual se le pasen: sin borrar
        // los viejos primero quedarían duplicados (mismo criterio que corregirCierre).
        cierrePosnetRepository.deleteAllByCajaId(cajaId);
        return cerrarCaja(caja, conteo, posnet, caja.getEntradasFisicasCortadas(), caja.getCambioContado(), caja.getDolaresContado());
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

        // Todo lo de esta caja se trae una sola vez y se reparte a cada cálculo (antes cada
        // helper repetía su propio findAllByCajaId).
        List<Compra> compras = compraRepository.findAllByCajaId(cajaId);
        List<AjusteCaja> ajustes = ajusteCajaRepository.findAllByCajaIdOrderByFechaAsc(cajaId);
        List<RetiroCaja> retiros = retiroCajaRepository.findAllByCajaIdOrderByFechaAsc(cajaId);
        List<IngresoEntradas> ingresosEntradas = ingresoEntradasRepository.findAllByCajaIdOrderByFechaAsc(cajaId);

        Integer entradasFisicasRestantes = null;
        if (caja.getEntradasFisicasInicial() != null) {
            int ingresosNetos = ingresosEntradas.stream()
                    .mapToInt(i -> i.getTipo() == TipoMovimientoEntradas.RETIRO ? -i.getCantidad() : i.getCantidad())
                    .sum();
            entradasFisicasRestantes = caja.getEntradasFisicasInicial() + ingresosNetos
                    - calcularEntradasEsperadas(compras, ajustes);
        }

        return CajaDetalleAbiertaDTO.builder()
                .operaciones(construirOperaciones(compras, retiros, ingresosEntradas))
                .totalVentasEfectivo(sumVentasPorFormaPago(compras, FormaPago.EFECTIVO_BOLETERIA))
                .totalVentasTarjeta(sumVentasPorFormaPago(compras, FormaPago.TARJETA))
                .totalVentasQr(sumVentasPorFormaPago(compras, FormaPago.MERCADO_PAGO_QR))
                .totalEntradasPagas(contarEntradasPagas(compras, ajustes))
                .entradasVendidasPorTipo(contarEntradasPorTipo(compras, ajustes))
                .huboVentaDolares(huboVentaDolares(compras))
                .entradasFisicasRestantes(entradasFisicasRestantes)
                .build();
    }

    /** "usuarioNombre" pasa a ordenar por las dos columnas reales detrás (nombre, apellido);
     * "totalRetiros" no tiene una columna propia en Caja (se computa con un JOIN + SUM), así
     * que cae al valor por defecto en vez de fallar. */
    private static Sort ordenCajasCerradas(String ordenarPor, String direccion) {
        Sort.Direction sentido = "ASC".equalsIgnoreCase(direccion) ? Sort.Direction.ASC : Sort.Direction.DESC;
        if ("usuarioNombre".equals(ordenarPor)) {
            return Sort.by(sentido, "usuario.nombre").and(Sort.by(sentido, "usuario.apellido"));
        }
        Set<String> ordenables = Set.of("fechaApertura", "fechaCierre", "montoInicial", "montoEsperado", "montoContado", "diferencia");
        String campo = ordenables.contains(ordenarPor) ? ordenarPor : "fechaCierre";
        return Sort.by(sentido, campo);
    }

    @Override
    public CajasCerradasResponseDTO getCajasCerradas(LocalDate desde, LocalDate hasta, String usuarioNombre,
                                                       String ordenarPor, String direccion, int page, int size) {
        LocalDateTime desdeDt = desde.atStartOfDay();
        LocalDateTime hastaDt = hasta.atTime(LocalTime.MAX);
        int tamanioPagina = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(Math.max(page, 0), tamanioPagina, ordenCajasCerradas(ordenarPor, direccion));

        Specification<Caja> spec = Specification.allOf(
                CajaSpecifications.cerradaEntre(desdeDt, hastaDt),
                CajaSpecifications.deUsuarioNombre(usuarioNombre),
                CajaSpecifications.habilitada()
        );
        Page<Caja> pagina = cajaRepository.findAll(spec, pageable);

        // Sólo la página: retiros y diferencia de posnet (N+1 / 3 queries en lote) se calculan para
        // las ≤100 filas que se muestran, no para todo el rango.
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
                        difPosnet.getOrDefault(caja.getId(), BigDecimal.ZERO)
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
    public List<CajaAbiertaDTO> getCajasAbiertas() {
        return cajaRepository.findAllByFechaCierreIsNullOrderByFechaAperturaAsc().stream()
                .map(caja -> {
                    // Acá sí se expone lo vendido hasta el momento: a diferencia de getActual (que lo
                    // esconde para que el propio boletero no pueda "calcar" el cierre), esto lo ve el
                    // admin en el dashboard de Hoy, no el dueño de la caja.
                    // El pago en dólares sigue siendo EFECTIVO_BOLETERIA (misma forma de pago,
                    // sólo cambia la moneda física), así que ya está incluido acá.
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
                            .build();
                })
                .toList();
    }

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

    // Los cálculos de abajo trabajan sobre las compras/movimientos de la caja ya traídos:
    // toDto y getOperacionesCaja los piden UNA vez y los reparten a todos (antes cada helper
    // repetía su propio findAllByCajaId sobre la misma caja).

    private BigDecimal sumVentasPorFormaPago(List<Compra> compras, FormaPago formaPago) {
        return compras.stream()
                .filter(c -> c.getFormaPago() == formaPago && c.getEstado() != EstadoCompra.CANCELADO)
                .map(Compra::getMontoTotal)
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
     * Impacto real en el cajón de PESOS de una venta en efectivo: si se pagó en pesos, entra
     * el total de la venta; si se pagó en dólares, no entra nada en pesos (entraron dólares,
     * contados aparte) pero SÍ sale el vuelto en pesos que se le dio al cliente — por eso acá
     * se resta en vez de sumar. Sin esto, efectivoEsperado quedaría de más por cada venta en
     * dólares (contaría el precio en pesos que en realidad nunca entró al cajón).
     */
    private BigDecimal impactoEfectivoArs(Compra compra) {
        if (compra.getCotizacionDolar() == null) return compra.getMontoTotal();
        return vueltoPesos(compra).negate();
    }

    private BigDecimal sumImpactoEfectivoArs(List<Compra> compras) {
        return compras.stream()
                .filter(c -> c.getFormaPago() == FormaPago.EFECTIVO_BOLETERIA && c.getEstado() != EstadoCompra.CANCELADO)
                .map(this::impactoEfectivoArs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Null si esta caja no tuvo ninguna venta en dólares (no hay nada que contar); si tuvo, exige el conteo. */
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

    /** Convierte las filas [idCaja, valor] de un GROUP BY a un Map, tolerando que el valor venga como Long/BigDecimal. */
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
     * Cuántas entradas debería haber cortado el boletero según lo que el sistema registró como
     * entregado, más el impacto de los ajustes manuales (una venta agregada suma sus pases al
     * talonario esperado; una quitada los resta).
     */
    private int calcularEntradasEsperadas(List<Compra> compras, List<AjusteCaja> ajustes) {
        int base = compras.stream()
                .filter(c -> c.getEstado() != EstadoCompra.CANCELADO)
                .flatMap(c -> c.getDetalles().stream())
                .filter(d -> d.getTipoEntrada() != null && Boolean.TRUE.equals(d.getTipoEntrada().getEntregaEntrada()))
                .mapToInt(CompraDetalle::getCantidad)
                .sum();
        return base + impactoAjustesEntradas(ajustes, tiposEntradaPorId(),
                t -> Boolean.TRUE.equals(t.getEntregaEntrada()));
    }

    /** Unidades vendidas de tipos de entrada con precio > 0 (excluye las gratis, los extras y los artículos, y las compras canceladas), más el impacto de los ajustes manuales. */
    private int contarEntradasPagas(List<Compra> compras, List<AjusteCaja> ajustes) {
        int base = compras.stream()
                .filter(c -> c.getEstado() != EstadoCompra.CANCELADO)
                .flatMap(c -> c.getDetalles().stream())
                .filter(d -> d.getTipoEntrada() != null
                        && d.getTipoEntrada().getTipo() == Tipo.ENTRADA
                        && d.getTipoEntrada().getPrecio() != null
                        && d.getTipoEntrada().getPrecio().compareTo(BigDecimal.ZERO) > 0)
                .mapToInt(CompraDetalle::getCantidad)
                .sum();
        return base + impactoAjustesEntradas(ajustes, tiposEntradaPorId(),
                t -> t.getTipo() == Tipo.ENTRADA && t.getPrecio() != null && t.getPrecio().compareTo(BigDecimal.ZERO) > 0);
    }

    /** Cuenta las unidades vendidas por tipo de entrada (ignora extras y artículos varios, y las compras canceladas), aplicando también los ajustes manuales. */
    private List<EntradasPorTipoDTO> contarEntradasPorTipo(List<Compra> compras, List<AjusteCaja> ajustes) {
        Map<String, Integer> cantidadPorTipo = new LinkedHashMap<>();
        for (Compra compra : compras) {
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

    /** Parte del monto de una compra que corresponde a artículos varios (líneas sin tipo de entrada), a precio congelado. */
    private BigDecimal montoArticulos(Compra compra) {
        return compra.getDetalles().stream()
                .filter(d -> d.getTipoEntrada() == null && d.getPrecioUnitario() != null)
                .map(d -> d.getPrecioUnitario().multiply(BigDecimal.valueOf(d.getCantidad())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Una entrada por cada línea de entrada PAGA de la compra, con la parte del monto que le
     * toca: se reparte lo cobrado de entradas (total − artículos) proporcional al precio de lista
     * de cada línea, y la última línea absorbe el redondeo para que la suma cuadre exacta.
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
                .map(d -> d.getTipoEntrada().getPrecio().multiply(BigDecimal.valueOf(d.getCantidad())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<SegmentoEntradaDTO> out = new ArrayList<>();
        BigDecimal acumulado = BigDecimal.ZERO;
        for (int i = 0; i < lineas.size(); i++) {
            CompraDetalle d = lineas.get(i);
            BigDecimal monto;
            if (i == lineas.size() - 1) {
                monto = montoEntradas.subtract(acumulado);
            } else if (sumaLista.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal lista = d.getTipoEntrada().getPrecio().multiply(BigDecimal.valueOf(d.getCantidad()));
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

    private List<OperacionCajaDTO> construirOperaciones(List<Compra> compras, List<RetiroCaja> movimientos, List<IngresoEntradas> ingresos) {
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

    private CajaResponseDTO toDto(Caja caja) {
        // Mientras la caja sigue abierta, ningún total "esperado" se expone: si el boletero
        // pudiera verlos antes de cerrar, alcanzaría con anotar esos mismos números para que
        // el cierre le dé perfecto aunque haya plata de menos. Recién se revelan al cerrar.
        boolean cerrada = caja.getFechaCierre() != null;

        // Todo lo de esta caja se trae UNA sola vez y se reparte a cada cálculo: antes cada
        // helper (sumVentasPorFormaPago, contarEntradasPagas, construirOperaciones, …) repetía
        // su propio findAllByCajaId sobre la misma caja, ~10 veces por respuesta.
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
        List<OperacionCajaDTO> operaciones = null;
        Integer totalEntradasPagas = null;
        List<EntradasPorTipoDTO> entradasVendidasPorTipo = null;
        BigDecimal dolaresEsperado = null;
        BigDecimal diferenciaDolares = null;

        // Booleano, no un monto: seguro de exponer aunque la caja siga ABIERTA (ver el
        // comentario en CajaResponseDTO.huboVentaDolares).
        boolean huboVentaDolares = huboVentaDolares(compras);

        if (cerrada) {
            // totalVentasEfectivo es la revenue "de lista" (precio de venta, sea cual sea la
            // moneda física con la que se pagó); efectivoEsperado es lo que tiene que haber
            // físicamente en pesos en el cajón — para eso usa sumImpactoEfectivoArs, que resta
            // el vuelto en pesos de las ventas en dólares en vez de sumar su precio (ver el
            // comentario de impactoEfectivoArs). Los dos números divergen a propósito cuando
            // hubo ventas en dólares.
            // Ajustes manuales de la repartición por forma de pago (traspasos que cargó un
            // admin cuando la cajera cobró de una forma y registró otra): se aplican como un
            // neto sobre lo vendido de cada forma. No cambian el total vendido (lo que sale de
            // una entra en otra), sólo a cuál forma se le atribuye.
            totalVentasEfectivo = sumVentasPorFormaPago(compras, FormaPago.EFECTIVO_BOLETERIA)
                    .add(sumAjustesNeto(ajustes, FormaPago.EFECTIVO_BOLETERIA));
            efectivoEsperado = calcularMontoEsperado(caja, compras, movimientos, ajustes);

            List<CierrePosnet> cierres = cierrePosnetRepository.findAllByCajaIdOrderByIdAsc(caja.getId());
            boolean combinado = cierres.stream().anyMatch(c -> c.getFormaPago() == null);

            // Lo vendido con Tarjeta y con QR se manda SIEMPRE por separado (el detalle de venta
            // sí distingue la forma), aunque el cierre del posnet se haya cargado combinado.
            totalVentasTarjeta = sumVentasPorFormaPago(compras, FormaPago.TARJETA)
                    .add(sumAjustesNeto(ajustes, FormaPago.TARJETA));
            totalVentasQr = sumVentasPorFormaPago(compras, FormaPago.MERCADO_PAGO_QR)
                    .add(sumAjustesNeto(ajustes, FormaPago.MERCADO_PAGO_QR));
            if (combinado) {
                // Cargado como un solo monto de Tarjeta+QR juntos: lo contado no se reparte entre
                // ambas, se compara ya combinado (totalCerradoTarjeta/Qr quedan null).
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

            entradasFisicasEsperadas = calcularEntradasEsperadas(compras, ajustes);
            if (caja.getEntradasFisicasCortadas() != null) {
                // Mismo criterio que la diferencia de efectivo (contado − esperado): positivo es
                // sobrante, negativo es faltante. Acá "lo esperado que quede en el talonario" es
                // inicial − entradasFisicasEsperadas y "lo que quedó de verdad" es inicial −
                // cortadas; restando el inicial de los dos lados queda esperadas − cortadas. Si
                // cortó MÁS de lo que las ventas justifican (cortadas > esperadas), faltan
                // entradas en el talonario — no sobran.
                diferenciaEntradas = entradasFisicasEsperadas - caja.getEntradasFisicasCortadas();
            }

            operaciones = construirOperaciones(compras, movimientos, ingresosMovimientos);

            entradasVendidasPorTipo = contarEntradasPorTipo(compras, ajustes);
            totalEntradasPagas = contarEntradasPagas(compras, ajustes);

            if (huboVentaDolares) {
                dolaresEsperado = sumDolaresEsperados(compras);
                if (caja.getDolaresContado() != null) {
                    diferenciaDolares = caja.getDolaresContado().subtract(dolaresEsperado);
                }
            }
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
                .entradasFisicasCortadas(caja.getEntradasFisicasCortadas())
                .entradasFisicasEsperadas(entradasFisicasEsperadas)
                .diferenciaEntradas(diferenciaEntradas)
                .totalIngresosEntradas(totalIngresosEntradas)
                .ingresosEntradas(ingresosEntradas)
                .operaciones(operaciones)
                .totalEntradasPagas(totalEntradasPagas)
                .entradasVendidasPorTipo(entradasVendidasPorTipo)
                .huboVentaDolares(huboVentaDolares)
                .dolaresEsperado(dolaresEsperado)
                .dolaresContado(caja.getDolaresContado())
                .diferenciaDolares(diferenciaDolares)
                .habilitada(caja.estaHabilitada())
                .build();
    }
}
