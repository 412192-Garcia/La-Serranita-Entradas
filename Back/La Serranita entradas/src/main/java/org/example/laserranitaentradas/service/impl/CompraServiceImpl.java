package org.example.laserranitaentradas.service.impl;

import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.payment.PaymentRefundClient;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.net.MPSearchRequest;
import com.mercadopago.resources.payment.Payment;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import jakarta.transaction.Transactional;
import org.example.laserranitaentradas.model.dto.*;
import org.example.laserranitaentradas.model.entity.Caja;
import org.example.laserranitaentradas.model.entity.Cliente;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.CompraDetalle;
import org.example.laserranitaentradas.model.entity.ArticuloVario;
import org.example.laserranitaentradas.model.entity.Cupon;
import org.example.laserranitaentradas.model.entity.FormaPago;
import org.example.laserranitaentradas.model.entity.Promocion;
import org.example.laserranitaentradas.model.entity.Tipo;
import org.example.laserranitaentradas.model.entity.TipoEntrada;
import org.example.laserranitaentradas.model.entity.Usuario;
import org.example.laserranitaentradas.model.entity.EstadoCompra;
import org.example.laserranitaentradas.repository.ArticuloVarioRepository;
import org.example.laserranitaentradas.repository.CompraRepository;
import org.example.laserranitaentradas.repository.CompraSpecifications;
import org.example.laserranitaentradas.repository.PromocionRepository;
import org.example.laserranitaentradas.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CompraServiceImpl implements CompraService {

    private static final Logger log = LoggerFactory.getLogger(CompraServiceImpl.class);

    private final CompraRepository compraRepository;
    private final TipoEntradaService tipoEntradaService;
    private final CuponService cuponService;
    private final DiaAperturaService diaAperturaService;
    private final ClienteService clienteService;
    private final UsuarioService usuarioService;
    private final CalculoPrecioService calculoPrecioService;
    private final EmailService emailService;
    private final CajaService cajaService;
    private final PromocionRepository promocionRepository;
    private final ArticuloVarioRepository articuloVarioRepository;
    private final Map<FormaPago, PagoService> estrategiasPago;
    private final EntityManager em;

    public CompraServiceImpl
            (CompraRepository compraRepository,
             TipoEntradaService tipoEntradaService,
             CuponService cuponService,
             DiaAperturaService diaAperturaService,
             ClienteService clienteService,
             UsuarioService usuarioService,
             CalculoPrecioService calculoPrecioService,
             EmailService emailService,
             CajaService cajaService,
             PromocionRepository promocionRepository,
             ArticuloVarioRepository articuloVarioRepository,
             List<PagoService> estrategiasDisponibles,
             EntityManager em)
    {
        this.compraRepository = compraRepository;
        this.tipoEntradaService = tipoEntradaService;
        this.cuponService = cuponService;
        this.diaAperturaService = diaAperturaService;
        this.clienteService = clienteService;
        this.usuarioService = usuarioService;
        this.calculoPrecioService = calculoPrecioService;
        this.emailService = emailService;
        this.cajaService = cajaService;
        this.promocionRepository = promocionRepository;
        this.articuloVarioRepository = articuloVarioRepository;
        this.estrategiasPago = estrategiasDisponibles.stream()
                .collect(Collectors.toMap(PagoService::getFormaPago, estrategia -> estrategia));
        this.em = em;
    }

    /**
     * Descuento de venta en puerta: catálogo de promos con nombre, o un descuento manual
     * ad-hoc que el cajero tipea directo (% o $) — mutuamente excluyentes. A diferencia del
     * cupón online, no queda un registro de "qué promo se usó" en la Compra: sólo se guarda
     * el monto de descuento resultante, igual que ya hace el cupón online con montoTotal.
     */
    private BigDecimal calcularDescuentoPos(BigDecimal montoBruto, Long promocionId,
                                             BigDecimal descuentoManualPorcentaje, BigDecimal descuentoManualMonto) {
        int cantidadElegidas = (promocionId != null ? 1 : 0)
                + (descuentoManualPorcentaje != null ? 1 : 0)
                + (descuentoManualMonto != null ? 1 : 0);
        if (cantidadElegidas > 1) {
            throw new IllegalArgumentException("Elegí una promo o cargá un descuento manual, no ambos");
        }

        BigDecimal descuento = BigDecimal.ZERO;
        if (promocionId != null) {
            Promocion promo = promocionRepository.findById(promocionId)
                    .orElseThrow(() -> new IllegalArgumentException("Promoción no encontrada para id: " + promocionId));
            if (!Boolean.TRUE.equals(promo.getActivo())) {
                throw new IllegalArgumentException("La promoción \"" + promo.getNombre() + "\" ya no está activa");
            }
            descuento = promo.getPorcentajeDescuento() != null
                    ? montoBruto.multiply(promo.getPorcentajeDescuento()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                    : promo.getMontoDescuento();
        } else if (descuentoManualPorcentaje != null) {
            descuento = montoBruto.multiply(descuentoManualPorcentaje).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else if (descuentoManualMonto != null) {
            descuento = descuentoManualMonto;
        }

        if (descuento.compareTo(BigDecimal.ZERO) < 0) descuento = BigDecimal.ZERO;
        if (descuento.compareTo(montoBruto) > 0) descuento = montoBruto;
        return descuento;
    }

    private PagoService resolverEstrategia(FormaPago formaPago) {
        if (formaPago == null) {
            throw new IllegalArgumentException("Debe indicar una forma de pago");
        }
        PagoService estrategia = estrategiasPago.get(formaPago);
        if (estrategia == null) {
            throw new IllegalArgumentException("Forma de pago no soportada: " + formaPago);
        }
        return estrategia;
    }

    @Transactional
    @Override
    public CompraResponseDTO iniciarCompraConPago(CompraRequestDTO compraRequest) throws Exception {

        Compra compraGuardada = this.create(compraRequest);

        PagoService estrategia = resolverEstrategia(compraGuardada.getFormaPago());
        PagoResponseDTO respuestaPago = estrategia.procesarPago(compraGuardada);

        CompraResponseDTO dto = new CompraResponseDTO();
        dto.setId(compraGuardada.getId());
        dto.setCodigoReserva(compraGuardada.getCodigoReserva());
        dto.setMontoTotal(compraGuardada.getMontoTotal());
        dto.setEstado(compraGuardada.getEstado().name());
        dto.setPreferenceId(respuestaPago.getPreferenceId());
        dto.setInitPoint(respuestaPago.getInitPoint());

        if (compraGuardada.getFormaPago() != null) {
            dto.setFormaPago(compraGuardada.getFormaPago());
        }

        dto.setPreferenceId(respuestaPago.getPreferenceId());
        dto.setInitPoint(respuestaPago.getInitPoint());

        return dto;
    }


    @Transactional
    @Override
    public Compra create(CompraRequestDTO compraRequest) {

        PagoService estrategia = resolverEstrategia(compraRequest.getFormaPago());

        ClienteDTO clienteDTO = compraRequest.getCliente();
        LocalDate fechaVisita = compraRequest.getFecha();

        // fechaVisita null = compra como regalo: quien lo recibe elige el día, no hay fecha que validar.
        String receptorNombre = null;
        String receptorEmail = null;
        String receptorDni = null;
        String receptorTelefono = null;
        if (fechaVisita != null) {
            Boolean abierto = diaAperturaService.getAbiertoByDate(fechaVisita);
            if (abierto == null || !abierto) {
                throw new IllegalArgumentException("El parque está cerrado en la fecha solicitada: " + fechaVisita);
            }
        } else {
            // Un regalo tiene que llegar pagado: si se reservara en efectivo, quien lo recibe
            // terminaría pagando de su bolsillo en la boletería lo que se supone que le regalaron.
            if (compraRequest.getFormaPago() == FormaPago.EFECTIVO_BOLETERIA) {
                throw new IllegalArgumentException("Los regalos sólo se pueden pagar online: no se puede reservar en efectivo.");
            }
            ReceptorRegaloDTO receptor = compraRequest.getReceptor();
            if (receptor == null || esBlanco(receptor.getNombre()) || esBlanco(receptor.getEmail()) || esBlanco(receptor.getDni())) {
                throw new IllegalArgumentException("Para comprar como regalo hay que indicar nombre, DNI y email de quien lo recibe.");
            }
            receptorNombre = receptor.getNombre();
            receptorEmail = receptor.getEmail();
            receptorDni = receptor.getDni();
            receptorTelefono = receptor.getTelefono();
        }


        Cliente cliente = null;
        if (clienteDTO != null && !esBlanco(clienteDTO.getDni())) {
            String dniStr = clienteDTO.getDni().trim();
            String nombreTipeado = normalizarNombre(clienteDTO.getNombre(), clienteDTO.getApellido());

            // Puede haber más de un cliente con este DNI (ver ClienteRepository): se busca,
            // entre los que ya existen, el que tenga el nombre más parecido al recién tipeado.
            Cliente mejorCandidato = null;
            double mejorSimilitud = -1;
            for (Cliente existente : clienteService.findAllByDni(dniStr)) {
                double similitud = similitudNombres(normalizarNombre(existente.getNombre(), existente.getApellido()), nombreTipeado);
                if (similitud > mejorSimilitud) {
                    mejorSimilitud = similitud;
                    mejorCandidato = existente;
                }
            }

            boolean debeCrearClienteAparte = mejorCandidato == null
                    || mejorSimilitud < UMBRAL_SIMILITUD_NOMBRES
                    || (mejorSimilitud < 1.0 && Boolean.TRUE.equals(compraRequest.getEsOtraPersona()));

            if (debeCrearClienteAparte) {
                // Nadie con este DNI todavía, el nombre no se parece en nada al de quien ya
                // lo tiene, o el usuario aclaró que no es la misma persona (nombre parecido
                // por casualidad): en vez de pisar los datos de quien ya estaba registrado,
                // se crea un cliente nuevo con el mismo DNI (no hace falta confirmar nada más).
                cliente = clienteService.create(Cliente.builder()
                        .dni(dniStr)
                        .nombre(clienteDTO.getNombre())
                        .apellido(clienteDTO.getApellido())
                        .edad(clienteDTO.getEdad())
                        .localidad(clienteDTO.getLocalidad())
                        .build());
            } else if (mejorSimilitud >= 1.0 || Boolean.TRUE.equals(compraRequest.getConfirmarDniExistente())) {
                // Nombre idéntico (se reutiliza sin preguntar nada), o ya confirmó que es la
                // misma persona con un nombre parecido pero no idéntico (typo).
                cliente = mejorCandidato;
                if (Boolean.TRUE.equals(compraRequest.getActualizarDatosCliente())) {
                    // Eligió actualizar sus datos: se pisan nombre/apellido/edad/localidad
                    // con lo recién tipeado.
                    cliente.setNombre(clienteDTO.getNombre());
                    cliente.setApellido(clienteDTO.getApellido());
                    cliente.setEdad(clienteDTO.getEdad());
                    cliente.setLocalidad(clienteDTO.getLocalidad());
                    cliente = clienteService.create(cliente);
                }
            } else {
                // Nombre parecido pero no idéntico al de quien ya tiene este DNI: puede ser
                // la misma persona con un typo, o puede ser otra — se le avisa al frontend
                // para que confirme antes de asociar la compra a esa identidad.
                throw new IllegalArgumentException("DNI_YA_REGISTRADO: Ya hay una reserva registrada con el DNI "
                        + dniStr + " a nombre de \"" + mejorCandidato.getNombre()
                        + (mejorCandidato.getApellido() != null ? " " + mejorCandidato.getApellido() : "")
                        + "\". Si sos la misma persona, confirmá para continuar.");
            }
        }

        String contactEmail = clienteDTO != null ? clienteDTO.getEmail() : null;
        String contactPhone = clienteDTO != null ? clienteDTO.getTelefono() : null;

        Cupon cupon = null;
        if (compraRequest.getCuponCodigo() != null) {
            cupon = cuponService.getByCode(compraRequest.getCuponCodigo()).orElse(null);
            if (cupon != null) {
                LocalDate hoy = LocalDate.now();
                if (cupon.getUsosMaximos() != null && cupon.getUsosActuales() >= cupon.getUsosMaximos()) {
                    throw new IllegalArgumentException("Cupón ha alcanzado su límite de usos: " + compraRequest.getCuponCodigo());
                }
                if (!cupon.getActivo() || cupon.getFechaExpiracion().isBefore(hoy)) {
                    throw new IllegalArgumentException("Cupón no válido o expirado: " + compraRequest.getCuponCodigo());
                }
            } else {
                throw new IllegalArgumentException("Cupón no encontrado para código: " + compraRequest.getCuponCodigo());
            }
        }

        DetallesCalculados calculo = construirDetalles(
                compraRequest.getEntradas(), compraRequest.getFormaPago(), fechaVisita);
        List<CompraDetalle> detalles = calculo.detalles();
        BigDecimal montoTotal = calculo.montoTotal();

        validarPaseObligatorio(detalles);

        BigDecimal descuentoAplicado = BigDecimal.ZERO;
        if (cupon != null) {

            if (cupon.getPorcentajeDescuento() != null) {
                descuentoAplicado = montoTotal.multiply(cupon.getPorcentajeDescuento()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            } else if (cupon.getMontoDescuento() != null) {
                descuentoAplicado = cupon.getMontoDescuento();
            }

            if (descuentoAplicado.compareTo(montoTotal) > 0) {
                descuentoAplicado = montoTotal;
            }

             cupon.setUsosActuales(cupon.getUsosActuales() + 1);
             if (cupon.getUsosMaximos() != null && cupon.getUsosActuales() >= cupon.getUsosMaximos()) {
                 cupon.setActivo(false);
             }
             cuponService.update(cupon);

        }

        String codigoReserva = generarCodigoReserva(fechaVisita);

        Compra nuevaCompra = Compra.builder()
                .cliente(cliente)
                .contactEmail(contactEmail)
                .contactPhone(contactPhone)
                .fechaVisita(fechaVisita)
                .codigoReserva(codigoReserva)
                .montoTotal(montoTotal)
                .descuentoAplicado(descuentoAplicado)
                .cupon(cupon)
                .detalles(detalles)
                .estado(estrategia.getEstadoInicial())
                .formaPago(compraRequest.getFormaPago())
                .receptorNombre(receptorNombre)
                .receptorEmail(receptorEmail)
                .receptorDni(receptorDni)
                .receptorTelefono(receptorTelefono)
                .build();


        for (CompraDetalle det : detalles) {
            det.setCompra(nuevaCompra);
        }

        return compraRepository.save(nuevaCompra);
    }

    @Transactional
    @Override
    public Compra actualizarEstado(Long compraId, EstadoCompra nuevoEstado) {
        Compra compra = compraRepository.findById(compraId)
                .orElseThrow(() -> new IllegalArgumentException("Compra no encontrada ID: " + compraId));

        compra.setEstado(nuevoEstado);
        return compraRepository.save(compra);
    }

    @Override
    public Optional<Compra> findById(Long id) {
        return compraRepository.findById(id);
    }

    @Override
    public Optional<Compra> findByDniandFecha(String dni, LocalDate fechaVisita) {
        return compraRepository.findByClienteDniAndFechaVisita(dni,fechaVisita);
    }

    @Override
    public Page<Compra> buscar(BusquedaComprasFiltroDTO filtro, Pageable pageable) {
        Specification<Compra> spec = construirSpecBusqueda(filtro);
        return compraRepository.findAll(spec, pageable);
    }

    /** Mismo criterio de `buscar()` (tipo→estados, texto, fecha(s), forma de pago) armado como Specification reutilizable. */
    private Specification<Compra> construirSpecBusqueda(BusquedaComprasFiltroDTO filtro) {
        List<EstadoCompra> estados = filtro.estados();
        if (filtro.tipo() == TipoListadoCompra.BOLETERIA) {
            // La venta de puerta es el único estado que nunca fue una reserva anticipada.
            estados = List.of(EstadoCompra.VENDIDO_EN_PUERTA);
        } else if (filtro.tipo() == TipoListadoCompra.ANTICIPADA && (estados == null || estados.isEmpty())) {
            // Sin un estado puntual elegido, "anticipada" es todo lo que no sea venta de puerta.
            estados = Arrays.stream(EstadoCompra.values())
                    .filter(e -> e != EstadoCompra.VENDIDO_EN_PUERTA)
                    .toList();
        }

        return Specification.allOf(
                CompraSpecifications.textoLibre(filtro.texto()),
                CompraSpecifications.fecha(filtro.fecha()),
                CompraSpecifications.sinFecha(filtro.sinFecha()),
                CompraSpecifications.fechaDesde(filtro.fechaDesde()),
                CompraSpecifications.fechaHasta(filtro.fechaHasta()),
                CompraSpecifications.estadoIn(estados),
                CompraSpecifications.formaPago(filtro.formaPago())
        );
    }

    @Override
    public Page<LocalDate> fechasDistintas(BusquedaComprasFiltroDTO filtro, Pageable pageable) {
        Specification<Compra> spec = construirSpecBusqueda(filtro);

        // JpaSpecificationExecutor no ofrece proyecciones (sólo entidades completas), así que
        // para "fechaVisita distintas" hace falta armar la Criteria a mano — reutilizando el
        // mismo Specification de arriba, que sólo necesita un (root, query, cb) cualquiera.
        CriteriaBuilder cb = em.getCriteriaBuilder();

        // Los regalos (fechaVisita null) quedan afuera de esta enumeración a propósito: tienen
        // su propio bloque en Boletería (filtro sinFecha de /buscar, ver CompraSpecifications),
        // así que esta paginación por día —pensada para "Ver todas las fechas"— es sólo de días
        // reales, sin una entrada fantasma para ellos.
        CriteriaQuery<LocalDate> queryDatos = cb.createQuery(LocalDate.class);
        Root<Compra> rootDatos = queryDatos.from(Compra.class);
        queryDatos.select(rootDatos.get("fechaVisita"))
                .distinct(true)
                .where(cb.and(spec.toPredicate(rootDatos, queryDatos, cb), cb.isNotNull(rootDatos.get("fechaVisita"))))
                .orderBy(cb.asc(rootDatos.get("fechaVisita")));
        List<LocalDate> pagina = em.createQuery(queryDatos)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();

        CriteriaQuery<Long> queryConteo = cb.createQuery(Long.class);
        Root<Compra> rootConteo = queryConteo.from(Compra.class);
        queryConteo.select(cb.countDistinct(rootConteo.get("fechaVisita")))
                .where(cb.and(spec.toPredicate(rootConteo, queryConteo, cb), cb.isNotNull(rootConteo.get("fechaVisita"))));
        long total = em.createQuery(queryConteo).getSingleResult();

        return new PageImpl<>(pagina, pageable, total);
    }

    @Transactional
    @Override
    public Compra marcarEntradasComoUsadas(Long compraId, Long usuarioValidadorId) {

        Compra compra = compraRepository.findById(compraId).orElseThrow(() -> new IllegalArgumentException("Compra no encontrada para id: " + compraId));

        Usuario usuario = usuarioService.obtenerUsuarioPorId(usuarioValidadorId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario validador no encontrado para id: " + usuarioValidadorId));

        compra.setEstado(EstadoCompra.USADO);
        compra.setUsuarioValidador(usuario);
        compra.setFechaValidacion(LocalDateTime.now());

        return compraRepository.save(compra);
    }

    /** Ventana de tiempo, más generosa que la del frontend, para poder deshacer una validación. */
    private static final long VENTANA_DESHACER_VALIDACION_SEGUNDOS = 120;

    @Transactional
    @Override
    public Compra deshacerValidacion(Long compraId) {
        Compra compra = compraRepository.findById(compraId)
                .orElseThrow(() -> new IllegalArgumentException("Compra no encontrada ID: " + compraId));

        if (compra.getEstado() != EstadoCompra.USADO) {
            throw new IllegalStateException(
                    "Sólo se puede deshacer la validación de una compra recién marcada como usada (estado actual: " + compra.getEstado() + ")");
        }
        if (compra.getFechaValidacion() == null
                || compra.getFechaValidacion().isBefore(LocalDateTime.now().minusSeconds(VENTANA_DESHACER_VALIDACION_SEGUNDOS))) {
            throw new IllegalStateException("Ya pasó el tiempo para deshacer esta validación.");
        }

        // Vuelve al estado del que salió: RESERVADO_EFECTIVO si era una reserva a cobrar en
        // caja, APROBADO si ya estaba paga online. Si había entrado a una caja (efectivo), se
        // saca: el cierre de caja no debe seguir contándola.
        compra.setEstado(compra.getFormaPago() == FormaPago.EFECTIVO_BOLETERIA
                ? EstadoCompra.RESERVADO_EFECTIVO
                : EstadoCompra.APROBADO);
        compra.setUsuarioValidador(null);
        compra.setFechaValidacion(null);
        compra.setCaja(null);

        return compraRepository.save(compra);
    }

    @Transactional
    @Override
    public Compra confirmarPagoEfectivo(Long compraId, Long usuarioValidadorId) {
        Compra compra = compraRepository.findById(compraId)
                .orElseThrow(() -> new IllegalArgumentException("Compra no encontrada ID: " + compraId));

        if (compra.getFormaPago() != FormaPago.EFECTIVO_BOLETERIA) {
            throw new IllegalStateException("La compra ID " + compraId + " no corresponde a una reserva con pago en efectivo");
        }
        if (compra.getEstado() != EstadoCompra.RESERVADO_EFECTIVO) {
            throw new IllegalStateException("La compra ID " + compraId + " no está pendiente de cobro en boletería (estado actual: " + compra.getEstado() + ")");
        }

        // El cobro pasa a integrar la caja abierta del boletero: sin caja no se puede cobrar.
        Caja caja = cajaService.getAbiertaOrThrow(usuarioValidadorId);
        Compra actualizada = marcarEntradasComoUsadas(compraId, usuarioValidadorId);
        actualizada.setCaja(caja);
        return compraRepository.save(actualizada);
    }

    @Override
    public CotizacionResponseDTO cotizar(CotizacionRequestDTO cotizacionRequest) {
        FormaPago formaPago = cotizacionRequest.getFormaPago();
        if (formaPago == null) {
            throw new IllegalArgumentException("Debe indicar una forma de pago");
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal ahorro = BigDecimal.ZERO;

        if (cotizacionRequest.getEntradas() != null) {
            for (DetalleCompraDTO d : cotizacionRequest.getEntradas()) {
                if (d == null || d.getTipoEntradaId() == null || d.getCantidad() == null) continue;

                TipoEntrada tipoEntrada = tipoEntradaService.findById(d.getTipoEntradaId())
                        .orElseThrow(() -> new IllegalArgumentException("TipoEntrada no encontrada para id: " + d.getTipoEntradaId()));

                subtotal = subtotal.add(calculoPrecioService.calcularTotal(tipoEntrada, d.getCantidad(), formaPago));
                ahorro = ahorro.add(calculoPrecioService.calcularAhorro(tipoEntrada, d.getCantidad(), formaPago));
            }
        }

        if (cotizacionRequest.getArticulos() != null) {
            for (LineaArticuloPosDTO a : cotizacionRequest.getArticulos()) {
                if (a == null || a.getCantidad() == null || a.getPrecioUnitario() == null) continue;
                subtotal = subtotal.add(a.getPrecioUnitario().multiply(BigDecimal.valueOf(a.getCantidad())));
            }
        }

        // El descuento de promo/manual (sólo venta en puerta) reduce el subtotal a cobrar,
        // pero no se mezcla con "ahorro": ahorro es específicamente el precio de grupo.
        BigDecimal descuento = calcularDescuentoPos(subtotal, cotizacionRequest.getPromocionId(),
                cotizacionRequest.getDescuentoManualPorcentaje(), cotizacionRequest.getDescuentoManualMonto());

        CotizacionResponseDTO dto = new CotizacionResponseDTO();
        dto.setSubtotal(subtotal.subtract(descuento));
        dto.setAhorro(ahorro);
        return dto;
    }

    @Override
    public Optional<String> consultarEstadoCompra(Long id) {
        return compraRepository.findById(id).map(compra -> compra.getEstado().name());
    }

    private boolean esBlanco(String s) {
        return s == null || s.isBlank();
    }

    /** Minúsculas, sin acentos y sin espacios de más — para comparar nombres tolerando variaciones menores. */
    private String normalizarNombre(String nombre, String apellido) {
        String junto = (nombre == null ? "" : nombre) + " " + (apellido == null ? "" : apellido);
        String sinAcentos = java.text.Normalizer.normalize(junto.trim().toLowerCase(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinAcentos.replaceAll("\\s+", " ");
    }

    /**
     * Por debajo de esto, dos nombres (ya normalizados con normalizarNombre) se consideran
     * personas distintas: no se pregunta nada, se crea un cliente nuevo con el mismo DNI.
     * Por encima (pero sin ser idénticos), se asume que puede ser la misma persona con un
     * typo en el nombre y se pide confirmación antes de reutilizar/actualizar sus datos.
     */
    private static final double UMBRAL_SIMILITUD_NOMBRES = 0.6;

    /** Similitud entre 0 (nada que ver) y 1 (idénticos), basada en distancia de Levenshtein. */
    private double similitudNombres(String a, String b) {
        int maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) return 1.0;
        return 1.0 - ((double) distanciaLevenshtein(a, b) / maxLen);
    }

    private int distanciaLevenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int costo = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + costo);
            }
        }
        return dp[a.length()][b.length()];
    }

    /** Detalles ya validados junto con el monto que suman, para devolver ambos de una sola pasada. */
    private record DetallesCalculados(List<CompraDetalle> detalles, BigDecimal montoTotal) {}

    /**
     * Convierte las líneas del pedido en detalles de compra, cobrando el precio que
     * corresponde a la forma de pago (el precio promocional por grupo sólo existe en
     * efectivo) y verificando contra el cupo diario de cada tipo.
     */
    private DetallesCalculados construirDetalles(List<DetalleCompraDTO> entradas, FormaPago formaPago, LocalDate fechaVisita) {
        // Cupo diario por tipo: se suma lo ya vendido ese día (sin contar lo cancelado)
        // más lo que se está agregando ahora. Los regalos no tienen fecha todavía, así
        // que no hay contra qué día chequear el cupo.
        Map<Long, Integer> cantidadVendidaPorTipo = new HashMap<>();
        if (fechaVisita != null) {
            for (Compra otra : compraRepository.findAllByFechaVisitaOrderByCodigoReservaAsc(fechaVisita)) {
                if (otra.getEstado() == EstadoCompra.CANCELADO || otra.getDetalles() == null) continue;
                for (CompraDetalle det : otra.getDetalles()) {
                    // Las líneas de artículo vario (venta en puerta) no tienen tipoEntrada: no cuentan para el cupo diario.
                    if (det.getTipoEntrada() == null) continue;
                    cantidadVendidaPorTipo.merge(det.getTipoEntrada().getId(), det.getCantidad(), Integer::sum);
                }
            }
        }

        BigDecimal montoTotal = BigDecimal.ZERO;
        List<CompraDetalle> detalles = new ArrayList<>();

        if (entradas != null) {
            for (DetalleCompraDTO d : entradas) {
                if (d == null || d.getTipoEntradaId() == null || d.getCantidad() == null) continue;

                Long tipoId = d.getTipoEntradaId();
                TipoEntrada tipoEntrada = tipoEntradaService.findById(tipoId)
                        .orElseThrow(() -> new IllegalArgumentException("TipoEntrada no encontrada para id: " + tipoId));

                if (tipoEntrada.getMaximoPorDia() != null) {
                    int nuevoTotal = cantidadVendidaPorTipo.merge(tipoId, d.getCantidad(), Integer::sum);
                    if (nuevoTotal > tipoEntrada.getMaximoPorDia()) {
                        throw new IllegalArgumentException(
                                "Se alcanzó el cupo diario de " + tipoEntrada.getNombre() + " para el " + fechaVisita
                                        + " (máximo " + tipoEntrada.getMaximoPorDia() + " por día).");
                    }
                }

                // RESERVA_ADMIN no cobra nada por acá: no tiene sentido pedirle un precio a
                // calculoPrecioService (que sólo sabe de precio de lista/grupo para las formas de pago reales).
                if (formaPago != FormaPago.RESERVA_ADMIN) {
                    montoTotal = montoTotal.add(calculoPrecioService.calcularTotal(tipoEntrada, d.getCantidad(), formaPago));
                }

                detalles.add(CompraDetalle.builder()
                        .tipoEntrada(tipoEntrada)
                        .cantidad(d.getCantidad())
                        .build());
            }
        }

        return new DetallesCalculados(detalles, montoTotal);
    }

    /**
     * Arma las líneas de artículo vario de una venta en puerta: de catálogo (articuloVarioId,
     * el nombre y precio quedan resueltos desde ahí) o libres (sólo descripcionLibre, sin
     * ningún catálogo detrás). A diferencia de las entradas, el precio SIEMPRE viene confiado
     * del cajero (precioUnitario) — no hay un precio "oficial" que validar contra un artículo
     * libre, y el catálogo mismo permite que el cajero lo ajuste al vender.
     */
    private DetallesCalculados construirLineasArticulos(List<LineaArticuloPosDTO> articulos) {
        BigDecimal montoTotal = BigDecimal.ZERO;
        List<CompraDetalle> detalles = new ArrayList<>();

        if (articulos != null) {
            for (LineaArticuloPosDTO a : articulos) {
                if (a == null || a.getCantidad() == null || a.getPrecioUnitario() == null) continue;
                if (a.getCantidad() < 1) {
                    throw new IllegalArgumentException("La cantidad de un artículo tiene que ser al menos 1");
                }
                if (a.getPrecioUnitario().compareTo(BigDecimal.ZERO) < 0) {
                    throw new IllegalArgumentException("El precio de un artículo no puede ser negativo");
                }

                boolean esDeCatalogo = a.getArticuloVarioId() != null;
                boolean esLibre = a.getDescripcionLibre() != null && !a.getDescripcionLibre().isBlank();
                if (esDeCatalogo == esLibre) {
                    throw new IllegalArgumentException("Cada artículo tiene que ser del catálogo o tener una descripción libre (no ambos, no ninguno)");
                }

                CompraDetalle.CompraDetalleBuilder builder = CompraDetalle.builder()
                        .cantidad(a.getCantidad())
                        .precioUnitario(a.getPrecioUnitario());

                if (esDeCatalogo) {
                    ArticuloVario articulo = articuloVarioRepository.findById(a.getArticuloVarioId())
                            .orElseThrow(() -> new IllegalArgumentException("Artículo no encontrado para id: " + a.getArticuloVarioId()));
                    builder.articuloVario(articulo);
                } else {
                    builder.descripcionLibre(a.getDescripcionLibre().trim());
                }

                detalles.add(builder.build());
                montoTotal = montoTotal.add(a.getPrecioUnitario().multiply(BigDecimal.valueOf(a.getCantidad())));
            }
        }

        return new DetallesCalculados(detalles, montoTotal);
    }

    /**
     * Si se compran entradas, tiene que haber al menos un pase de un tipo obligatorio (ej: un
     * adulto responsable). Las líneas de artículo vario (tipoEntrada null) se ignoran acá: una
     * venta de sólo artículos (sin entradas) no dispara esta exigencia.
     */
    private void validarPaseObligatorio(List<CompraDetalle> detalles) {
        boolean hayEntradas = detalles.stream()
                .anyMatch(d -> d.getTipoEntrada() != null && d.getTipoEntrada().getTipo() == Tipo.ENTRADA);
        boolean hayObligatorio = detalles.stream()
                .anyMatch(d -> d.getTipoEntrada() != null && Boolean.TRUE.equals(d.getTipoEntrada().getObligatorio()) && d.getCantidad() > 0);
        if (hayEntradas && !hayObligatorio) {
            throw new IllegalArgumentException(
                    "La compra debe incluir al menos un pase de un tipo obligatorio (por ejemplo, un adulto responsable) para poder ingresar al parque."
            );
        }
    }

    /**
     * Código visible yyMMdd-N: N es el orden de esta reserva entre todas las que ya
     * existen para ese mismo día de visita. Para regalos (sin fecha) se usa REGALO-N.
     */
    private String generarCodigoReserva(LocalDate fechaVisita) {
        if (fechaVisita != null) {
            long numeroDelDia = compraRepository.countByFechaVisita(fechaVisita) + 1;
            return fechaVisita.format(DateTimeFormatter.ofPattern("yyMMdd")) + "-" + numeroDelDia;
        }
        long numeroRegalo = compraRepository.countByFechaVisitaIsNull() + 1;
        return "REGALO-" + numeroRegalo;
    }

    @Transactional
    @Override
    public Compra registrarVentaPos(VentaPosRequestDTO request, Long usuarioVendedorId) {
        // Antes que nada: si esta misma venta ya se procesó (reintento del POS de algo cuya
        // respuesta se perdió en un corte de conexión), devolver la compra guardada en vez de
        // cobrarla dos veces.
        String idempotencyKey = request.getIdempotencyKey();
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<Compra> yaRegistrada = compraRepository.findByIdempotencyKey(idempotencyKey);
            if (yaRegistrada.isPresent()) {
                return yaRegistrada.get();
            }
        }
        if (request.getFormaPago() == null) {
            throw new IllegalArgumentException("Debe indicar la forma de pago del cobro");
        }
        boolean sinEntradas = request.getEntradas() == null || request.getEntradas().isEmpty();
        boolean sinArticulos = request.getArticulos() == null || request.getArticulos().isEmpty();
        if (sinEntradas && sinArticulos) {
            throw new IllegalArgumentException("La venta no tiene entradas ni artículos cargados");
        }

        Usuario vendedor = usuarioService.obtenerUsuarioPorId(usuarioVendedorId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario vendedor no encontrado para id: " + usuarioVendedorId));

        // Toda venta de puerta se hace contra una caja abierta, sea cual sea la forma de
        // pago: es la que después se cierra y concilia al final del turno.
        Caja caja = cajaService.getAbiertaOrThrow(usuarioVendedorId);

        // El visitante está entrando en este momento, así que la fecha de visita es hoy.
        // A diferencia de la compra online no se valida que el día esté marcado como
        // abierto: si hay alguien vendiendo en la boletería, el parque está abierto.
        // fechaOriginal la manda el POS cuando la venta estuvo encolada sin conexión: se usa
        // para que una venta cobrada a las 23:50 y sincronizada pasada la medianoche no quede
        // registrada como visita del día siguiente.
        LocalDateTime momentoVenta = request.getFechaOriginal() == null ? LocalDateTime.now() : request.getFechaOriginal();
        LocalDate hoy = momentoVenta.toLocalDate();

        DetallesCalculados calculoEntradas = construirDetalles(request.getEntradas(), request.getFormaPago(), hoy);
        DetallesCalculados calculoArticulos = construirLineasArticulos(request.getArticulos());

        List<CompraDetalle> todosLosDetalles = new ArrayList<>(calculoEntradas.detalles());
        todosLosDetalles.addAll(calculoArticulos.detalles());
        if (todosLosDetalles.isEmpty()) {
            throw new IllegalArgumentException("La venta no tiene entradas ni artículos cargados");
        }
        // Sólo exige el pase obligatorio si hay líneas de entrada: una venta sólo de
        // artículos (ej. un souvenir suelto) no tiene por qué incluir un pase de ingreso.
        validarPaseObligatorio(todosLosDetalles);

        BigDecimal montoBruto = calculoEntradas.montoTotal().add(calculoArticulos.montoTotal());
        BigDecimal descuento = calcularDescuentoPos(montoBruto, request.getPromocionId(),
                request.getDescuentoManualPorcentaje(), request.getDescuentoManualMonto());
        BigDecimal montoFinal = montoBruto.subtract(descuento);
        // calcularDescuentoPos ya validó que la promo exista y esté activa: se vuelve a buscar
        // acá sólo para poder guardar la referencia en la Compra (ver comentario en Compra.promocion).
        Promocion promocionUsada = request.getPromocionId() != null
                ? promocionRepository.findById(request.getPromocionId()).orElse(null)
                : null;

        // Pagar en dólares sigue siendo un cobro en EFECTIVO_BOLETERIA (misma forma de pago,
        // sólo cambia la moneda física): sólo se activa si el request manda cotización. El
        // boletero entra cuántos dólares recibió (no el vuelto: el vuelto en pesos se deriva
        // de dolaresRecibidos × cotización − montoFinal), así se sabe cuántos dólares tienen
        // que contar al cerrar la caja.
        BigDecimal cotizacionDolar = request.getCotizacionDolar();
        BigDecimal dolaresRecibidos = null;
        if (cotizacionDolar != null) {
            if (request.getFormaPago() != FormaPago.EFECTIVO_BOLETERIA) {
                throw new IllegalArgumentException("El pago en dólares sólo está disponible cobrando en efectivo");
            }
            if (cotizacionDolar.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("La cotización del dólar tiene que ser mayor a cero");
            }
            dolaresRecibidos = request.getDolaresRecibidos();
            if (dolaresRecibidos == null || dolaresRecibidos.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Indicá cuántos dólares entregó el cliente");
            }
            if (dolaresRecibidos.multiply(cotizacionDolar).compareTo(montoFinal) < 0) {
                throw new IllegalArgumentException("Los dólares recibidos no alcanzan para cubrir el total");
            }
        }

        // Venta anónima: no hay cliente ni contacto que cargar (ya están entrando, no hay
        // nada que validar después ni comprobante que mandar). Nace VENDIDO_EN_PUERTA porque
        // el cobro y el ingreso pasan en el mismo acto y nunca fue una reserva anticipada.
        Compra venta = Compra.builder()
                .cliente(null)
                .fechaVisita(hoy)
                .codigoReserva(generarCodigoReserva(hoy))
                .montoTotal(montoFinal)
                .descuentoAplicado(descuento)
                .detalles(todosLosDetalles)
                .estado(EstadoCompra.VENDIDO_EN_PUERTA)
                .formaPago(request.getFormaPago())
                .usuarioValidador(vendedor)
                .fechaValidacion(momentoVenta)
                .caja(caja)
                .promocion(promocionUsada)
                .cotizacionDolar(cotizacionDolar)
                .dolaresRecibidos(dolaresRecibidos)
                .idempotencyKey(idempotencyKey)
                .build();

        for (CompraDetalle det : todosLosDetalles) {
            det.setCompra(venta);
        }

        return compraRepository.save(venta);
    }

    @Transactional
    @Override
    public Compra actualizarContacto(Long compraId, EditarContactoRequest request) {
        Compra compra = compraRepository.findById(compraId)
                .orElseThrow(() -> new IllegalArgumentException("Compra no encontrada ID: " + compraId));

        if (compra.getCliente() != null) {
            Cliente cliente = compra.getCliente();
            String dniNuevo = esBlanco(request.getDni()) ? null : request.getDni().trim();
            if (dniNuevo != null && !dniNuevo.equals(cliente.getDni())) {
                // El Cliente se comparte entre compras (se reutiliza por similitud de nombre al
                // comprar, ver create()): pisar el DNI ahí afectaría también cualquier otra
                // compra que apunte al mismo registro. Se crea uno propio de ESTA compra en vez
                // de tocar el compartido.
                cliente = clienteService.create(Cliente.builder()
                        .dni(dniNuevo)
                        .nombre(!esBlanco(request.getNombre()) ? request.getNombre() : cliente.getNombre())
                        .apellido(!esBlanco(request.getApellido()) ? request.getApellido() : cliente.getApellido())
                        .edad(cliente.getEdad())
                        .localidad(cliente.getLocalidad())
                        .build());
                compra.setCliente(cliente);
            } else {
                if (!esBlanco(request.getNombre())) cliente.setNombre(request.getNombre());
                if (!esBlanco(request.getApellido())) cliente.setApellido(request.getApellido());
            }
        }
        if (!esBlanco(request.getEmail())) compra.setContactEmail(request.getEmail());
        if (!esBlanco(request.getTelefono())) compra.setContactPhone(request.getTelefono());

        // El DNI de quien recibe un regalo vive directo en la Compra, no en un Cliente
        // compartido: no hace falta ningún desacople, se pisa nomás.
        if (compra.getFechaVisita() == null && !esBlanco(request.getReceptorDni())) {
            compra.setReceptorDni(request.getReceptorDni().trim());
        }

        return compraRepository.save(compra);
    }

    @Override
    public void reenviarComprobante(Long compraId) {
        Compra compra = compraRepository.findById(compraId)
                .orElseThrow(() -> new IllegalArgumentException("Compra no encontrada ID: " + compraId));

        if (compra.getEstado() != EstadoCompra.APROBADO && compra.getEstado() != EstadoCompra.USADO) {
            throw new IllegalStateException("La compra ID " + compraId + " todavía no tiene un comprobante confirmado para reenviar.");
        }
        emailService.enviarComprobanteCompra(compraId);
        if (compra.getFechaVisita() == null) {
            emailService.enviarAvisoRegalo(compraId);
        }
    }

    @Transactional
    @Override
    public boolean confirmarAprobado(Long compraId) {
        Compra compra = compraRepository.findById(compraId).orElse(null);
        // Idempotente: si ya está aprobada o usada no hay nada que hacer (evita
        // reprocesar y reenviar el comprobante si Mercado Pago reintenta el aviso,
        // o si el webhook y la verificación directa llegan casi al mismo tiempo).
        if (compra == null || compra.getEstado() == EstadoCompra.APROBADO || compra.getEstado() == EstadoCompra.USADO) {
            return false;
        }
        compra.setEstado(EstadoCompra.APROBADO);
        compraRepository.save(compra);
        emailService.enviarComprobanteCompra(compraId);
        if (compra.getFechaVisita() == null) {
            emailService.enviarAvisoRegalo(compraId);
        }
        return true;
    }

    @Override
    public String verificarPagoDirecto(Long compraId) {
        Compra compra = compraRepository.findById(compraId)
                .orElseThrow(() -> new IllegalArgumentException("Compra no encontrada ID: " + compraId));

        // Si ya se sabe el resultado (por webhook o una verificación previa) no hace
        // falta volver a preguntarle a Mercado Pago.
        if (compra.getEstado() != EstadoCompra.PENDIENTE_PAGO || compra.getFormaPago() != FormaPago.MERCADO_PAGO) {
            return compra.getEstado().name();
        }

        try {
            // limit/offset van explícitos: si se dejan sin setear, el SDK arma la URL
            // iterando todos los parámetros y revienta con NullPointerException al
            // encontrar esos dos en null (bug conocido de esta versión del SDK).
            MPSearchRequest searchRequest = MPSearchRequest.builder()
                    .filters(Map.of("external_reference", compraId.toString()))
                    .limit(10)
                    .offset(0)
                    .build();
            List<Payment> pagos = new PaymentClient().search(searchRequest).getResults();
            // El external_reference identifica la compra, pero no es una clave 100% exclusiva
            // de Mercado Pago (por ejemplo, en un entorno de pruebas donde la base se reinicia
            // y los IDs se reciclan, puede haber un pago viejo con el mismo external_reference).
            // Exigir que el monto coincida evita aprobar una compra por un pago que en realidad
            // es de otra.
            boolean hayAprobado = pagos != null && pagos.stream()
                    .anyMatch(p -> "approved".equals(p.getStatus()) && compra.getMontoTotal().compareTo(p.getTransactionAmount()) == 0);
            if (hayAprobado) {
                confirmarAprobado(compraId);
            }
        } catch (MPException | MPApiException | RuntimeException e) {
            // No se pudo consultar a Mercado Pago ahora: se deja la compra como está
            // para poder reintentar más tarde (webhook, otra verificación, etc.).
            log.error("Error consultando a Mercado Pago el estado de la compra ID {}", compraId, e);
        }

        return compraRepository.findById(compraId).map(c -> c.getEstado().name()).orElse(compra.getEstado().name());
    }

    @Transactional
    @Override
    public Compra reembolsarCompra(Long compraId) {
        Compra compra = compraRepository.findById(compraId)
                .orElseThrow(() -> new IllegalArgumentException("Compra no encontrada ID: " + compraId));

        // Sólo se reembolsa lo pagado online y todavía no usado: APROBADO es el único
        // estado que cumple ambas cosas (el efectivo nunca llega a APROBADO, pasa
        // directo a USADO al cobrarse en boletería).
        if (compra.getEstado() != EstadoCompra.APROBADO) {
            throw new IllegalStateException(
                    "Sólo se pueden reembolsar compras pagadas online que todavía no fueron utilizadas (estado actual: " + compra.getEstado() + ")");
        }

        try {
            MPSearchRequest searchRequest = MPSearchRequest.builder()
                    .filters(Map.of("external_reference", compraId.toString()))
                    .limit(10)
                    .offset(0)
                    .build();
            List<Payment> pagos = new PaymentClient().search(searchRequest).getResults();
            Payment pagoAprobado = pagos == null ? null : pagos.stream()
                    .filter(p -> "approved".equals(p.getStatus()) && compra.getMontoTotal().compareTo(p.getTransactionAmount()) == 0)
                    .findFirst()
                    .orElse(null);
            if (pagoAprobado == null) {
                throw new IllegalStateException("No se encontró en Mercado Pago el pago aprobado de la compra ID " + compraId);
            }
            new PaymentRefundClient().refund(pagoAprobado.getId());
        } catch (MPApiException e) {
            // El mensaje de MPApiException no trae el detalle; hay que sacarlo de la
            // respuesta cruda para no tener que reproducir el request a mano cada vez.
            log.error("Error al reembolsar en Mercado Pago la compra ID {} (HTTP {}): {}",
                    compraId, e.getStatusCode(), e.getApiResponse() != null ? e.getApiResponse().getContent() : "sin detalle", e);
            throw new IllegalStateException("No se pudo procesar el reembolso en Mercado Pago. Reintentá en unos minutos.");
        } catch (MPException e) {
            log.error("Error al reembolsar en Mercado Pago la compra ID {}", compraId, e);
            throw new IllegalStateException("No se pudo procesar el reembolso en Mercado Pago. Reintentá en unos minutos.");
        }

        compra.setEstado(EstadoCompra.REEMBOLSADA);
        return compraRepository.save(compra);
    }

    @Transactional
    @Override
    public Compra cancelarVenta(Long compraId) {
        Compra compra = compraRepository.findById(compraId)
                .orElseThrow(() -> new IllegalArgumentException("Compra no encontrada ID: " + compraId));
        if (compra.getCaja() == null) {
            throw new IllegalArgumentException("Esta compra no pasó por una caja: no se cancela desde acá.");
        }
        if (compra.getEstado() == EstadoCompra.CANCELADO) {
            throw new IllegalStateException("Esta venta ya está cancelada.");
        }
        compra.setEstado(EstadoCompra.CANCELADO);
        return compraRepository.save(compra);
    }

    @Transactional
    @Override
    public Compra editarVenta(Long compraId, EditarVentaRequestDTO request) {
        Compra compra = compraRepository.findById(compraId)
                .orElseThrow(() -> new IllegalArgumentException("Compra no encontrada ID: " + compraId));
        if (compra.getCaja() == null) {
            throw new IllegalArgumentException("Esta compra no pasó por una caja: no se edita desde acá.");
        }
        if (compra.getEstado() == EstadoCompra.CANCELADO) {
            throw new IllegalStateException("Esta venta está cancelada: no se puede editar, hay que cargarla de nuevo.");
        }
        if (request.getFormaPago() == null) {
            throw new IllegalArgumentException("Falta indicar la forma de pago.");
        }

        LocalDate fechaVisita = compra.getFechaVisita();
        // Cupo diario: se cuenta contra todas las compras del día MENOS esta misma (se está
        // reemplazando lo que ya tenía, no sumando encima — ver construirDetalles, mismo criterio).
        Map<Long, Integer> cantidadVendidaPorTipo = new HashMap<>();
        if (fechaVisita != null) {
            for (Compra otra : compraRepository.findAllByFechaVisitaOrderByCodigoReservaAsc(fechaVisita)) {
                if (otra.getId().equals(compraId) || otra.getEstado() == EstadoCompra.CANCELADO || otra.getDetalles() == null) continue;
                for (CompraDetalle det : otra.getDetalles()) {
                    if (det.getTipoEntrada() == null) continue;
                    cantidadVendidaPorTipo.merge(det.getTipoEntrada().getId(), det.getCantidad(), Integer::sum);
                }
            }
        }

        BigDecimal montoEntradas = BigDecimal.ZERO;
        List<CompraDetalle> nuevasEntradas = new ArrayList<>();
        if (request.getEntradas() != null) {
            for (DetalleCompraDTO d : request.getEntradas()) {
                if (d == null || d.getTipoEntradaId() == null || d.getCantidad() == null || d.getCantidad() <= 0) continue;
                TipoEntrada tipoEntrada = tipoEntradaService.findById(d.getTipoEntradaId())
                        .orElseThrow(() -> new IllegalArgumentException("TipoEntrada no encontrada para id: " + d.getTipoEntradaId()));
                if (tipoEntrada.getMaximoPorDia() != null) {
                    int nuevoTotal = cantidadVendidaPorTipo.merge(d.getTipoEntradaId(), d.getCantidad(), Integer::sum);
                    if (nuevoTotal > tipoEntrada.getMaximoPorDia()) {
                        throw new IllegalArgumentException("Se alcanzó el cupo diario de " + tipoEntrada.getNombre()
                                + " (máximo " + tipoEntrada.getMaximoPorDia() + " por día).");
                    }
                }
                montoEntradas = montoEntradas.add(calculoPrecioService.calcularTotal(tipoEntrada, d.getCantidad(), request.getFormaPago()));
                nuevasEntradas.add(CompraDetalle.builder().tipoEntrada(tipoEntrada).cantidad(d.getCantidad()).compra(compra).build());
            }
        }

        // Los artículos varios (souvenirs) no se tocan acá: si el error está ahí, se cancela la
        // venta entera y se vuelve a cargar (ver EditarVentaRequestDTO).
        List<CompraDetalle> detallesArticulos = compra.getDetalles().stream()
                .filter(det -> det.getTipoEntrada() == null)
                .toList();
        BigDecimal montoArticulos = detallesArticulos.stream()
                .map(det -> det.getPrecioUnitario() != null ? det.getPrecioUnitario().multiply(BigDecimal.valueOf(det.getCantidad())) : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<CompraDetalle> todosLosDetalles = new ArrayList<>(nuevasEntradas);
        todosLosDetalles.addAll(detallesArticulos);
        if (todosLosDetalles.isEmpty()) {
            throw new IllegalArgumentException("La venta no puede quedar sin entradas ni artículos.");
        }
        validarPaseObligatorio(todosLosDetalles);

        BigDecimal montoBruto = montoEntradas.add(montoArticulos);
        // El descuento ya aplicado (promo o manual) se mantiene como monto fijo: no se
        // vuelve a evaluar elegibilidad de promo acá, sólo se lo re-acota si el nuevo bruto
        // quedó más chico que el descuento original.
        BigDecimal descuento = compra.getDescuentoAplicado() != null ? compra.getDescuentoAplicado() : BigDecimal.ZERO;
        if (descuento.compareTo(montoBruto) > 0) descuento = montoBruto;
        BigDecimal montoFinal = montoBruto.subtract(descuento);

        if (compra.getCotizacionDolar() != null) {
            if (request.getFormaPago() != FormaPago.EFECTIVO_BOLETERIA) {
                // Ya no tiene sentido seguir marcada como cobrada en dólares si deja de ser efectivo.
                compra.setCotizacionDolar(null);
                compra.setDolaresRecibidos(null);
            } else if (compra.getDolaresRecibidos().multiply(compra.getCotizacionDolar()).compareTo(montoFinal) < 0) {
                throw new IllegalArgumentException(
                        "Los dólares que había recibido el cajero ya no alcanzan para cubrir el nuevo total: cancelá esta venta y cargala de nuevo.");
            }
        }

        // orphanRemoval en Compra.detalles: sacar las líneas de entrada viejas de la colección
        // alcanza para que Hibernate las borre; las de artículo quedan intactas.
        compra.getDetalles().removeIf(det -> det.getTipoEntrada() != null);
        compra.getDetalles().addAll(nuevasEntradas);
        compra.setFormaPago(request.getFormaPago());
        compra.setMontoTotal(montoFinal);
        compra.setDescuentoAplicado(descuento);

        return compraRepository.save(compra);
    }
}
