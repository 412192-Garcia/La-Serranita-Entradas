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
import org.example.laserranitaentradas.repository.CajaRepository;
import org.example.laserranitaentradas.repository.CompraRepository;
import org.example.laserranitaentradas.repository.CompraSpecifications;
import org.example.laserranitaentradas.repository.PromocionRepository;
import org.example.laserranitaentradas.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
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
    private final CajaRepository cajaRepository;
    private final PromocionRepository promocionRepository;
    private final ArticuloVarioRepository articuloVarioRepository;
    private final Map<FormaPago, PagoService> estrategiasPago;
    private final EntityManager em;
    /** El propio bean, pero visto a través del proxy de Spring: es la única forma de que
     *  @Transactional valga en una llamada de un método de esta clase a otro. @Lazy corta el
     *  ciclo de dependencia que si no tendría consigo mismo al construirse. */
    private final CompraService self;

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
             List<PagoService> estrategiasDisponibles,
             EntityManager em,
             @Lazy CompraService self)
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
        this.estrategiasPago = estrategiasDisponibles.stream()
                .collect(Collectors.toMap(PagoService::getFormaPago, estrategia -> estrategia));
        this.em = em;
        this.self = self;
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

    /**
     * Valida el cobro en dólares de una venta de puerta y devuelve los dólares que entregó el
     * cliente, o null si el pago fue en pesos (el request no trae cotización). Pagar en dólares
     * sigue siendo un cobro EFECTIVO_BOLETERIA: sólo cambia la moneda física, y el boletero
     * declara los dólares recibidos (no el vuelto: el vuelto en pesos se deriva de
     * dolaresRecibidos × cotización − montoFinal) para poder contarlos al cerrar la caja.
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

    /**
     * SIN_COBRO es sólo para ventas de puerta cuyo total quedó en $0: si hay algo que cobrar,
     * el boletero tiene que elegir efectivo/tarjeta/QR. (No se fuerza al revés: una venta de $0
     * cobrada como "efectivo" sigue siendo válida, sólo suma 0.)
     */
    private void validarFormaPagoPos(FormaPago formaPago, BigDecimal montoFinal) {
        if (formaPago == FormaPago.SIN_COBRO && montoFinal.compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalArgumentException("Esta venta tiene un monto a cobrar: elegí efectivo, tarjeta o QR.");
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
     * A propósito SIN @Transactional: `create` ya abre y cierra la suya, y lo que sigue —
     * armar la preferencia en Mercado Pago — es una llamada HTTP a un tercero. Tenerla dentro
     * de la transacción retenía una conexión del pool durante todo el viaje de red (o durante
     * el timeout, cuando Mercado Pago no responde) y, peor, hacía que el lock que serializa la
     * numeración del código de reserva se soltara recién después de esa llamada.
     *
     * Si Mercado Pago falla, la compra queda en PENDIENTE_PAGO en vez de deshacerse. Eso no es
     * un estado nuevo ni huérfano: es exactamente el del cliente que abre el checkout y nunca
     * paga, y ExpiracionCheckoutsScheduler lo cancela y le libera el cupón a las 3 h.
     *
     * `self.create` y no `this.create`: el @Transactional de create lo aplica el proxy de
     * Spring, y una llamada con `this` no pasa por el proxy. Con `this` create se quedaría sin
     * transacción y el lock de la numeración no duraría nada.
     */
    @Override
    public CompraResponseDTO iniciarCompraConPago(CompraRequestDTO compraRequest) throws Exception {

        Compra compraGuardada = self.create(compraRequest);

        PagoService estrategia = resolverEstrategia(compraGuardada.getFormaPago());
        PagoResponseDTO respuestaPago;
        try {
            respuestaPago = estrategia.procesarPago(compraGuardada);
        } catch (Exception e) {
            // Se libera el cupón y el lugar del día en vez de esperar las 3 h del barrido
            // (antes, con todo en una transacción, el rollback lo hacía solo).
            //
            // Una excepción acá NO prueba que Mercado Pago no haya creado la preferencia: un
            // timeout puede caer después de que MP la aceptó. Se compensa igual, por dos
            // razones. Primero, la respuesta que sale de este método es un error, así que el
            // cliente nunca recibe el init_point y no tiene por dónde llegar a pagar esa
            // preferencia. Segundo, si aun así entrara un pago, confirmarAprobado revive una
            // compra CANCELADA a propósito (ver el comentario ahí): la persona termina con su
            // entrada igual, a costa de un lugar de más en el día, que es el error barato.
            // Si alguna vez se saca esa reactivación, hay que sacar también esta compensación.
            liberarReservaNoIniciada(compraGuardada.getId());
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
     * Cancela una compra que se creó pero cuyo pago nunca llegó a iniciarse, y le devuelve al
     * cupón el uso y al día el lugar. Es seguro asumir que no está paga: el cliente nunca
     * recibió el link de Mercado Pago.
     *
     * Si esto llegara a fallar no se propaga: el error que importa es el original (por qué
     * falló el pago), y el barrido de checkouts abandonados igual va a limpiar la compra más
     * tarde. Perder el error real por un problema al compensar sería peor.
     */
    private void liberarReservaNoIniciada(Long compraId) {
        try {
            Compra compra = compraRepository.findById(compraId).orElse(null);
            if (compra == null || compra.getEstado() != EstadoCompra.PENDIENTE_PAGO) {
                return;
            }
            compra.setEstado(EstadoCompra.CANCELADO);
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
        } else {
            // fechaVisita null = sin día fijo. Dos casos:
            //  - Regalo (compra online): quien compra no es quien entra -> hace falta el receptor
            //    (a quién avisar y con qué DNI validar). Tiene que llegar pagado (nunca efectivo:
            //    si no, quien lo recibe termina pagando en la puerta lo que le regalaron).
            //  - Reserva abierta generada por un ADMIN (invitado, premio): el titular es quien
            //    entra, se valida con SU DNI, no hace falta receptor.
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
                // El nombre se valida igual que el DNI: más abajo, si el DNI viene cargado, se
                // crea el Cliente con el nombre tal cual llegó, y Cliente.nombre es NOT NULL.
                // Sin este chequeo, una reserva sin fecha con nombre vacío pasaba la validación
                // y terminaba dando de alta un cliente sin nombre.
                throw new IllegalArgumentException("Indicá el titular (nombre y DNI) para una reserva sin fecha.");
            }
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

        // El cupón se valida en dos pasos a propósito. Acá se lee sólo para poder devolver un
        // mensaje que diga QUÉ pasa (agotado / vencido / inexistente), que es lo que ve el
        // cliente en pantalla. Pero esta lectura NO autoriza nada: entre leerla y guardar la
        // compra puede entrar otra compra con el mismo cupón. La autorización real es el
        // consumo atómico de más abajo (cuponService.consumirUso), que es el que manda.
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
        validarNingunoSoloPos(detalles);

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

            // Un cupón de monto fijo puede tapar el total entero. Mercado Pago no acepta una
            // preferencia por $0, así que esa compra fallaría al pedir el checkout — y hasta
            // hace un rato fallaba DESPUÉS de haber consumido el uso del cupón. Se rechaza
            // antes de tocar nada: un cupón que cubre todo es para la boletería, no para el
            // pago online, que necesita algo que cobrar.
            if (compraRequest.getFormaPago() == FormaPago.MERCADO_PAGO
                    && montoTotal.subtract(descuentoAplicado).compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("El cupón " + compraRequest.getCuponCodigo()
                        + " cubre el total de la compra: no hay importe que pagar online. "
                        + "Usalo en la boletería del parque.");
            }

            // Recién acá se decide de verdad si este cupón se puede usar: una sola sentencia
            // que valida y descuenta a la vez. Si otra compra simultánea se llevó el último
            // uso, devuelve false y la compra se rechaza en vez de regalar el descuento. Va
            // último a propósito: cualquier validación que pueda tirar la compra abajo tiene
            // que estar antes, para no quemar un uso del cupón por una compra que no va a
            // existir.
            if (!cuponService.consumirUso(cupon.getId())) {
                throw new IllegalArgumentException(
                        "El cupón " + compraRequest.getCuponCodigo() + " ya no está disponible.");
            }

            // montoTotal es lo que se le cobra al cliente, no el bruto: si no se resta acá, el
            // cupón queda de adorno. El front ya le muestra el total con el descuento aplicado
            // (ver entradas.ts), así que sin esto veía "$17.150" en pantalla y Mercado Pago le
            // cobraba los "$34.300" de lista. La venta por POS siempre lo hizo así
            // (montoFinal = montoBruto - descuento); era sólo el camino online el que no.
            montoTotal = montoTotal.subtract(descuentoAplicado);

            // El incremento de usosActuales y el apagado del cupón al agotarse ya los hizo
            // consumirUso() en la misma sentencia que autorizó el uso.
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

        // Un regalo queda SIN fechaVisita a propósito, incluso después de canjearse: su
        // recaudación se atribuye a la fecha de compra (la plata entró al venderlo) y su ingreso
        // a la fecha de validación, cada uno a su mes, sin moverse (ver ReporteServiceImpl).

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

        // Vuelve al estado del que salió: RESERVADO_EFECTIVO si el ingreso se cobró en una caja
        // (reserva a pagar en boletería, sea en efectivo, tarjeta o QR), APROBADO si ya estaba
        // paga online (nunca tocó una caja). Al deshacer se saca de la caja: el cierre no debe
        // seguir contándola.
        compra.setEstado(compra.getCaja() != null
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
     * Cantidad ya vendida ese día por tipo de entrada, para chequear contra el cupo diario:
     * suma las líneas de entrada de todas las compras de esa fechaVisita, sin contar lo
     * cancelado, las líneas de artículo vario (sin tipoEntrada) ni las de la compra que se
     * está reemplazando (`excluirCompraId`, cuando se reprecia/edita una venta existente).
     * Devuelve un mapa vacío para los regalos (fechaVisita null): no hay día contra el cual medir.
     */
    private Map<Long, Integer> cantidadVendidaPorTipoEnElDia(LocalDate fechaVisita, Long excluirCompraId) {
        Map<Long, Integer> cantidadVendidaPorTipo = new HashMap<>();
        if (fechaVisita == null) return cantidadVendidaPorTipo;

        // Este conteo se mira y recién después se escribe la compra, así que sin lock dos
        // ventas simultáneas para el último lugar leen las dos "queda 1" y entran las dos.
        // El lock va acá y no en quien valida porque este método es el que alimenta a TODOS
        // los caminos que controlan cupo: compra online, venta en puerta, cobro de una reserva
        // en el POS y edición de una venta. Se libera solo al terminar la transacción y es el
        // mismo que serializa la numeración del código de reserva del día.
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
     * Convierte las líneas del pedido en detalles de compra, cobrando el precio que
     * corresponde a la forma de pago (el precio promocional por grupo sólo existe en
     * efectivo) y verificando contra el cupo diario de cada tipo.
     */
    private DetallesCalculados construirDetalles(List<DetalleCompraDTO> entradas, FormaPago formaPago, LocalDate fechaVisita) {
        return construirDetalles(entradas, formaPago, fechaVisita, null);
    }

    /**
     * `excluirCompraId`: cuando se está reconstruyendo una compra ya existente (cobrar una
     * reserva en el POS), sus líneas actuales NO cuentan para el cupo del día — se están
     * reemplazando, no sumando encima (mismo criterio que editarVenta).
     */
    private DetallesCalculados construirDetalles(List<DetalleCompraDTO> entradas, FormaPago formaPago,
                                                 LocalDate fechaVisita, Long excluirCompraId) {
        // Cupo diario por tipo: se suma lo ya vendido ese día (sin contar lo cancelado)
        // más lo que se está agregando ahora. Los regalos no tienen fecha todavía, así
        // que no hay contra qué día chequear el cupo.
        Map<Long, Integer> cantidadVendidaPorTipo = cantidadVendidaPorTipoEnElDia(fechaVisita, excluirCompraId);

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
     * Defensa en profundidad para la compra pública online: un tipo "Solo POS" ya está
     * filtrado del lado del frontend (seleccion-entradas.ts), pero acá se rechaza igual
     * por si alguien arma el request a mano. No se usa desde crearVenta (POS): ahí vender
     * un tipo "Solo POS" es exactamente el caso de uso que existe para.
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
     * Código visible yyMMdd-N: N es el orden de esta reserva entre todas las que ya
     * existen para ese mismo día de visita. Sin fecha: REGALO-N si es un regalo (tiene
     * receptor), ABIERTA-N si es una reserva sin día generada por un admin.
     *
     * El `count(*) + 1` es correcto sólo si nadie más lo está haciendo al mismo tiempo: dos
     * compras simultáneas leían el mismo conteo, armaban el mismo código y la segunda moría
     * contra el unique de codigo_reserva — se perdía una reserva. El lock de abajo serializa
     * el tramo "contar y usar ese número" entre las compras del mismo prefijo.
     *
     * Contar filas que todavía no están commiteadas no sirve, así que el lock tiene que durar
     * hasta el commit (por eso _xact_: Postgres lo suelta solo ahí, no hace falta liberarlo a
     * mano ni siquiera si la transacción falla). Eso es tolerable únicamente porque este
     * método corre en su propia transacción, corta: el llamado a Mercado Pago quedó fuera a
     * propósito (ver iniciarCompraConPago). Si volviera a quedar adentro, este lock pasaría a
     * retenerse durante toda la llamada HTTP y serializaría las compras del día detrás de
     * ella.
     */
    private String generarCodigoReserva(LocalDate fechaVisita, boolean tieneReceptor) {
        String prefijo = fechaVisita != null
                ? fechaVisita.format(DateTimeFormatter.ofPattern("yyMMdd"))
                : (tieneReceptor ? "REGALO" : "ABIERTA");
        // La clave del lock es un hash del prefijo: si dos prefijos distintos colisionaran,
        // lo único que pasa es que se esperan de más, nunca que se repita un número.
        compraRepository.bloquearFechaDeVisita(prefijo.hashCode());

        long numero = fechaVisita != null
                ? compraRepository.countByFechaVisita(fechaVisita) + 1
                : compraRepository.countByFechaVisitaIsNull() + 1;
        return prefijo + "-" + numero;
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

        Usuario vendedor = usuarioService.obtenerUsuarioPorId(usuarioVendedorId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario vendedor no encontrado para id: " + usuarioVendedorId));

        // Toda venta de puerta se hace contra una caja abierta, sea cual sea la forma de
        // pago: es la que después se cierra y concilia al final del turno.
        Caja caja = cajaService.getAbiertaOrThrow(usuarioVendedorId);

        // Si el boletero cargó una anticipada RESERVADO_EFECTIVO en el POS, esto no crea una
        // compra nueva: reprecia y cierra la reserva existente.
        if (request.getCompraReservadaId() != null) {
            return cobrarReservaComoVentaPos(request, caja, vendedor, idempotencyKey);
        }

        return crearVenta(request, caja, vendedor, idempotencyKey);
    }

    @Transactional
    @Override
    public Compra registrarVentaPosComoAdmin(Long cajaId, VentaPosRequestDTO request) {
        Caja caja = cajaRepository.findById(cajaId)
                .orElseThrow(() -> new IllegalArgumentException("Caja no encontrada para id: " + cajaId));
        if (caja.getFechaCierre() != null) {
            throw new IllegalStateException("Esta caja ya está cerrada");
        }
        // Queda a nombre del boletero dueño de la caja (no del admin que la carga): es una
        // venta que le faltó registrar a esa persona, no una del admin.
        return crearVenta(request, caja, caja.getUsuario(), null);
    }

    /** Arma y guarda la venta contra una caja y un vendedor ya resueltos (turno propio o, vía
     * admin, el de otro boletero). Único idempotencyKey no nulo: el propio POS, contra su cola
     * offline — un admin corrigiendo una caja siempre está conectado. */
    private Compra crearVenta(VentaPosRequestDTO request, Caja caja, Usuario vendedor, String idempotencyKey) {
        if (request.getFormaPago() == null) {
            throw new IllegalArgumentException("Debe indicar la forma de pago del cobro");
        }
        boolean sinEntradas = request.getEntradas() == null || request.getEntradas().isEmpty();
        boolean sinArticulos = request.getArticulos() == null || request.getArticulos().isEmpty();
        if (sinEntradas && sinArticulos) {
            throw new IllegalArgumentException("La venta no tiene entradas ni artículos cargados");
        }

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
        validarFormaPagoPos(request.getFormaPago(), montoFinal);
        // calcularDescuentoPos ya validó que la promo exista y esté activa: se vuelve a buscar
        // acá sólo para poder guardar la referencia en la Compra (ver comentario en Compra.promocion).
        Promocion promocionUsada = request.getPromocionId() != null
                ? promocionRepository.findById(request.getPromocionId()).orElse(null)
                : null;

        BigDecimal cotizacionDolar = request.getCotizacionDolar();
        BigDecimal dolaresRecibidos = validarCobroDolares(request, montoFinal);

        // Venta anónima: no hay cliente ni contacto que cargar (ya están entrando, no hay
        // nada que validar después ni comprobante que mandar). Nace VENDIDO_EN_PUERTA porque
        // el cobro y el ingreso pasan en el mismo acto y nunca fue una reserva anticipada.
        Compra venta = Compra.builder()
                .cliente(null)
                .fechaVisita(hoy)
                .codigoReserva(generarCodigoReserva(hoy, false))
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

    /**
     * Cierra una reserva RESERVADO_EFECTIVO que el boletero cargó y cobró en el POS (desde el
     * panel de anticipadas): reprecia sus líneas según la forma de pago elegida
     * (efectivo/tarjeta/QR o dólares), aplica el descuento que haya cargado el boletero y la
     * marca USADO contra su caja. No crea una compra nueva — mantiene cliente, código y fecha
     * de visita de la reserva.
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

        // Cupo del día contra la fecha de visita de la reserva, sin contar sus propias líneas
        // actuales (se están reemplazando, no sumando encima).
        DetallesCalculados calculoEntradas = construirDetalles(
                request.getEntradas(), request.getFormaPago(), reserva.getFechaVisita(), reserva.getId());
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
        Promocion promocionUsada = request.getPromocionId() != null
                ? promocionRepository.findById(request.getPromocionId()).orElse(null)
                : null;

        BigDecimal cotizacionDolar = request.getCotizacionDolar();
        BigDecimal dolaresRecibidos = validarCobroDolares(request, montoFinal);

        // orphanRemoval en Compra.detalles: vaciar la colección alcanza para que Hibernate
        // borre las líneas viejas antes de cargar las nuevas (mismo criterio que editarVenta).
        reserva.getDetalles().clear();
        reserva.getDetalles().addAll(todosLosDetalles);
        for (CompraDetalle det : todosLosDetalles) {
            det.setCompra(reserva);
        }
        reserva.setMontoTotal(montoFinal);
        reserva.setDescuentoAplicado(descuento);
        reserva.setPromocion(promocionUsada);
        reserva.setFormaPago(request.getFormaPago());
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

        // Ya se le devolvió la plata: un aviso tardío de Mercado Pago no puede revivirla, o el
        // visitante entraría con una entrada reembolsada.
        if (compra.getEstado() == EstadoCompra.REEMBOLSADA) {
            log.error("Llegó una confirmación de pago para la compra ID {}, que está REEMBOLSADA. "
                    + "No se toca: revisar a mano si ese pago hay que devolverlo.", compraId);
            return false;
        }

        // Cancelada y pagada después: pasa cuando el barrido la dio por abandonada y el cliente
        // terminó de pagar igual (la preferencia sigue viva un rato más). Se la revive a
        // propósito: entre dejar a alguien que pagó sin entrada y meter un lugar de más en el
        // día, lo segundo es mucho menos grave. Pero se avisa fuerte, porque el cupo de ese día
        // ya se había liberado y el uso del cupón también.
        if (compra.getEstado() == EstadoCompra.CANCELADO) {
            log.warn("La compra ID {} ({}) estaba CANCELADA por checkout abandonado y llegó el pago: "
                    + "se reactiva. Ojo: su lugar en el cupo del {} ya se había liberado.",
                    compraId, compra.getCodigoReserva(), compra.getFechaVisita());
            // Al cancelarla se le devolvió el uso del cupón, así que hay que volver a tomarlo:
            // si no, ese uso queda disponible para otra compra y el cupón termina aplicado dos
            // veces. Si ya no quedan usos (alguien se lo llevó mientras tanto) igual se aprueba
            // —la persona pagó y tiene que entrar—, pero queda avisado para poder corregirlo.
            if (compra.getCupon() != null && !cuponService.consumirUso(compra.getCupon().getId())) {
                log.error("Se reactivó la compra ID {} pero el cupón {} ya no tenía usos libres: "
                        + "quedó aplicado una vez de más. Revisar a mano.",
                        compraId, compra.getCupon().getCodigo());
            }
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
            if (hayPagoAprobadoEnMercadoPago(compra)) {
                confirmarAprobado(compraId);
            }
        } catch (MPException | MPApiException | RuntimeException e) {
            // No se pudo consultar a Mercado Pago ahora: se deja la compra como está
            // para poder reintentar más tarde (webhook, otra verificación, etc.).
            log.error("Error consultando a Mercado Pago el estado de la compra ID {}", compraId, e);
        }

        return compraRepository.findById(compraId).map(c -> c.getEstado().name()).orElse(compra.getEstado().name());
    }

    /**
     * Le pregunta a Mercado Pago si esta compra tiene un pago aprobado.
     *
     * PROPAGA la excepción si no se pudo consultar, y esa es toda la gracia: quien llama
     * necesita poder distinguir "verifiqué y no pagó" de "no pude verificar". Cuando eso se
     * atrapaba acá adentro, las dos situaciones se veían iguales desde afuera —la compra
     * seguía en PENDIENTE_PAGO— y expirarCheckoutAbandonado terminaba cancelando compras
     * pagadas cada vez que Mercado Pago no contestaba.
     */
    // protected y no private para poder sustituirlo en los tests: la consulta arma un
    // PaymentClient del SDK contra la API real, así que sin este punto de corte no habría forma
    // de probar qué decide expirarCheckoutAbandonado cuando Mercado Pago no responde — que es
    // justo el camino en el que un cliente que pagó se quedaba sin su entrada.
    protected boolean hayPagoAprobadoEnMercadoPago(Compra compra) throws MPException, MPApiException {
        // limit/offset van explícitos: si se dejan sin setear, el SDK arma la URL
        // iterando todos los parámetros y revienta con NullPointerException al
        // encontrar esos dos en null (bug conocido de esta versión del SDK).
        MPSearchRequest searchRequest = MPSearchRequest.builder()
                .filters(Map.of("external_reference", compra.getId().toString()))
                .limit(10)
                .offset(0)
                .build();
        List<Payment> pagos = new PaymentClient().search(searchRequest).getResults();
        // El external_reference identifica la compra, pero no es una clave 100% exclusiva
        // de Mercado Pago (por ejemplo, en un entorno de pruebas donde la base se reinicia
        // y los IDs se reciclan, puede haber un pago viejo con el mismo external_reference).
        // Exigir que el monto coincida evita aprobar una compra por un pago que en realidad
        // es de otra.
        return pagos != null && pagos.stream()
                .anyMatch(p -> "approved".equals(p.getStatus()) && compra.getMontoTotal().compareTo(p.getTransactionAmount()) == 0);
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

        // Última chance: si el pago entró pero el webhook nunca llegó, esto lo confirma y la
        // compra deja de estar PENDIENTE_PAGO (no se cancela).
        if (compra.getFormaPago() == FormaPago.MERCADO_PAGO) {
            try {
                if (hayPagoAprobadoEnMercadoPago(compra)) {
                    confirmarAprobado(compraId);
                    return;
                }
            } catch (MPException | MPApiException | RuntimeException e) {
                // No sabemos si pagó o no. Cancelar acá es la peor opción posible: si había
                // pagado, el cliente se queda sin entrada y sin aviso. Se deja como está y
                // listo — el scheduler vuelve a pasar en el próximo ciclo (cada 15 min por
                // defecto) y la compra, que sigue PENDIENTE_PAGO y vieja, se vuelve a tomar
                // sola. No hace falta reprogramar nada ni marcarla de ninguna forma especial.
                log.warn("No se pudo verificar contra Mercado Pago la compra ID {}: NO se cancela, "
                        + "se reintenta en el próximo ciclo", compraId, e);
                return;
            }
        }

        compra.setEstado(EstadoCompra.CANCELADO);
        liberarCupon(compra);
        compraRepository.save(compra);
        log.info("Compra ID {} cancelada: checkout abandonado sin pagarse", compraId);
    }

    /**
     * Devuelve el uso de cupón que una compra había consumido al crearse (ver create()), cuando
     * esa compra se cancela o se reembolsa. Si el cupón se había desactivado solo por llegar al
     * máximo de usos, se reactiva al liberar uno (mientras no esté vencido).
     *
     * La resta la hace la base en una sentencia (cuponService.liberarUso) y no este método
     * leyendo la entidad y guardándola: si otra compra consumía un uso en el medio, el save
     * pisaba ese incremento con un contador viejo.
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
        // El descuento ya aplicado (promo o manual) se mantiene como monto fijo: no se
        // vuelve a evaluar elegibilidad de promo acá, sólo se lo re-acota si el nuevo bruto
        // quedó más chico que el descuento original.
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

        // orphanRemoval en Compra.detalles: vaciar la colección entera alcanza para que
        // Hibernate borre todas las líneas viejas (entradas y artículos) antes de cargar las nuevas.
        compra.getDetalles().clear();
        compra.getDetalles().addAll(todosLosDetalles);
        for (CompraDetalle det : todosLosDetalles) {
            det.setCompra(compra);
        }
        compra.setFormaPago(request.getFormaPago());
        compra.setMontoTotal(montoFinal);
        compra.setDescuentoAplicado(descuento);

        return compraRepository.save(compra);
    }
}
