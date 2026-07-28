package org.example.laserranitaentradas.service.impl;

import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.net.MPSearchRequest;
import com.mercadopago.resources.payment.Payment;
import jakarta.transaction.Transactional;
import org.example.laserranitaentradas.model.dto.*;
import org.example.laserranitaentradas.model.entity.Cliente;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.CompraDetalle;
import org.example.laserranitaentradas.model.entity.Cupon;
import org.example.laserranitaentradas.model.entity.FormaPago;
import org.example.laserranitaentradas.model.entity.Tipo;
import org.example.laserranitaentradas.model.entity.TipoEntrada;
import org.example.laserranitaentradas.model.entity.Usuario;
import org.example.laserranitaentradas.model.entity.EstadoCompra;
import org.example.laserranitaentradas.repository.CompraRepository;
import org.example.laserranitaentradas.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
    private final Map<FormaPago, PagoService> estrategiasPago;

    public CompraServiceImpl
            (CompraRepository compraRepository,
             TipoEntradaService tipoEntradaService,
             CuponService cuponService,
             DiaAperturaService diaAperturaService,
             ClienteService clienteService,
             UsuarioService usuarioService,
             CalculoPrecioService calculoPrecioService,
             EmailService emailService,
             List<PagoService> estrategiasDisponibles)
    {
        this.compraRepository = compraRepository;
        this.tipoEntradaService = tipoEntradaService;
        this.cuponService = cuponService;
        this.diaAperturaService = diaAperturaService;
        this.clienteService = clienteService;
        this.usuarioService = usuarioService;
        this.calculoPrecioService = calculoPrecioService;
        this.emailService = emailService;
        this.estrategiasPago = estrategiasDisponibles.stream()
                .collect(Collectors.toMap(PagoService::getFormaPago, estrategia -> estrategia));
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
        if (clienteDTO != null && clienteDTO.getDni() != null) {
            String dniStr = String.valueOf(clienteDTO.getDni());
            cliente = clienteService.findByDni(dniStr).orElse(null);
            if (cliente == null) {
                Cliente nuevo = Cliente.builder()
                        .dni(dniStr)
                        .nombre(clienteDTO.getNombre())
                        .apellido(clienteDTO.getApellido())
                        .edad(clienteDTO.getEdad())
                        .localidad(clienteDTO.getLocalidad())
                        .build();
                cliente = clienteService.create(nuevo);
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

        // Cupo diario por tipo de entrada: se suma lo ya vendido ese día (sin contar lo cancelado)
        // más lo que se está agregando en esta misma compra. Los regalos no tienen fecha todavía,
        // así que no hay contra qué día chequear el cupo.
        Map<Long, Integer> cantidadVendidaPorTipo = new HashMap<>();
        if (fechaVisita != null) {
            for (Compra otra : compraRepository.findAllByFechaVisitaOrderByCodigoReservaAsc(fechaVisita)) {
                if (otra.getEstado() == EstadoCompra.CANCELADO || otra.getDetalles() == null) continue;
                for (CompraDetalle det : otra.getDetalles()) {
                    cantidadVendidaPorTipo.merge(det.getTipoEntrada().getId(), det.getCantidad(), Integer::sum);
                }
            }
        }

        BigDecimal montoTotal = BigDecimal.ZERO;
        List<CompraDetalle> detalles = new ArrayList<>();

        if (compraRequest.getEntradas() != null) {
            for (DetalleCompraDTO d : compraRequest.getEntradas()) {
                if (d == null || d.getTipoEntradaId() == null || d.getCantidad() == null) continue;

                Long tipoId = d.getTipoEntradaId();
                Optional<TipoEntrada> tipoOpt = tipoEntradaService.findById(tipoId);
                if (tipoOpt.isEmpty()) {
                    throw new IllegalArgumentException("TipoEntrada no encontrada para id: " + tipoId);
                }
                TipoEntrada tipoEntrada = tipoOpt.get();

                if (tipoEntrada.getMaximoPorDia() != null) {
                    int nuevoTotal = cantidadVendidaPorTipo.merge(tipoId, d.getCantidad(), Integer::sum);
                    if (nuevoTotal > tipoEntrada.getMaximoPorDia()) {
                        throw new IllegalArgumentException(
                                "Se alcanzó el cupo diario de " + tipoEntrada.getNombre() + " para el " + fechaVisita
                                        + " (máximo " + tipoEntrada.getMaximoPorDia() + " por día).");
                    }
                }

                BigDecimal subtotal = calculoPrecioService.calcularTotal(tipoEntrada, d.getCantidad(), compraRequest.getFormaPago());
                montoTotal = montoTotal.add(subtotal);

                CompraDetalle detalle = CompraDetalle.builder()
                        .tipoEntrada(tipoEntrada)
                        .cantidad(d.getCantidad())
                        .build();
                detalles.add(detalle);
            }
        }

        boolean hayEntradas = detalles.stream()
                .anyMatch(d -> d.getTipoEntrada().getTipo() == Tipo.ENTRADA);
        boolean hayObligatorio = detalles.stream()
                .anyMatch(d -> Boolean.TRUE.equals(d.getTipoEntrada().getObligatorio()) && d.getCantidad() > 0);
        if (hayEntradas && !hayObligatorio) {
            throw new IllegalArgumentException(
                    "La compra debe incluir al menos un pase de un tipo obligatorio (por ejemplo, un adulto responsable) para poder ingresar al parque."
            );
        }

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

        // Código visible yyMMdd-N: N es el orden de esta reserva entre todas las
        // que ya existen para ese mismo día de visita (se asigna una sola vez, al crear).
        // Para regalos (sin fecha) se usa un contador propio: REGALO-N.
        String codigoReserva;
        if (fechaVisita != null) {
            long numeroDelDia = compraRepository.countByFechaVisita(fechaVisita) + 1;
            codigoReserva = fechaVisita.format(DateTimeFormatter.ofPattern("yyMMdd")) + "-" + numeroDelDia;
        } else {
            long numeroRegalo = compraRepository.countByFechaVisitaIsNull() + 1;
            codigoReserva = "REGALO-" + numeroRegalo;
        }

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
    public List<Compra> getAllByDni(String dni) {
        return compraRepository.findAllByClienteDni(dni);
    }

    @Override
    public List<Compra> getAllByFechaVisita(LocalDate fechaVisita) {
        return compraRepository.findAllByFechaVisitaOrderByCodigoReservaAsc(fechaVisita);
    }

    @Override
    public List<Compra> getAll() {
        return compraRepository.findAllByOrderByFechaVisitaAscCodigoReservaAsc();
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

        return marcarEntradasComoUsadas(compraId, usuarioValidadorId);
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

        CotizacionResponseDTO dto = new CotizacionResponseDTO();
        dto.setSubtotal(subtotal);
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

    @Transactional
    @Override
    public Compra actualizarContacto(Long compraId, EditarContactoRequest request) {
        Compra compra = compraRepository.findById(compraId)
                .orElseThrow(() -> new IllegalArgumentException("Compra no encontrada ID: " + compraId));

        if (compra.getCliente() != null) {
            if (!esBlanco(request.getNombre())) compra.getCliente().setNombre(request.getNombre());
            if (!esBlanco(request.getApellido())) compra.getCliente().setApellido(request.getApellido());
        }
        if (!esBlanco(request.getEmail())) compra.setContactEmail(request.getEmail());
        if (!esBlanco(request.getTelefono())) compra.setContactPhone(request.getTelefono());

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
}
