package org.example.laserranitaentradas.service.impl;

import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.example.laserranitaentradas.monitoreo.AuditoriaContexto;

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
import org.example.laserranitaentradas.model.entity.ConfirmacionPago;
import org.example.laserranitaentradas.model.entity.MotivoCancelacion;
import org.example.laserranitaentradas.config.UsuarioAutenticado;
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
import org.example.laserranitaentradas.repository.CajaRepository;
import org.example.laserranitaentradas.repository.CompraRepository;
import org.example.laserranitaentradas.repository.CompraSpecifications;
import org.example.laserranitaentradas.repository.PromocionRepository;
import org.example.laserranitaentradas.service.*;
import org.example.laserranitaentradas.service.point.CobroPointService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CompraServiceImpl implements CompraService {

    private static final Logger log = LoggerFactory.getLogger(CompraServiceImpl.class);
    /**
     * Problemas con la plata de un cliente (pagó de más, pago sobre una reembolsada): van a Sistema >
     * Errores, área Pagos.
     */
    private static final Logger alertasPagos = LoggerFactory.getLogger("alertas.pagos");

    /**
     * Ventana para reusar un checkout sin pagar del mismo cliente. Pasado esto, es más probable que sea otra
     * compra.
     */
    static final int MINUTOS_REUSO_CHECKOUT = 30;

    /** Quién figura como cancelada_por cuando la cancela la app sola (mismo nombre que AuditConfig). */
    private static final String USUARIO_SISTEMA = "SISTEMA";

    private final CompraRepository compraRepository;
    private final TipoEntradaService tipoEntradaService;
    private final CuponService cuponService;
    private final DiaAperturaService diaAperturaService;
    private final ClienteService clienteService;
    private final UsuarioService usuarioService;
    private final CalculoPrecioService calculoPrecioService;
    private final EmailService emailService;
    private final CajaService cajaService;
    private final CajaRepository cajaRepository;
    private final PromocionRepository promocionRepository;
    private final ArticuloVarioRepository articuloVarioRepository;
    private final FacturaService facturaService;
    private final Map<FormaPago, PagoService> estrategiasPago;
    private final EntityManager em;
    /**
     * El propio bean a través del proxy, para que valga @Transactional entre métodos de esta clase. @Lazy
     * corta el ciclo.
     */
    private final CompraService self;
    private final CobroPointService cobroPointService;

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
             CajaRepository cajaRepository,
             PromocionRepository promocionRepository,
             ArticuloVarioRepository articuloVarioRepository,
             FacturaService facturaService,
             List<PagoService> estrategiasDisponibles,
             EntityManager em,
             @Lazy CompraService self,
             CobroPointService cobroPointService)
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
        this.cajaRepository = cajaRepository;
        this.promocionRepository = promocionRepository;
        this.articuloVarioRepository = articuloVarioRepository;
        this.facturaService = facturaService;
        this.estrategiasPago = estrategiasDisponibles.stream()
                .collect(Collectors.toMap(PagoService::getFormaPago, estrategia -> estrategia));
        this.em = em;
        this.self = self;
        this.cobroPointService = cobroPointService;
    }

    /**
     * Descuento de venta en puerta: promo del catálogo o descuento manual (% o $), excluyentes. Sólo se
     * guarda el monto.
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

    /**
     * Valida el cobro en dólares y devuelve los dólares recibidos, o null si se pagó en pesos. Sigue siendo
     * EFECTIVO_BOLETERIA; el vuelto se deriva.
     */
    private BigDecimal validarCobroDolares(VentaPosRequestDTO request, BigDecimal montoFinal) {
        BigDecimal cotizacionDolar = request.getCotizacionDolar();
        if (cotizacionDolar == null) return null;
        if (request.getFormaPago() != FormaPago.EFECTIVO_BOLETERIA) {
            throw new IllegalArgumentException("El pago en dólares sólo está disponible cobrando en efectivo");
        }
        if (cotizacionDolar.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("La cotización del dólar tiene que ser mayor a cero");
        }
        BigDecimal dolaresRecibidos = request.getDolaresRecibidos();
        if (dolaresRecibidos == null || dolaresRecibidos.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Indicá cuántos dólares entregó el cliente");
        }
        if (dolaresRecibidos.multiply(cotizacionDolar).compareTo(montoFinal) < 0) {
            throw new IllegalArgumentException("Los dólares recibidos no alcanzan para cubrir el total");
        }
        return dolaresRecibidos;
    }

    /** SIN_COBRO sólo si el total es $0. */
    private void validarFormaPagoPos(FormaPago formaPago, BigDecimal montoFinal) {
        if (formaPago == FormaPago.SIN_COBRO && montoFinal.compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalArgumentException("Esta venta tiene un monto a cobrar: elegí efectivo, tarjeta o QR.");
        }
    }

    /** Formas que se pueden combinar en un pago mixto. */
    private static final Set<FormaPago> FORMAS_PAGO_MIXTO = Set.of(
            FormaPago.EFECTIVO_BOLETERIA, FormaPago.TARJETA, FormaPago.MERCADO_PAGO_QR);

    /**
     * Pago mixto: dos formas presenciales distintas (salvo dos tarjetas), secundaria positiva y menor al
     * total, y sin dólares.
     */
    private void validarPagoMixto(VentaPosRequestDTO request, BigDecimal montoFinal) {
        FormaPago secundaria = request.getFormaPagoSecundaria();
        if (secundaria == null) return;
        if (request.getCotizacionDolar() != null) {
            throw new IllegalArgumentException("No se puede combinar pago mixto con pago en dólares");
        }
        if (!FORMAS_PAGO_MIXTO.contains(request.getFormaPago()) || !FORMAS_PAGO_MIXTO.contains(secundaria)) {
            throw new IllegalArgumentException("El pago mixto sólo admite efectivo, tarjeta o QR");
        }
        if (secundaria == request.getFormaPago() && secundaria != FormaPago.TARJETA) {
            throw new IllegalArgumentException("Las dos formas de pago tienen que ser distintas");
        }
        BigDecimal monto = request.getMontoFormaPagoSecundaria();
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0 || monto.compareTo(montoFinal) >= 0) {
            throw new IllegalArgumentException("El monto de la segunda forma de pago tiene que ser mayor a cero y menor al total");
        }
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

    /**
     * Sin @Transactional a propósito: la llamada a Mercado Pago no puede quedar dentro, porque retiene la
     * conexión y el lock de numeración del código de reserva. Si MP falla, la compra queda PENDIENTE_PAGO y
     * la cancela el barrido.
     *
     * `self.create`, no `this.create`: con `this` no pasa por el proxy y create queda sin transacción.
     */
    @Override
    public CompraResponseDTO iniciarCompraConPago(CompraRequestDTO compraRequest) throws Exception {

        Compra compraGuardada = self.create(compraRequest);
        if (compraGuardada.isReusada()) {
            verificarSinPagoEnCurso(compraGuardada);
        }

        PagoService estrategia = resolverEstrategia(compraGuardada.getFormaPago());
        PagoResponseDTO respuestaPago;
        try {
            respuestaPago = estrategia.procesarPago(compraGuardada);
        } catch (Exception e) {
            if (compraGuardada.isReusada()) {
                // Reusada: el cliente puede estar pagando con el link anterior, no se cancela.
                log.warn("No se pudo generar un link nuevo para el checkout reusado {} (compra ID {}): queda pendiente",
                        compraGuardada.getCodigoReserva(), compraGuardada.getId(), e);
                throw e;
            }
            // Se liberan cupón y lugar sin esperar al barrido. Aunque MP haya creado la preferencia, el
            // cliente nunca recibe el link; y si igual paga, confirmarAprobado revive la compra. Si se saca
            // esa reactivación, sacar también esto.
            liberarReservaNoIniciada(compraGuardada.getId(), e);
            throw e;
        }

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

        return dto;
    }


    /**
     * Antes de dar un link nuevo a un checkout reusado, se fija si ya pagó o tiene un pago en proceso, para
     * que no pague dos veces. Si no se puede consultar a MP, sigue con el link nuevo.
     */
    private void verificarSinPagoEnCurso(Compra compra) {
        List<Payment> pagos;
        try {
            pagos = buscarPagosEnMercadoPago(compra).stream()
                    .filter(p -> esPosteriorALaCompra(p, compra))
                    .toList();
        } catch (MPException | MPApiException | RuntimeException e) {
            log.warn("No se pudo ver en Mercado Pago si el checkout reusado {} ya tiene un pago: se sigue con un link nuevo",
                    compra.getCodigoReserva(), e);
            return;
        }
        Optional<List<Long>> cubren = pagosQueCubren(compra, pagos);
        if (cubren.isPresent()) {
            self.confirmarAprobado(compra.getId(), cubren.get(), ConfirmacionPago.VERIFICACION);
            throw new IllegalStateException("El pago de tu reserva #" + compra.getCodigoReserva()
                    + " ya se acreditó: no hace falta pagar de nuevo. Te mandamos la confirmación por mail.");
        }
        if (pagos.stream().anyMatch(p -> PAGO_EN_PROCESO.contains(p.getStatus()))) {
            throw new IllegalStateException("Ya hay un pago en proceso para tu reserva #" + compra.getCodigoReserva()
                    + ". Esperá unos minutos a que Mercado Pago lo confirme (te llega por mail) antes de volver a pagar.");
        }
    }

    /** Estados de un pago de Mercado Pago que todavía pueden terminar aprobados. */
    private static final Set<String> PAGO_EN_PROCESO = Set.of("pending", "in_process", "authorized");

    /**
     * Cancela una compra cuyo pago nunca se inició y libera cupón y lugar. Si falla no se propaga: importa el
     * error original y el barrido la limpia después.
     */
    private void liberarReservaNoIniciada(Long compraId, Exception causa) {
        try {
            Compra compra = compraRepository.findById(compraId).orElse(null);
            if (compra == null || compra.getEstado() != EstadoCompra.PENDIENTE_PAGO) {
                return;
            }
            compra.cancelar(MotivoCancelacion.PAGO_NO_INICIADO, recortarDetalle(describirError(causa)), USUARIO_SISTEMA);
            liberarCupon(compra);
            compraRepository.save(compra);
            log.info("Compra ID {} cancelada: no se pudo iniciar el pago", compraId);
        } catch (RuntimeException e) {
            log.error("No se pudo cancelar la compra ID {} tras fallar el inicio del pago; "
                    + "queda para el barrido de checkouts abandonados", compraId, e);
        }
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
            // La compra online para HOY se corta antes del cierre. RESERVA_ADMIN queda afuera.
            if (compraRequest.getFormaPago() != FormaPago.RESERVA_ADMIN
                    && diaAperturaService.compraDelDiaCerrada(fechaVisita, LocalDateTime.now())) {
                LocalDateTime limite = diaAperturaService.getLimiteDeCompra(fechaVisita);
                throw new IllegalArgumentException("La compra online para hoy ya cerró: se corta a las "
                        + limite.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
                        + " hs. Podés elegir otro día.");
            }
        } else {
            // fechaVisita null = sin día fijo:
            // - Regalo: hace falta el receptor y tiene que llegar pagado, nunca en efectivo.
            // - Reserva abierta de un ADMIN: entra el titular, sin receptor.
            if (compraRequest.getFormaPago() == FormaPago.EFECTIVO_BOLETERIA) {
                throw new IllegalArgumentException("Los regalos sólo se pueden pagar online: no se puede reservar en efectivo.");
            }
            ReceptorRegaloDTO receptor = compraRequest.getReceptor();
            boolean receptorCompleto = receptor != null && !esBlanco(receptor.getNombre())
                    && !esBlanco(receptor.getEmail()) && !esBlanco(receptor.getDni());
            if (receptorCompleto) {
                receptorNombre = receptor.getNombre();
                receptorEmail = receptor.getEmail();
                receptorDni = receptor.getDni();
                receptorTelefono = receptor.getTelefono();
            } else if (compraRequest.getFormaPago() != FormaPago.RESERVA_ADMIN) {
                throw new IllegalArgumentException("Para comprar como regalo hay que indicar nombre, DNI y email de quien lo recibe.");
            } else if (clienteDTO == null || esBlanco(clienteDTO.getNombre()) || esBlanco(clienteDTO.getDni())) {
                // Cliente.nombre es NOT NULL: sin esto se creaba un cliente sin nombre.
                throw new IllegalArgumentException("Indicá el titular (nombre y DNI) para una reserva sin fecha.");
            }
        }


        Cliente cliente = null;
        if (clienteDTO != null && !esBlanco(clienteDTO.getDni())) {
            String dniStr = clienteDTO.getDni().trim();
            String nombreTipeado = normalizarNombre(clienteDTO.getNombre(), clienteDTO.getApellido());

            // Puede haber varios clientes con el mismo DNI: se elige el de nombre más parecido.
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
                // Nadie con este DNI, nombre muy distinto o el usuario aclaró que es otra persona: se crea un
                // cliente nuevo con el mismo DNI.
                cliente = clienteService.create(Cliente.builder()
                        .dni(dniStr)
                        .nombre(clienteDTO.getNombre())
                        .apellido(clienteDTO.getApellido())
                        .edad(clienteDTO.getEdad())
                        .localidad(clienteDTO.getLocalidad())
                        .build());
            } else if (mejorSimilitud >= 1.0 || Boolean.TRUE.equals(compraRequest.getConfirmarDniExistente())) {
                // Nombre idéntico, o ya confirmó que es la misma persona.
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
                // Nombre parecido pero no idéntico: el front pide confirmación.
                throw new IllegalArgumentException("DNI_YA_REGISTRADO: Ya hay una reserva registrada con el DNI "
                        + dniStr + " a nombre de \"" + mejorCandidato.getNombre()
                        + (mejorCandidato.getApellido() != null ? " " + mejorCandidato.getApellido() : "")
                        + "\". Si sos la misma persona, confirmá para continuar.");
            }
        }
        // Quien compra un regalo no da DNI, pero se guarda como cliente por el nombre. Se crea después de ver
        // si es un reintento, para no dejar clientes sueltos.
        boolean compradorDeRegaloSinDni = cliente == null && receptorNombre != null
                && clienteDTO != null && !esBlanco(clienteDTO.getNombre());

        String contactEmail = clienteDTO != null ? clienteDTO.getEmail() : null;
        String contactPhone = clienteDTO != null ? clienteDTO.getTelefono() : null;

        // Mismo cliente apretando "Pagar" con lo mismo mientras su checkout sigue sin pagar: se reusa la
        // compra. Cada compra nueva aparta cupo y un uso de cupón por 3 h. Va antes del cupón y del cupo
        // porque esa compra ya los tiene tomados.
        Compra checkoutAReusar = compraRequest.getFormaPago() == FormaPago.MERCADO_PAGO
                ? buscarCheckoutReutilizable(cliente, contactEmail, fechaVisita, receptorDni,
                        compraRequest.getCuponCodigo(), compraRequest.getEntradas()).orElse(null)
                : null;

        if (compradorDeRegaloSinDni) {
            // Sin DNI: en un reintento se reusa el cliente de la compra pendiente.
            Cliente previo = checkoutAReusar != null ? checkoutAReusar.getCliente() : null;
            if (previo != null && previo.getDni() == null) {
                previo.setNombre(clienteDTO.getNombre());
                previo.setApellido(clienteDTO.getApellido());
                cliente = previo;
            } else {
                cliente = clienteService.create(Cliente.builder()
                        .nombre(clienteDTO.getNombre())
                        .apellido(clienteDTO.getApellido())
                        .build());
            }
        }

        // Esta lectura sólo sirve para el mensaje de error (agotado, vencido, inexistente). Lo que autoriza
        // es cuponService.consumirUso, más abajo.
        Cupon cupon = null;
        if (compraRequest.getCuponCodigo() != null) {
            cupon = cuponService.getByCode(compraRequest.getCuponCodigo()).orElse(null);
            if (cupon != null) {
                LocalDate hoy = LocalDate.now();
                if (checkoutAReusar == null
                        && cupon.getUsosMaximos() != null && cupon.getUsosActuales() >= cupon.getUsosMaximos()) {
                    throw new IllegalArgumentException("Cupón ha alcanzado su límite de usos: " + compraRequest.getCuponCodigo());
                }
                if (!cupon.getActivo() || (cupon.getFechaExpiracion() != null && cupon.getFechaExpiracion().isBefore(hoy))) {
                    throw new IllegalArgumentException("Cupón no válido o expirado: " + compraRequest.getCuponCodigo());
                }
                if (cupon.getFechaDesde() != null && cupon.getFechaDesde().isAfter(hoy)) {
                    throw new IllegalArgumentException("El cupón " + compraRequest.getCuponCodigo()
                            + " recién se puede usar desde el " + cupon.getFechaDesde() + ".");
                }
            } else {
                throw new IllegalArgumentException("Cupón no encontrado para código: " + compraRequest.getCuponCodigo());
            }
        }

        DetallesCalculados calculo = construirDetalles(compraRequest.getEntradas(), compraRequest.getFormaPago(),
                fechaVisita, checkoutAReusar != null ? checkoutAReusar.getId() : null);
        List<CompraDetalle> detalles = calculo.detalles();
        BigDecimal montoTotal = calculo.montoTotal();

        validarPaseObligatorio(detalles);
        validarNingunoSoloPos(detalles);

        // Reintento: se reusa la compra pendiente si el total no cambió.
        if (checkoutAReusar != null) {
            BigDecimal brutoAnterior = checkoutAReusar.getMontoTotal().add(
                    checkoutAReusar.getDescuentoAplicado() != null ? checkoutAReusar.getDescuentoAplicado() : BigDecimal.ZERO);
            if (brutoAnterior.compareTo(montoTotal) == 0) {
                // Lo último que tipeó manda (pudo corregir el mail o el teléfono entre intentos).
                checkoutAReusar.setCliente(cliente);
                checkoutAReusar.setContactEmail(contactEmail);
                checkoutAReusar.setContactPhone(contactPhone);
                checkoutAReusar.setReceptorNombre(receptorNombre);
                checkoutAReusar.setReceptorEmail(receptorEmail);
                checkoutAReusar.setReceptorTelefono(receptorTelefono);
                log.info("Se reusa el checkout pendiente {} (compra ID {}) en vez de crear otra compra igual",
                        checkoutAReusar.getCodigoReserva(), checkoutAReusar.getId());
                checkoutAReusar.setReusada(true);
                return compraRepository.save(checkoutAReusar);
            }
            // Cambió el precio: compra nueva, y el cupo se vuelve a medir contando también la pendiente.
            calculo = construirDetalles(compraRequest.getEntradas(), compraRequest.getFormaPago(), fechaVisita);
            detalles = calculo.detalles();
            montoTotal = calculo.montoTotal();
        }

        BigDecimal descuentoAplicado = BigDecimal.ZERO;
        if (cupon != null) {
            CuponDescuentoCalculator.Resultado resultadoCupon = CuponDescuentoCalculator.calcular(cupon, calculo.lineasEntrada());
            if (resultadoCupon.motivoNoAplica() != null) {
                throw new IllegalArgumentException(resultadoCupon.motivoNoAplica());
            }
            descuentoAplicado = resultadoCupon.descuento();

            if (descuentoAplicado.compareTo(montoTotal) > 0) {
                descuentoAplicado = montoTotal;
            }

            // Mercado Pago no acepta $0: un cupón que cubre todo se rechaza antes de consumir el uso.
            if (compraRequest.getFormaPago() == FormaPago.MERCADO_PAGO
                    && montoTotal.subtract(descuentoAplicado).compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("El cupón " + compraRequest.getCuponCodigo()
                        + " cubre el total de la compra: no hay importe que pagar online. "
                        + "Usalo en la boletería del parque.");
            }

            // Valida y descuenta el uso en una sola sentencia; si otra compra se llevó el último, se rechaza.
            // Va último para no quemar un uso por una compra que no se crea.
            if (!cuponService.consumirUso(cupon.getId())) {
                throw new IllegalArgumentException(
                        "El cupón " + compraRequest.getCuponCodigo() + " ya no está disponible.");
            }

            // montoTotal es lo que se cobra: hay que restar el descuento.
            montoTotal = montoTotal.subtract(descuentoAplicado);

            // consumirUso ya incrementó usosActuales y apagó el cupón si se agotó.
        }

        String codigoReserva = generarCodigoReserva(fechaVisita, receptorNombre != null);

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

    /**
     * Checkout sin pagar reciente que sea la misma compra: misma persona, mismo día, mismo cupón y mismas
     * entradas. El total se chequea después.
     */
    private Optional<Compra> buscarCheckoutReutilizable(Cliente cliente, String contactEmail, LocalDate fechaVisita,
                                                       String receptorDni, String cuponCodigo,
                                                       List<DetalleCompraDTO> entradas) {
        String dni = cliente != null ? cliente.getDni() : null;
        Map<String, Integer> lineas = new HashMap<>();
        if (entradas != null) {
            for (DetalleCompraDTO e : entradas) {
                if (e.getTipoEntradaId() != null && e.getCantidad() != null && e.getCantidad() > 0) {
                    lineas.merge("T" + e.getTipoEntradaId(), e.getCantidad(), Integer::sum);
                }
            }
        }
        if (lineas.isEmpty()) return Optional.empty();
        return compraRepository.findCheckoutsPendientesDesde(LocalDateTime.now().minusMinutes(MINUTOS_REUSO_CHECKOUT))
                .stream()
                .filter(c -> esLaMismaPersona(c, dni, contactEmail))
                .filter(c -> java.util.Objects.equals(c.getFechaVisita(), fechaVisita))
                .filter(c -> java.util.Objects.equals(c.getReceptorDni(), receptorDni))
                .filter(c -> mismoCupon(c.getCupon(), cuponCodigo))
                .filter(c -> lineasDeCompra(c.getDetalles()).equals(lineas))
                .max(Comparator.comparing(Compra::getFechaCreacion));
    }

    /**
     * Si alguno tiene DNI manda el DNI (una familia puede compartir mail). El mail sólo cuando ninguno tiene
     * DNI.
     */
    private static boolean esLaMismaPersona(Compra compra, String dni, String contactEmail) {
        String dniCompra = compra.getCliente() != null ? compra.getCliente().getDni() : null;
        if (dni != null || dniCompra != null) return dni != null && dni.equals(dniCompra);
        return contactEmail != null && !contactEmail.isBlank() && contactEmail.trim().equalsIgnoreCase(
                compra.getContactEmail() != null ? compra.getContactEmail().trim() : null);
    }

    private static boolean mismoCupon(Cupon cupon, String cuponCodigo) {
        if (cupon == null) return cuponCodigo == null || cuponCodigo.isBlank();
        return cupon.getCodigo() != null && cupon.getCodigo().equalsIgnoreCase(cuponCodigo);
    }

    /** "Qué se lleva" de una compra, para comparar dos: cantidad por tipo de entrada o artículo. */
    private static Map<String, Integer> lineasDeCompra(List<CompraDetalle> detalles) {
        Map<String, Integer> lineas = new HashMap<>();
        if (detalles == null) return lineas;
        for (CompraDetalle d : detalles) {
            String clave = d.getTipoEntrada() != null ? "T" + d.getTipoEntrada().getId()
                    : d.getArticuloVario() != null ? "A" + d.getArticuloVario().getId()
                    : "L" + d.getDescripcionLibre();
            lineas.merge(clave, d.getCantidad() != null ? d.getCantidad() : 0, Integer::sum);
        }
        return lineas;
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
    public Optional<Compra> findByCodigoReserva(String codigoReserva) {
        return compraRepository.findByCodigoReserva(codigoReserva);
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

    /** El criterio de `buscar()` como Specification. */
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

        // JpaSpecificationExecutor no hace proyecciones: Criteria a mano con el mismo Specification.
        CriteriaBuilder cb = em.getCriteriaBuilder();

        // Sin regalos (fechaVisita null): tienen su propio bloque en Boletería.
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

        // Un regalo queda sin fechaVisita aun canjeado: la recaudación va a la fecha de compra y el ingreso a
        // la de validación.

        return compraRepository.save(compra);
    }

    /** Igual que Boleteria.VENTANA_DESHACER_MENU_MS en el front: mantenerlos iguales. */
    private static final long VENTANA_DESHACER_VALIDACION_SEGUNDOS = 2400;

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

        // Vuelve a RESERVADO_EFECTIVO si se cobró en la puerta (con caja, o efectivo de un admin sin caja), o
        // a APROBADO si estaba paga online.
        compra.setEstado(compra.getCaja() != null || compra.getFormaPago() == FormaPago.EFECTIVO_BOLETERIA
                ? EstadoCompra.RESERVADO_EFECTIVO
                : EstadoCompra.APROBADO);
        compra.setUsuarioValidador(null);
        compra.setFechaValidacion(null);
        compra.setCaja(null);

        return compraRepository.save(compra);
    }

    @Transactional
    @Override
    public Compra confirmarPagoEfectivo(Long compraId, Long usuarioValidadorId, boolean permitirSinCaja) {
        Compra compra = compraRepository.findById(compraId)
                .orElseThrow(() -> new IllegalArgumentException("Compra no encontrada ID: " + compraId));

        if (compra.getFormaPago() != FormaPago.EFECTIVO_BOLETERIA) {
            throw new IllegalStateException("La compra ID " + compraId + " no corresponde a una reserva con pago en efectivo");
        }
        if (compra.getEstado() != EstadoCompra.RESERVADO_EFECTIVO) {
            throw new IllegalStateException("La compra ID " + compraId + " no está pendiente de cobro en boletería (estado actual: " + compra.getEstado() + ")");
        }

        // Va a la caja abierta de quien cobra; un admin sin caja la deja fuera de cualquier cierre.
        Caja caja = cajaService.getAbierta(usuarioValidadorId)
                .orElseGet(() -> {
                    if (permitirSinCaja) return null;
                    throw new IllegalStateException("No hay una caja abierta: abrí la caja antes de cobrar.");
                });
        Compra actualizada = marcarEntradasComoUsadas(compraId, usuarioValidadorId);
        actualizada.setCaja(caja);
        return compraRepository.save(actualizada);
    }

    @Transactional
    @Override
    public CotizacionResponseDTO cotizar(CotizacionRequestDTO cotizacionRequest) {
        FormaPago formaPago = cotizacionRequest.getFormaPago();
        if (formaPago == null) {
            throw new IllegalArgumentException("Debe indicar una forma de pago");
        }

        // Cotización de una reserva: los mismos precios congelados que usa el cobro.
        Compra reserva = cotizacionRequest.getCompraReservadaId() == null ? null
                : compraRepository.findById(cotizacionRequest.getCompraReservadaId())
                        .filter(c -> c.getEstado() == EstadoCompra.RESERVADO_EFECTIVO)
                        .orElse(null);
        Map<Long, BigDecimal> preciosReservados = reserva == null ? Map.of() : preciosListaReservados(reserva);

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal ahorro = BigDecimal.ZERO;
        BigDecimal listaEntradas = BigDecimal.ZERO;
        List<CuponDescuentoCalculator.Linea> lineasCupon = new ArrayList<>();

        if (cotizacionRequest.getEntradas() != null) {
            for (DetalleCompraDTO d : cotizacionRequest.getEntradas()) {
                if (d == null || d.getTipoEntradaId() == null || d.getCantidad() == null) continue;

                TipoEntrada tipoEntrada = tipoEntradaService.findById(d.getTipoEntradaId())
                        .orElseThrow(() -> new IllegalArgumentException("TipoEntrada no encontrada para id: " + d.getTipoEntradaId()));

                BigDecimal precioCongelado = preciosReservados.get(d.getTipoEntradaId());
                BigDecimal totalLinea;
                if (precioCongelado != null) {
                    totalLinea = calculoPrecioService.calcularTotal(tipoEntrada, d.getCantidad(), formaPago, precioCongelado);
                    ahorro = ahorro.add(calculoPrecioService.calcularAhorro(tipoEntrada, d.getCantidad(), formaPago, precioCongelado));
                } else {
                    totalLinea = calculoPrecioService.calcularTotal(tipoEntrada, d.getCantidad(), formaPago);
                    ahorro = ahorro.add(calculoPrecioService.calcularAhorro(tipoEntrada, d.getCantidad(), formaPago));
                }
                BigDecimal precioLista = precioCongelado != null ? precioCongelado : tipoEntrada.getPrecio();
                if (precioLista != null) {
                    listaEntradas = listaEntradas.add(precioLista.multiply(BigDecimal.valueOf(d.getCantidad())));
                }
                subtotal = subtotal.add(totalLinea);
                lineasCupon.add(new CuponDescuentoCalculator.Linea(d.getTipoEntradaId(), d.getCantidad(), totalLinea));
            }
        }

        // Mismo criterio que el cobro: lo mismo que reservó, en efectivo -> el monto de la reserva.
        if (reserva != null && mantienePrecioReservado(reserva, formaPago, cotizacionRequest.getEntradas())) {
            subtotal = reserva.getMontoTotal();
            ahorro = listaEntradas.subtract(subtotal).max(BigDecimal.ZERO);
        }

        if (cotizacionRequest.getArticulos() != null) {
            for (LineaArticuloPosDTO a : cotizacionRequest.getArticulos()) {
                if (a == null || a.getCantidad() == null || a.getPrecioUnitario() == null) continue;
                subtotal = subtotal.add(a.getPrecioUnitario().multiply(BigDecimal.valueOf(a.getCantidad())));
            }
        }

        // El descuento de promo/manual no cuenta como "ahorro": ahorro es sólo el precio de grupo.
        BigDecimal descuento = calcularDescuentoPos(subtotal, cotizacionRequest.getPromocionId(),
                cotizacionRequest.getDescuentoManualPorcentaje(), cotizacionRequest.getDescuentoManualMonto());

        CotizacionResponseDTO dto = new CotizacionResponseDTO();
        dto.setSubtotal(subtotal.subtract(descuento));
        dto.setAhorro(ahorro);
        aplicarCuponACotizacion(cotizacionRequest, lineasCupon, dto);
        return dto;
    }

    /**
     * Suma el descuento del cupón a la cotización. Nunca tira: si no aplica, descuento 0 y el motivo en
     * avisoCupon.
     */
    private void aplicarCuponACotizacion(CotizacionRequestDTO request, List<CuponDescuentoCalculator.Linea> lineas,
                                         CotizacionResponseDTO dto) {
        String codigo = request.getCuponCodigo();
        if (codigo == null || codigo.isBlank()) return;

        Optional<Cupon> encontrado = cuponService.getByCode(codigo);
        if (encontrado.isEmpty()) {
            dto.setAvisoCupon("Cupón no encontrado: " + codigo);
            return;
        }
        Cupon cupon = encontrado.get();
        LocalDate hoy = LocalDate.now();
        if (!cupon.getActivo() || (cupon.getFechaExpiracion() != null && cupon.getFechaExpiracion().isBefore(hoy))) {
            dto.setAvisoCupon("Cupón no válido o expirado: " + codigo);
            return;
        }
        if (cupon.getFechaDesde() != null && cupon.getFechaDesde().isAfter(hoy)) {
            dto.setAvisoCupon("El cupón " + codigo + " recién se puede usar desde el " + cupon.getFechaDesde() + ".");
            return;
        }
        CuponDescuentoCalculator.Resultado resultado = CuponDescuentoCalculator.calcular(cupon, lineas);
        dto.setAvisoCupon(resultado.motivoNoAplica());
        dto.setDescuentoCupon(resultado.descuento());
    }

    @Override
    public Optional<String> consultarEstadoCompra(Long id) {
        return compraRepository.findById(id).map(compra -> compra.getEstado().name());
    }

    private boolean esBlanco(String s) {
        return s == null || s.isBlank();
    }

    /** Minúsculas, sin acentos y sin espacios de más. */
    private String normalizarNombre(String nombre, String apellido) {
        String junto = (nombre == null ? "" : nombre) + " " + (apellido == null ? "" : apellido);
        String sinAcentos = java.text.Normalizer.normalize(junto.trim().toLowerCase(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinAcentos.replaceAll("\\s+", " ");
    }

    /**
     * Por debajo, personas distintas: cliente nuevo con el mismo DNI. Por encima sin ser idénticos, se pide
     * confirmación.
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
    private record DetallesCalculados(List<CompraDetalle> detalles, BigDecimal montoTotal,
                                      List<CuponDescuentoCalculator.Linea> lineasEntrada) {
        DetallesCalculados(List<CompraDetalle> detalles, BigDecimal montoTotal) {
            this(detalles, montoTotal, List.of());
        }
    }

    /**
     * Vendido ese día por tipo, para el cupo diario. Sin canceladas, artículos ni la compra
     * `excluirCompraId`. Vacío para regalos.
     */
    private Map<Long, Integer> cantidadVendidaPorTipoEnElDia(LocalDate fechaVisita, Long excluirCompraId) {
        Map<Long, Integer> cantidadVendidaPorTipo = new HashMap<>();
        if (fechaVisita == null) return cantidadVendidaPorTipo;

        // Lock para que dos ventas simultáneas no tomen el último lugar. Va acá porque todos los caminos que
        // controlan cupo pasan por este método. Se libera al terminar la transacción y es el mismo de la
        // numeración del código de reserva.
        compraRepository.bloquearFechaDeVisita(
                fechaVisita.format(DateTimeFormatter.ofPattern("yyMMdd")).hashCode());

        for (Compra otra : compraRepository.findAllByFechaVisitaOrderByCodigoReservaAsc(fechaVisita)) {
            if ((excluirCompraId != null && excluirCompraId.equals(otra.getId()))
                    || otra.getEstado() == EstadoCompra.CANCELADO || otra.getDetalles() == null) continue;
            for (CompraDetalle det : otra.getDetalles()) {
                if (det.getTipoEntrada() == null) continue;
                cantidadVendidaPorTipo.merge(det.getTipoEntrada().getId(), det.getCantidad(), Integer::sum);
            }
        }
        return cantidadVendidaPorTipo;
    }

    /**
     * Arma los detalles con el precio de la forma de pago (el de grupo sólo en efectivo) y chequea el cupo
     * diario.
     */
    private DetallesCalculados construirDetalles(List<DetalleCompraDTO> entradas, FormaPago formaPago, LocalDate fechaVisita) {
        return construirDetalles(entradas, formaPago, fechaVisita, null);
    }

    /**
     * `excluirCompraId`: las líneas actuales de esa compra no cuentan para el cupo, se están reemplazando.
     */
    private DetallesCalculados construirDetalles(List<DetalleCompraDTO> entradas, FormaPago formaPago,
                                                 LocalDate fechaVisita, Long excluirCompraId) {
        return construirDetalles(entradas, formaPago, fechaVisita, excluirCompraId, null);
    }

    /** `preciosListaCongelados`: precio de lista al reservar; los tipos que no están usan el actual. */
    private DetallesCalculados construirDetalles(List<DetalleCompraDTO> entradas, FormaPago formaPago,
                                                 LocalDate fechaVisita, Long excluirCompraId,
                                                 Map<Long, BigDecimal> preciosListaCongelados) {
        // Cupo: lo vendido ese día más lo nuevo. Los regalos no tienen día.
        Map<Long, Integer> cantidadVendidaPorTipo = cantidadVendidaPorTipoEnElDia(fechaVisita, excluirCompraId);

        BigDecimal montoTotal = BigDecimal.ZERO;
        List<CompraDetalle> detalles = new ArrayList<>();
        List<CuponDescuentoCalculator.Linea> lineasEntrada = new ArrayList<>();

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

                // RESERVA_ADMIN no cobra: no se pide precio.
                BigDecimal precioCongelado = preciosListaCongelados == null ? null : preciosListaCongelados.get(tipoId);
                BigDecimal precioLista = precioCongelado != null ? precioCongelado : tipoEntrada.getPrecio();
                BigDecimal totalLinea = BigDecimal.ZERO;
                if (formaPago != FormaPago.RESERVA_ADMIN) {
                    totalLinea = precioCongelado != null
                            ? calculoPrecioService.calcularTotal(tipoEntrada, d.getCantidad(), formaPago, precioCongelado)
                            : calculoPrecioService.calcularTotal(tipoEntrada, d.getCantidad(), formaPago);
                    montoTotal = montoTotal.add(totalLinea);
                }
                lineasEntrada.add(new CuponDescuentoCalculator.Linea(tipoId, d.getCantidad(), totalLinea));

                detalles.add(CompraDetalle.builder()
                        .tipoEntrada(tipoEntrada)
                        .cantidad(d.getCantidad())
                        .precioUnitario(precioLista)
                        .build());
            }
        }

        return new DetallesCalculados(detalles, montoTotal, lineasEntrada);
    }

    /** Líneas de artículo vario: de catálogo o libres. El precio siempre lo pone el cajero. */
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

    /** Si hay entradas, tiene que haber un pase obligatorio. Una venta de sólo artículos no lo exige. */
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
     * El front ya filtra los tipos "Solo POS" en la compra online; acá se rechaza por si arman el request a
     * mano.
     */
    private void validarNingunoSoloPos(List<CompraDetalle> detalles) {
        boolean haySoloPos = detalles.stream()
                .anyMatch(d -> d.getTipoEntrada() != null && Boolean.TRUE.equals(d.getTipoEntrada().getSoloPos()));
        if (haySoloPos) {
            throw new IllegalArgumentException(
                    "Uno de los tipos de entrada seleccionados no está disponible para la compra online."
            );
        }
    }

    /**
     * Código yyMMdd-N, N = orden de la reserva en el día. Sin fecha: REGALO-N o RESERVA-N.
     *
     * El lock serializa "contar y usar el número": sin él dos compras simultáneas armaban el mismo código y
     * una moría contra el unique. Es _xact_ porque tiene que durar hasta el commit. Esto exige una
     * transacción corta: la llamada a Mercado Pago tiene que seguir afuera (ver iniciarCompraConPago).
     */
    private String generarCodigoReserva(LocalDate fechaVisita, boolean tieneReceptor) {
        String prefijo = fechaVisita != null
                ? fechaVisita.format(DateTimeFormatter.ofPattern("yyMMdd"))
                : (tieneReceptor ? "REGALO" : "RESERVA");
        // Si dos prefijos colisionan sólo se esperan de más.
        compraRepository.bloquearFechaDeVisita(prefijo.hashCode());

        long numero = fechaVisita != null
                ? compraRepository.countByFechaVisita(fechaVisita) + 1
                : compraRepository.countByFechaVisitaIsNull() + 1;
        return prefijo + "-" + numero;
    }

    @Transactional
    @Override
    public Compra registrarVentaPos(VentaPosRequestDTO request, Long usuarioVendedorId) {
        // Si la venta ya se procesó (reintento del POS), devolver la guardada en vez de cobrar dos veces.
        String idempotencyKey = request.getIdempotencyKey();
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<Compra> yaRegistrada = compraRepository.findByIdempotencyKey(idempotencyKey);
            if (yaRegistrada.isPresent()) {
                return yaRegistrada.get();
            }
        }

        Usuario vendedor = usuarioService.obtenerUsuarioPorId(usuarioVendedorId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario vendedor no encontrado para id: " + usuarioVendedorId));

        // Toda venta de puerta va contra una caja abierta.
        Caja caja = cajaService.getAbiertaOrThrow(usuarioVendedorId);

        // Una reserva RESERVADO_EFECTIVO cargada en el POS no crea compra nueva: se reprecia y se cierra.
        Compra venta = request.getCompraReservadaId() != null
                ? cobrarReservaComoVentaPos(request, caja, vendedor, idempotencyKey)
                : crearVenta(request, caja, vendedor, idempotencyKey);
        vincularCobroPoint(request, venta);
        // Misma transacción que la venta; la emisión arranca después del commit.
        facturaService.solicitar(venta, request.getFacturacion());
        return venta;
    }

    @Transactional
    @Override
    public Compra registrarVentaPosComoAdmin(Long cajaId, VentaPosRequestDTO request) {
        Caja caja = cajaRepository.findById(cajaId)
                .orElseThrow(() -> new IllegalArgumentException("Caja no encontrada para id: " + cajaId));
        if (caja.getFechaCierre() != null) {
            throw new IllegalStateException("Esta caja ya está cerrada");
        }
        // Queda a nombre del dueño de la caja, no del admin.
        Compra venta = crearVenta(request, caja, caja.getUsuario(), null);
        vincularCobroPoint(request, venta);
        facturaService.solicitar(venta, request.getFacturacion());
        return venta;
    }

    /** Ata el cobro del Point a la venta, en la misma transacción. */
    private void vincularCobroPoint(VentaPosRequestDTO request, Compra venta) {
        cobroPointService.vincular(request.getCobrosPointIds(), venta);
    }

    /** Guarda la venta contra una caja y un vendedor ya resueltos. Sólo el POS manda idempotencyKey. */
    private Compra crearVenta(VentaPosRequestDTO request, Caja caja, Usuario vendedor, String idempotencyKey) {
        if (request.getFormaPago() == null) {
            throw new IllegalArgumentException("Debe indicar la forma de pago del cobro");
        }
        boolean sinEntradas = request.getEntradas() == null || request.getEntradas().isEmpty();
        boolean sinArticulos = request.getArticulos() == null || request.getArticulos().isEmpty();
        if (sinEntradas && sinArticulos) {
            throw new IllegalArgumentException("La venta no tiene entradas ni artículos cargados");
        }

        // La visita es hoy y no se valida que el día esté abierto. fechaOriginal viene de la cola offline,
        // para que una venta de las 23:50 sincronizada después de medianoche no caiga al día siguiente.
        LocalDateTime momentoVenta = request.getFechaOriginal() == null ? LocalDateTime.now() : request.getFechaOriginal();
        LocalDate hoy = momentoVenta.toLocalDate();

        DetallesCalculados calculoEntradas = construirDetalles(request.getEntradas(), request.getFormaPago(), hoy);
        DetallesCalculados calculoArticulos = construirLineasArticulos(request.getArticulos());

        List<CompraDetalle> todosLosDetalles = new ArrayList<>(calculoEntradas.detalles());
        todosLosDetalles.addAll(calculoArticulos.detalles());
        if (todosLosDetalles.isEmpty()) {
            throw new IllegalArgumentException("La venta no tiene entradas ni artículos cargados");
        }
        validarPaseObligatorio(todosLosDetalles);

        BigDecimal montoBruto = calculoEntradas.montoTotal().add(calculoArticulos.montoTotal());
        BigDecimal descuento = calcularDescuentoPos(montoBruto, request.getPromocionId(),
                request.getDescuentoManualPorcentaje(), request.getDescuentoManualMonto());
        BigDecimal montoFinal = montoBruto.subtract(descuento);
        validarFormaPagoPos(request.getFormaPago(), montoFinal);
        validarPagoMixto(request, montoFinal);
        // Se vuelve a buscar la promo sólo para guardar la referencia.
        Promocion promocionUsada = request.getPromocionId() != null
                ? promocionRepository.findById(request.getPromocionId()).orElse(null)
                : null;

        BigDecimal cotizacionDolar = request.getCotizacionDolar();
        BigDecimal dolaresRecibidos = validarCobroDolares(request, montoFinal);

        // Venta anónima. Nace VENDIDO_EN_PUERTA: cobro e ingreso en el mismo acto.
        Compra venta = Compra.builder()
                .cliente(null)
                .fechaVisita(hoy)
                .codigoReserva(generarCodigoReserva(hoy, false))
                .montoTotal(montoFinal)
                .descuentoAplicado(descuento)
                .detalles(todosLosDetalles)
                .estado(EstadoCompra.VENDIDO_EN_PUERTA)
                .formaPago(request.getFormaPago())
                .formaPagoSecundaria(request.getFormaPagoSecundaria())
                .montoFormaPagoSecundaria(request.getFormaPagoSecundaria() != null ? request.getMontoFormaPagoSecundaria() : null)
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

    /**
     * Cierra una reserva RESERVADO_EFECTIVO cobrada en el POS: reprecia según la forma de pago, aplica el
     * descuento y la marca USADO. Mantiene cliente, código y fecha.
     */
    private Compra cobrarReservaComoVentaPos(VentaPosRequestDTO request, Caja caja, Usuario vendedor, String idempotencyKey) {
        Compra reserva = compraRepository.findById(request.getCompraReservadaId())
                .orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada ID: " + request.getCompraReservadaId()));
        if (reserva.getEstado() != EstadoCompra.RESERVADO_EFECTIVO) {
            throw new IllegalStateException("La reserva ID " + reserva.getId()
                    + " no está pendiente de cobro en boletería (estado actual: " + reserva.getEstado() + ")");
        }
        if (request.getFormaPago() == null) {
            throw new IllegalArgumentException("Debe indicar la forma de pago del cobro");
        }

        LocalDateTime momentoVenta = request.getFechaOriginal() == null ? LocalDateTime.now() : request.getFechaOriginal();

        // Cupo contra el día de la reserva sin contar sus propias líneas. Se reprecia con el precio de lista
        // que tenía al reservar.
        DetallesCalculados calculoEntradas = construirDetalles(
                request.getEntradas(), request.getFormaPago(), reserva.getFechaVisita(), reserva.getId(),
                preciosListaReservados(reserva));
        DetallesCalculados calculoArticulos = construirLineasArticulos(request.getArticulos());

        List<CompraDetalle> todosLosDetalles = new ArrayList<>(calculoEntradas.detalles());
        todosLosDetalles.addAll(calculoArticulos.detalles());
        if (todosLosDetalles.isEmpty()) {
            throw new IllegalArgumentException("La venta no tiene entradas ni artículos cargados");
        }
        validarPaseObligatorio(todosLosDetalles);

        // Si paga lo mismo que reservó y en efectivo, se cobra el monto de la reserva sin reevaluar
        // escalones.
        boolean mantienePrecioReservado = mantienePrecioReservado(reserva, request.getFormaPago(), request.getEntradas());
        BigDecimal montoEntradas = mantienePrecioReservado ? reserva.getMontoTotal() : calculoEntradas.montoTotal();
        BigDecimal descuentoPrevio = mantienePrecioReservado && reserva.getDescuentoAplicado() != null
                ? reserva.getDescuentoAplicado() : BigDecimal.ZERO;

        BigDecimal montoBruto = montoEntradas.add(calculoArticulos.montoTotal());
        BigDecimal descuento = calcularDescuentoPos(montoBruto, request.getPromocionId(),
                request.getDescuentoManualPorcentaje(), request.getDescuentoManualMonto());
        BigDecimal montoFinal = montoBruto.subtract(descuento);
        validarFormaPagoPos(request.getFormaPago(), montoFinal);
        validarPagoMixto(request, montoFinal);
        Promocion promocionUsada = request.getPromocionId() != null
                ? promocionRepository.findById(request.getPromocionId()).orElse(null)
                : null;

        BigDecimal cotizacionDolar = request.getCotizacionDolar();
        BigDecimal dolaresRecibidos = validarCobroDolares(request, montoFinal);

        // orphanRemoval: vaciar la colección borra las líneas viejas.
        reserva.getDetalles().clear();
        reserva.getDetalles().addAll(todosLosDetalles);
        for (CompraDetalle det : todosLosDetalles) {
            det.setCompra(reserva);
        }
        reserva.setMontoTotal(montoFinal);
        reserva.setDescuentoAplicado(descuentoPrevio.add(descuento));
        reserva.setPromocion(promocionUsada);
        reserva.setFormaPago(request.getFormaPago());
        reserva.setFormaPagoSecundaria(request.getFormaPagoSecundaria());
        reserva.setMontoFormaPagoSecundaria(request.getFormaPagoSecundaria() != null ? request.getMontoFormaPagoSecundaria() : null);
        reserva.setCotizacionDolar(cotizacionDolar);
        reserva.setDolaresRecibidos(dolaresRecibidos);
        reserva.setEstado(EstadoCompra.USADO);
        reserva.setCaja(caja);
        reserva.setUsuarioValidador(vendedor);
        reserva.setFechaValidacion(momentoVenta);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            reserva.setIdempotencyKey(idempotencyKey);
        }

        return compraRepository.save(reserva);
    }

    /** Precio de lista de cada tipo al reservar. Las líneas viejas sin ese dato no aparecen. */
    private Map<Long, BigDecimal> preciosListaReservados(Compra reserva) {
        Map<Long, BigDecimal> precios = new HashMap<>();
        if (reserva.getDetalles() == null) return precios;
        for (CompraDetalle d : reserva.getDetalles()) {
            if (d.getTipoEntrada() != null && d.getPrecioUnitario() != null) {
                precios.put(d.getTipoEntrada().getId(), d.getPrecioUnitario());
            }
        }
        return precios;
    }

    /** True si paga en efectivo las mismas entradas y cantidades. Con artículos propios no califica. */
    private boolean mantienePrecioReservado(Compra reserva, FormaPago formaPago, List<DetalleCompraDTO> entradas) {
        if (formaPago != FormaPago.EFECTIVO_BOLETERIA || reserva.getFormaPago() != FormaPago.EFECTIVO_BOLETERIA
                || reserva.getDetalles() == null || reserva.getMontoTotal() == null) {
            return false;
        }
        Map<Long, Integer> reservadas = new HashMap<>();
        for (CompraDetalle d : reserva.getDetalles()) {
            if (d.getTipoEntrada() == null) return false;
            reservadas.merge(d.getTipoEntrada().getId(), d.getCantidad(), Integer::sum);
        }
        Map<Long, Integer> pedidas = new HashMap<>();
        if (entradas != null) {
            for (DetalleCompraDTO d : entradas) {
                if (d == null || d.getTipoEntradaId() == null || d.getCantidad() == null || d.getCantidad() <= 0) continue;
                pedidas.merge(d.getTipoEntradaId(), d.getCantidad(), Integer::sum);
            }
        }
        return !reservadas.isEmpty() && reservadas.equals(pedidas);
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
                // El Cliente se comparte entre compras: se crea uno propio en vez de pisar el DNI del
                // compartido.
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

        // El DNI del receptor de un regalo está en la Compra: se pisa directo.
        if (compra.getFechaVisita() == null && compra.getReceptorDni() != null
                && !esBlanco(request.getReceptorDni())) {
            compra.setReceptorDni(request.getReceptorDni().trim());
        }

        return compraRepository.save(compra);
    }

    @Override
    public void reenviarComprobante(Long compraId) {
        Compra compra = compraRepository.findById(compraId)
                .orElseThrow(() -> new IllegalArgumentException("Compra no encontrada ID: " + compraId));

        // RESERVADO_EFECTIVO también tiene mail, y se tiene que poder reenviar.
        if (compra.getEstado() != EstadoCompra.APROBADO && compra.getEstado() != EstadoCompra.USADO
                && compra.getEstado() != EstadoCompra.RESERVADO_EFECTIVO) {
            throw new IllegalStateException("La compra ID " + compraId + " todavía no tiene un comprobante confirmado para reenviar.");
        }
        emailService.enviarComprobanteCompra(compraId);
        // Sólo un regalo tiene a quién avisar (una reserva sin fecha generada desde Acciones no).
        if (compra.getFechaVisita() == null && compra.getReceptorEmail() != null) {
            emailService.enviarAvisoRegalo(compraId);
        }
    }

    @Transactional
    @Override
    public boolean confirmarAprobado(Long compraId, List<Long> pagosMercadoPago, ConfirmacionPago origen) {
        Compra compra = compraRepository.findById(compraId).orElse(null);
        if (compra == null) {
            return false;
        }
        // Idempotente: MP reintenta avisos y el webhook puede llegar junto con la verificación directa.
        if (compra.getEstado() == EstadoCompra.APROBADO || compra.getEstado() == EstadoCompra.USADO) {
            return false;
        }

        // Un aviso tardío no puede revivir una compra reembolsada.
        if (compra.getEstado() == EstadoCompra.REEMBOLSADA) {
            alertasPagos.error("Llegó una confirmación de los pagos {} de Mercado Pago para la compra ID {}, que está REEMBOLSADA. "
                    + "No se toca: revisar a mano si esos pagos hay que devolverlos.", pagosMercadoPago, compraId);
            return false;
        }

        boolean estabaCancelada = compra.getEstado() == EstadoCompra.CANCELADO;
        String idsPagos = pagosMercadoPago.stream().map(String::valueOf).collect(Collectors.joining(","));
        // Con dos confirmaciones simultáneas las dos pasan el chequeo de arriba: gana una sola sentencia, y
        // cupón y mails corren sólo en esa.
        if (compraRepository.aprobarSiSigueSinPagar(compraId, idsPagos) == 0) {
            return false;
        }
        compra.setEstado(EstadoCompra.APROBADO);
        compra.setMpPaymentIds(idsPagos);
        // La sentencia ya borró el motivo en la base; se refleja en memoria para no reescribirlo.
        compra.setMotivoCancelacion(null);
        compra.setDetalleCancelacion(null);
        compra.setFechaCancelacion(null);
        compra.setCanceladaPor(null);
        compraRepository.registrarConfirmacionPago(compraId, origen.name(), LocalDateTime.now());

        // Cancelada por el barrido y pagada igual: se revive (mejor un lugar de más que alguien que pagó sin
        // entrada), pero se avisa porque cupo y cupón ya se habían liberado.
        if (estabaCancelada) {
            log.warn("La compra ID {} ({}) estaba CANCELADA por checkout abandonado y llegó el pago: "
                    + "se reactiva. Ojo: su lugar en el cupo del {} ya se había liberado.",
                    compraId, compra.getCodigoReserva(), compra.getFechaVisita());
            // Hay que volver a tomar el uso del cupón. Si no quedan, se aprueba igual y queda avisado.
            if (compra.getCupon() != null && !cuponService.consumirUso(compra.getCupon().getId())) {
                log.error("Se reactivó la compra ID {} pero el cupón {} ya no tenía usos libres: "
                        + "quedó aplicado una vez de más. Revisar a mano.",
                        compraId, compra.getCupon().getCodigo());
            }
        }
        // Con facturación online, la confirmación sale con la factura después del commit. Si no, sale ya.
        if (!facturaService.solicitarOnline(compra)) {
            emailService.enviarComprobanteCompra(compraId);
        }
        if (compra.getFechaVisita() == null) {
            emailService.enviarAvisoRegalo(compraId);
        }
        return true;
    }

    @Override
    public String verificarPagoDirecto(Long compraId) {
        Compra compra = compraRepository.findById(compraId)
                .orElseThrow(() -> new IllegalArgumentException("Compra no encontrada ID: " + compraId));

        // Si ya se sabe el resultado no se consulta a MP.
        if (compra.getEstado() != EstadoCompra.PENDIENTE_PAGO || compra.getFormaPago() != FormaPago.MERCADO_PAGO) {
            return compra.getEstado().name();
        }

        try {
            pagosQueCubrenLaCompra(compra).ifPresent(pagos -> self.confirmarAprobado(compraId, pagos, ConfirmacionPago.VERIFICACION));
        } catch (MPException | MPApiException | RuntimeException e) {
            // No se pudo consultar: se deja como está para reintentar.
            log.error("Error consultando a Mercado Pago el estado de la compra ID {}", compraId, e);
        }

        return compraRepository.findById(compraId).map(c -> c.getEstado().name()).orElse(compra.getEstado().name());
    }

    /**
     * Ids de los pagos que pagan la compra.
     *
     * PROPAGA la excepción a propósito: quien llama tiene que distinguir "no pagó" de "no pude verificar".
     * Antes se atrapaba y expirarCheckoutAbandonado cancelaba compras pagadas cuando MP no contestaba.
     */
    protected Optional<List<Long>> pagosQueCubrenLaCompra(Compra compra) throws MPException, MPApiException {
        return pagosQueCubren(compra, buscarPagosEnMercadoPago(compra));
    }

    // protected para poder sustituirlo en los tests.
    protected List<Payment> buscarPagosEnMercadoPago(Compra compra) throws MPException, MPApiException {
        // limit/offset explícitos: en null el SDK tira NullPointerException.
        MPSearchRequest searchRequest = MPSearchRequest.builder()
                .filters(Map.of("external_reference", compra.getCodigoReserva()))
                .limit(10)
                .offset(0)
                .build();
        List<Payment> pagos = new PaymentClient().search(searchRequest).getResults();
        return pagos != null ? pagos : List.of();
    }

    /**
     * Pagos aprobados que suman exactamente el total (uno o varios con tarjetas distintas). Vacío si todavía
     * no alcanza o no cierra.
     */
    private static Optional<List<Long>> pagosQueCubren(Compra compra, Collection<Payment> pagos) {
        List<Payment> aprobados = pagos.stream()
                .filter(p -> "approved".equals(p.getStatus()) && p.getTransactionAmount() != null
                        && esPosteriorALaCompra(p, compra))
                .sorted(Comparator.comparing(Payment::getId))
                .toList();
        Optional<Payment> porElTotal = aprobados.stream()
                .filter(p -> compra.getMontoTotal().compareTo(p.getTransactionAmount()) == 0)
                .findFirst();
        if (porElTotal.isPresent()) {
            return Optional.of(List.of(porElTotal.get().getId()));
        }
        BigDecimal suma = aprobados.stream().map(Payment::getTransactionAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (aprobados.size() > 1 && compra.getMontoTotal().compareTo(suma) == 0) {
            return Optional.of(aprobados.stream().map(Payment::getId).toList());
        }
        return Optional.empty();
    }

    /**
     * Si la base se reinicia los códigos de reserva se repiten: un pago anterior a la compra no puede ser de
     * ella.
     */
    private static boolean esPosteriorALaCompra(Payment pago, Compra compra) {
        return compra.getFechaCreacion() == null || pago.getDateCreated() == null
                || !pago.getDateCreated().atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
                        .isBefore(compra.getFechaCreacion());
    }

    @Override
    public boolean confirmarPagoMercadoPago(Payment avisado) {
        if (!"approved".equals(avisado.getStatus()) || avisado.getExternalReference() == null) {
            return false;
        }
        // Los pagos del Point también avisan por "payment", pero los maneja CobroPointService por "order".
        if (avisado.getExternalReference().startsWith(CobroPointService.PREFIJO_REFERENCIA)) {
            return false;
        }
        Compra compra = compraRepository.findByCodigoReserva(avisado.getExternalReference()).orElse(null);
        if (compra == null) {
            log.warn("Pago aprobado {} de Mercado Pago con external_reference {} que no corresponde a ninguna compra",
                    avisado.getId(), avisado.getExternalReference());
            return false;
        }

        if (compra.getEstado() == EstadoCompra.APROBADO || compra.getEstado() == EstadoCompra.USADO) {
            // Un pago ya registrado es MP reintentando. Uno aprobado nuevo y posterior: el cliente pagó dos
            // veces.
            if (!compra.pagosMercadoPago().contains(avisado.getId()) && esPosteriorALaCompra(avisado, compra)) {
                alertasPagos.error("La compra {} ya estaba paga con los pagos {} de Mercado Pago y llegó otro pago aprobado, "
                                + "el {} por {}: el cliente pagó de más. Devolverlo desde el panel de Mercado Pago.",
                        compra.getCodigoReserva(), compra.pagosMercadoPago(), avisado.getId(), avisado.getTransactionAmount());
            }
            return false;
        }

        Map<Long, Payment> candidatos = new LinkedHashMap<>();
        try {
            buscarPagosEnMercadoPago(compra).forEach(p -> candidatos.put(p.getId(), p));
        } catch (MPException | MPApiException | RuntimeException e) {
            log.warn("No se pudieron buscar los pagos de la compra {} en Mercado Pago: se evalúa sólo el pago avisado {}",
                    compra.getCodigoReserva(), avisado.getId(), e);
        }
        // La búsqueda de MP puede no traer todavía el pago recién hecho: el avisado se suma siempre.
        candidatos.put(avisado.getId(), avisado);

        Optional<List<Long>> pagos = pagosQueCubren(compra, candidatos.values());
        if (pagos.isEmpty()) {
            log.info("El pago aprobado {} de Mercado Pago (monto {}) no cubre el total de la compra {} ({}) ni sumado a los "
                            + "demás aprobados: puede ser una tarjeta de varias con el resto en curso, o un pago que no es de esta compra.",
                    avisado.getId(), avisado.getTransactionAmount(), compra.getCodigoReserva(), compra.getMontoTotal());
            return false;
        }
        // self y no this: el webhook llega sin transacción y confirmarAprobado necesita la suya.
        return self.confirmarAprobado(compra.getId(), pagos.get(), ConfirmacionPago.WEBHOOK);
    }

    @Override
    public List<Long> idsCheckoutsAbandonados(int horasAntiguedad) {
        return compraRepository.findIdsByEstadoAndFechaCreacionBefore(
                EstadoCompra.PENDIENTE_PAGO, LocalDateTime.now().minusHours(horasAntiguedad));
    }

    @Transactional
    @Override
    public void expirarCheckoutAbandonado(Long compraId) {
        Compra compra = compraRepository.findById(compraId).orElse(null);
        if (compra == null || compra.getEstado() != EstadoCompra.PENDIENTE_PAGO) {
            return;
        }

        // Si el pago entró y el webhook no llegó, esto la confirma.
        List<Payment> intentos = List.of();
        if (compra.getFormaPago() == FormaPago.MERCADO_PAGO) {
            try {
                intentos = buscarPagosEnMercadoPago(compra).stream()
                        .filter(p -> esPosteriorALaCompra(p, compra))
                        .toList();
                Optional<List<Long>> pagos = pagosQueCubren(compra, intentos);
                if (pagos.isPresent()) {
                    confirmarAprobado(compraId, pagos.get(), ConfirmacionPago.BARRIDO);
                    return;
                }
            } catch (MPException | MPApiException | RuntimeException e) {
                // No sabemos si pagó: no se cancela. El scheduler la vuelve a tomar en el próximo ciclo.
                log.warn("No se pudo verificar contra Mercado Pago la compra ID {}: NO se cancela, "
                        + "se reintenta en el próximo ciclo", compraId, e);
                return;
            }
        }

        // Sin intentos abandonó el checkout; con intentos, pagó y no se aprobó. Se anota qué contestó MP.
        MotivoCancelacion motivo = intentos.isEmpty()
                ? MotivoCancelacion.CHECKOUT_ABANDONADO : MotivoCancelacion.PAGO_RECHAZADO;
        String detalle = intentos.isEmpty()
                ? (compra.getFormaPago() == FormaPago.MERCADO_PAGO ? "No llegó a intentar el pago." : null)
                : recortarDetalle(intentos.stream()
                        .sorted(Comparator.comparing(Payment::getId))
                        .map(CompraServiceImpl::describirIntentoDePago)
                        .collect(Collectors.joining(" · ")));
        compra.cancelar(motivo, detalle, USUARIO_SISTEMA);
        liberarCupon(compra);
        compraRepository.save(compra);
        log.info("Compra ID {} cancelada: checkout sin pagarse ({}){}", compraId, motivo,
                detalle != null ? ": " + detalle : "");
    }

    /** Respuesta de MP en criollo: "visa: rechazado, fondos insuficientes". */
    static String describirIntentoDePago(Payment pago) {
        String medio = pago.getPaymentMethodId() != null ? pago.getPaymentMethodId() : "medio desconocido";
        String estado = switch (pago.getStatus() == null ? "" : pago.getStatus()) {
            case "rejected" -> "rechazado";
            case "cancelled" -> "cancelado";
            case "pending", "in_process", "authorized" -> "quedó sin acreditar";
            case "refunded", "charged_back" -> "devuelto";
            case "approved" -> "aprobado por un monto que no cubre la compra";
            case "" -> "estado desconocido";
            default -> pago.getStatus();
        };
        String razon = explicarStatusDetail(pago.getStatusDetail());
        return medio + ": " + estado + (razon != null ? ", " + razon : "");
    }

    /** Los status_detail de Mercado Pago más comunes; los demás van tal cual vienen. */
    private static String explicarStatusDetail(String detalle) {
        if (detalle == null || detalle.isBlank() || "accredited".equals(detalle)) return null;
        return switch (detalle) {
            case "cc_rejected_insufficient_amount" -> "fondos insuficientes";
            case "cc_rejected_bad_filled_security_code" -> "código de seguridad mal cargado";
            case "cc_rejected_bad_filled_date" -> "vencimiento mal cargado";
            case "cc_rejected_bad_filled_card_number" -> "número de tarjeta mal cargado";
            case "cc_rejected_bad_filled_other" -> "datos de la tarjeta mal cargados";
            case "cc_rejected_call_for_authorize" -> "el banco pidió autorizar el pago";
            case "cc_rejected_card_disabled" -> "tarjeta deshabilitada";
            case "cc_rejected_card_error" -> "error con la tarjeta";
            case "cc_rejected_duplicated_payment" -> "pago duplicado";
            case "cc_rejected_high_risk" -> "rechazado por prevención de fraude";
            case "cc_rejected_max_attempts" -> "demasiados intentos";
            case "cc_rejected_blacklist" -> "tarjeta bloqueada";
            case "cc_rejected_invalid_installments" -> "cuotas no válidas";
            case "cc_rejected_other_reason" -> "rechazado por el banco";
            case "pending_contingency", "pending_review_manual" -> "en revisión de Mercado Pago";
            case "pending_waiting_payment", "pending_waiting_transfer" -> "esperando el pago";
            case "expired" -> "venció";
            case "by_collector", "by_payer" -> "cancelado antes de pagar";
            default -> detalle;
        };
    }

    private static String describirError(Exception e) {
        if (e instanceof MPApiException api && api.getApiResponse() != null && api.getApiResponse().getContent() != null) {
            return "Mercado Pago contestó HTTP " + api.getStatusCode() + ": " + api.getApiResponse().getContent();
        }
        String mensaje = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        return "No se pudo iniciar el pago: " + mensaje;
    }

    /** detalle_cancelacion admite 300 caracteres. */
    private static String recortarDetalle(String detalle) {
        if (detalle == null) return null;
        return detalle.length() <= 300 ? detalle : detalle.substring(0, 299) + "…";
    }

    /**
     * Devuelve el uso del cupón al cancelar o reembolsar y lo reactiva si se había apagado por agotarse. La
     * resta va en una sentencia para no pisar un uso concurrente.
     */
    private void liberarCupon(Compra compra) {
        Cupon cupon = compra.getCupon();
        if (cupon == null) {
            return;
        }
        cuponService.liberarUso(cupon.getId());
    }

    @Transactional
    @Override
    public Compra reembolsarCompra(Long compraId) {
        Compra compra = compraRepository.findById(compraId)
                .orElseThrow(() -> new IllegalArgumentException("Compra no encontrada ID: " + compraId));

        // Sólo APROBADO: pagado online y sin usar.
        if (compra.getEstado() != EstadoCompra.APROBADO) {
            throw new IllegalStateException(
                    "Sólo se pueden reembolsar compras pagadas online que todavía no fueron utilizadas (estado actual: " + compra.getEstado() + ")");
        }

        List<Long> pagos = compra.pagosMercadoPago();
        if (pagos.isEmpty()) {
            throw new IllegalStateException("La compra no tiene registrado su pago de Mercado Pago: "
                    + "el reembolso hay que hacerlo desde el panel de Mercado Pago.");
        }

        try {
            PaymentClient consulta = new PaymentClient();
            PaymentRefundClient reembolsos = new PaymentRefundClient();
            for (Long pagoId : pagos) {
                // Con varias tarjetas, en un reintento se saltean los pagos ya devueltos.
                if ("refunded".equals(consulta.get(pagoId).getStatus())) {
                    continue;
                }
                reembolsos.refund(pagoId);
            }
        } catch (MPApiException e) {
            // El detalle del error está en la respuesta cruda, no en el mensaje de MPApiException.
            log.error("Error al reembolsar en Mercado Pago la compra ID {} (HTTP {}): {}",
                    compraId, e.getStatusCode(), e.getApiResponse() != null ? e.getApiResponse().getContent() : "sin detalle", e);
            throw new IllegalStateException("No se pudo procesar el reembolso en Mercado Pago. Reintentá en unos minutos.");
        } catch (MPException e) {
            log.error("Error al reembolsar en Mercado Pago la compra ID {}", compraId, e);
            throw new IllegalStateException("No se pudo procesar el reembolso en Mercado Pago. Reintentá en unos minutos.");
        }

        compra.setEstado(EstadoCompra.REEMBOLSADA);
        AuditoriaContexto.referencia("#" + compra.getCodigoReserva());
        AuditoriaContexto.detalle("Monto reembolsado: $" + compra.getMontoTotal().stripTrailingZeros().toPlainString());
        // Si estaba facturada: nota de crédito por el total (o anulación si no llegó a ARCA).
        facturaService.alCancelarVenta(compra);
        return compraRepository.save(compra);
    }

    @Transactional
    @Override
    public Compra cancelarReservaAdmin(Long compraId) {
        Compra compra = compraRepository.findById(compraId)
                .orElseThrow(() -> new IllegalArgumentException("Compra no encontrada ID: " + compraId));
        if (compra.getFormaPago() != FormaPago.RESERVA_ADMIN) {
            throw new IllegalArgumentException("Sólo se cancelan por acá las reservas generadas desde Acciones.");
        }
        if (compra.getEstado() != EstadoCompra.APROBADO) {
            throw new IllegalStateException(
                    "Sólo se pueden cancelar reservas que todavía no fueron utilizadas (estado actual: " + compra.getEstado() + ")");
        }
        AuditoriaContexto.referencia("#" + compra.getCodigoReserva());
        compra.cancelar(MotivoCancelacion.RESERVA_CANCELADA, null, usuarioActual());
        facturaService.alCancelarVenta(compra);
        return compraRepository.save(compra);
    }

    @Transactional
    @Override
    public Compra cambiarFechaVisita(Long compraId, LocalDate nuevaFecha, boolean avisarPorMail) {
        Compra compra = compraRepository.findById(compraId)
                .orElseThrow(() -> new IllegalArgumentException("Compra no encontrada ID: " + compraId));
        if (nuevaFecha == null) {
            throw new IllegalArgumentException("Elegí el día nuevo.");
        }
        // Sólo anticipadas que todavía no entraron: una venta de puerta ya se usó en el momento.
        if (compra.getEstado() != EstadoCompra.APROBADO && compra.getEstado() != EstadoCompra.RESERVADO_EFECTIVO) {
            throw new IllegalStateException("Sólo se puede cambiar la fecha de una reserva que todavía no ingresó.");
        }
        if (compra.getFechaVisita() == null && compra.getReceptorDni() != null) {
            throw new IllegalStateException("Un regalo no tiene fecha: la elige quien lo recibe.");
        }
        if (nuevaFecha.equals(compra.getFechaVisita())) {
            throw new IllegalArgumentException("La reserva ya es para ese día.");
        }
        if (nuevaFecha.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("No se puede pasar una reserva a un día que ya pasó.");
        }
        DateTimeFormatter formato = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        Boolean abierto = diaAperturaService.getAbiertoByDate(nuevaFecha);
        if (abierto == null || !abierto) {
            throw new IllegalArgumentException("El parque está cerrado el " + nuevaFecha.format(formato) + ".");
        }

        // Cupo diario del día nuevo: lo que ya hay ese día más las entradas de esta reserva.
        Map<Long, Integer> vendidoEseDia = cantidadVendidaPorTipoEnElDia(nuevaFecha, compraId);
        for (CompraDetalle det : compra.getDetalles()) {
            TipoEntrada tipo = det.getTipoEntrada();
            if (tipo == null || tipo.getMaximoPorDia() == null) continue;
            int total = vendidoEseDia.merge(tipo.getId(), det.getCantidad(), Integer::sum);
            if (total > tipo.getMaximoPorDia()) {
                throw new IllegalArgumentException("No hay cupo de " + tipo.getNombre() + " ese día (máximo "
                        + tipo.getMaximoPorDia() + " por día).");
            }
        }

        AuditoriaContexto.referencia("#" + compra.getCodigoReserva());
        AuditoriaContexto.detalle((compra.getFechaVisita() != null ? compra.getFechaVisita().format(formato) : "Sin fecha")
                + " → " + nuevaFecha.format(formato));
        compra.setFechaVisita(nuevaFecha);
        Compra guardada = compraRepository.save(compra);

        if (avisarPorMail) {
            // Después del commit: el mail lee la compra desde otro hilo.
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        emailService.enviarComprobanteCompra(compraId);
                    }
                });
            } else {
                emailService.enviarComprobanteCompra(compraId);
            }
        }
        return guardada;
    }

    @Transactional
    @Override
    public Compra cancelarVenta(Long compraId, String motivo) {
        // Opcional: si se escribe, queda en la compra (Reportes > Cancelaciones) y en la auditoría.
        String detalle = motivo == null || motivo.isBlank() ? null : motivo.trim();
        if (detalle != null && detalle.length() > 300) {
            throw new IllegalArgumentException("El motivo es muy largo: hasta 300 caracteres.");
        }
        Compra compra = compraRepository.findById(compraId)
                .orElseThrow(() -> new IllegalArgumentException("Compra no encontrada ID: " + compraId));
        if (compra.getCaja() == null) {
            throw new IllegalArgumentException("Esta compra no pasó por una caja: no se cancela desde acá.");
        }
        if (compra.getEstado() == EstadoCompra.CANCELADO) {
            throw new IllegalStateException("Esta venta ya está cancelada.");
        }
        AuditoriaContexto.referencia("#" + compra.getCodigoReserva());
        AuditoriaContexto.detalle("Monto: " + "$" + compra.getMontoTotal().stripTrailingZeros().toPlainString() + " · " + compra.getFormaPago()
                + (detalle != null ? " · Motivo: " + detalle : ""));
        // Pagada con el Point: se le devuelve la plata al cliente. Si MP no la acepta, no se cancela.
        cobroPointService.devolverDeVenta(compra);
        compra.cancelar(MotivoCancelacion.ANULADA_EN_CAJA, detalle, usuarioActual());
        // Si estaba facturada: nota de crédito (o anulación si la factura no llegó a ARCA).
        facturaService.alCancelarVenta(compra);
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
        BigDecimal montoAnterior = compra.getMontoTotal();
        FormaPago formaAnterior = compra.getFormaPago();
        String itemsAnteriores = resumenItems(compra.getDetalles());
        AuditoriaContexto.referencia("#" + compra.getCodigoReserva());

        LocalDate fechaVisita = compra.getFechaVisita();
        // Cupo sin contar esta misma compra.
        Map<Long, Integer> cantidadVendidaPorTipo = cantidadVendidaPorTipoEnElDia(fechaVisita, compraId);

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

        DetallesCalculados calculoArticulos = construirLineasArticulos(request.getArticulos());
        List<CompraDetalle> nuevosArticulos = calculoArticulos.detalles();
        BigDecimal montoArticulos = calculoArticulos.montoTotal();

        List<CompraDetalle> todosLosDetalles = new ArrayList<>(nuevasEntradas);
        todosLosDetalles.addAll(nuevosArticulos);
        if (todosLosDetalles.isEmpty()) {
            throw new IllegalArgumentException("La venta no puede quedar sin entradas ni artículos.");
        }
        validarPaseObligatorio(todosLosDetalles);

        BigDecimal montoBruto = montoEntradas.add(montoArticulos);
        // El descuento se mantiene como monto fijo; sólo se acota si el nuevo bruto es menor.
        BigDecimal descuento = compra.getDescuentoAplicado() != null ? compra.getDescuentoAplicado() : BigDecimal.ZERO;
        if (descuento.compareTo(montoBruto) > 0) descuento = montoBruto;
        BigDecimal montoFinal = montoBruto.subtract(descuento);
        validarFormaPagoPos(request.getFormaPago(), montoFinal);

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

        // orphanRemoval: vaciar la colección borra todas las líneas viejas.
        compra.getDetalles().clear();
        compra.getDetalles().addAll(todosLosDetalles);
        for (CompraDetalle det : todosLosDetalles) {
            det.setCompra(compra);
        }
        compra.setFormaPago(request.getFormaPago());
        compra.setMontoTotal(montoFinal);
        compra.setDescuentoAplicado(descuento);
        // Lo que cobró el Point no se puede cambiar editando: ver CobroPointService#validarEdicion.
        cobroPointService.validarEdicion(compra);

        Compra guardada = compraRepository.save(compra);
        AuditoriaContexto.cambio("Total", montoAnterior, guardada.getMontoTotal());
        AuditoriaContexto.cambio("Forma de pago", formaAnterior, guardada.getFormaPago());
        AuditoriaContexto.cambio("Ítems", itemsAnteriores, resumenItems(guardada.getDetalles()));
        // Si cambió el total de una venta facturada: nota de crédito + factura nueva.
        facturaService.alEditarVenta(guardada, montoAnterior);
        return guardada;
    }

    /** "2× General, 1× Gorra" para el antes/después del historial. */
    private static String resumenItems(List<CompraDetalle> detalles) {
        if (detalles == null || detalles.isEmpty()) return "(ninguno)";
        return detalles.stream()
                .map(d -> d.getCantidad() + "× " + (d.getTipoEntrada() != null ? d.getTipoEntrada().getNombre()
                        : d.getArticuloVario() != null ? d.getArticuloVario().getNombre()
                        : java.util.Objects.requireNonNullElse(d.getDescripcionLibre(), "Artículo")))
                .collect(Collectors.joining(", "));
    }

    private static String usuarioActual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getPrincipal() instanceof UsuarioAutenticado u ? u.username() : USUARIO_SISTEMA;
    }
}
