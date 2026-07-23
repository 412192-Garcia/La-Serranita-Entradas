package org.example.laserranitaentradas.service.impl;

import jakarta.transaction.Transactional;
import org.example.laserranitaentradas.model.dto.*;
import org.example.laserranitaentradas.model.entity.Cliente;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.CompraDetalle;
import org.example.laserranitaentradas.model.entity.Cupon;
import org.example.laserranitaentradas.model.entity.FormaPago;
import org.example.laserranitaentradas.model.entity.TipoEntrada;
import org.example.laserranitaentradas.model.entity.Usuario;
import org.example.laserranitaentradas.model.entity.EstadoCompra;
import org.example.laserranitaentradas.repository.CompraRepository;
import org.example.laserranitaentradas.service.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CompraServiceImpl implements CompraService {

    private final CompraRepository compraRepository;
    private final TipoEntradaService tipoEntradaService;
    private final CuponService cuponService;
    private final DiaAperturaService diaAperturaService;
    private final ClienteService clienteService;
    private final UsuarioService usuarioService;
    private final CalculoPrecioService calculoPrecioService;
    private final Map<FormaPago, PagoService> estrategiasPago;

    public CompraServiceImpl
            (CompraRepository compraRepository,
             TipoEntradaService tipoEntradaService,
             CuponService cuponService,
             DiaAperturaService diaAperturaService,
             ClienteService clienteService,
             UsuarioService usuarioService,
             CalculoPrecioService calculoPrecioService,
             List<PagoService> estrategiasDisponibles)
    {
        this.compraRepository = compraRepository;
        this.tipoEntradaService = tipoEntradaService;
        this.cuponService = cuponService;
        this.diaAperturaService = diaAperturaService;
        this.clienteService = clienteService;
        this.usuarioService = usuarioService;
        this.calculoPrecioService = calculoPrecioService;
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

        if (fechaVisita == null) {
            throw new IllegalArgumentException("Fecha de visita requerida");
        }

        Boolean abierto = diaAperturaService.getAbiertoByDate(fechaVisita);
        if (abierto == null || !abierto) {
            throw new IllegalArgumentException("El parque está cerrado en la fecha solicitada: " + fechaVisita);
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

                BigDecimal subtotal = calculoPrecioService.calcularTotal(tipoEntrada, d.getCantidad(), compraRequest.getFormaPago());
                montoTotal = montoTotal.add(subtotal);

                CompraDetalle detalle = CompraDetalle.builder()
                        .tipoEntrada(tipoEntrada)
                        .cantidad(d.getCantidad())
                        .build();
                detalles.add(detalle);
            }
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

        Compra nuevaCompra = Compra.builder()
                .cliente(cliente)
                .contactEmail(contactEmail)
                .contactPhone(contactPhone)
                .fechaVisita(fechaVisita)
                .montoTotal(montoTotal)
                .descuentoAplicado(descuentoAplicado)
                .cupon(cupon)
                .detalles(detalles)
                .estado(estrategia.getEstadoInicial())
                .formaPago(compraRequest.getFormaPago())
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
    public List<Compra> getAll() {
        return compraRepository.findAll();
    }

    @Transactional
    @Override
    public Compra marcarEntradasComoUsadas(Long compraId, Long usuarioValidadorId) {

        Compra compra = compraRepository.findById(compraId).orElseThrow(() -> new IllegalArgumentException("Compra no encontrada para id: " + compraId));

        Usuario usuario = usuarioService.obtenerUsuarioPorId(usuarioValidadorId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario validador no encontrado para id: " + usuarioValidadorId));

        compra.setEstado(EstadoCompra.USADO);
        compra.setUsuarioValidador(usuario);

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
}
