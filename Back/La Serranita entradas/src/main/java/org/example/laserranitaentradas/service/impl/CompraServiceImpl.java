package org.example.laserranitaentradas.service.impl;

import jakarta.transaction.Transactional;
import org.example.laserranitaentradas.model.dto.CompraRequestDTO;
import org.example.laserranitaentradas.model.dto.DetalleCompraDTO;
import org.example.laserranitaentradas.model.dto.ClienteDTO;
import org.example.laserranitaentradas.model.entity.Cliente;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.CompraDetalle;
import org.example.laserranitaentradas.model.entity.Cupon;
import org.example.laserranitaentradas.model.entity.TipoEntrada;
import org.example.laserranitaentradas.repository.CompraRepository;
import org.example.laserranitaentradas.service.ClienteService;
import org.example.laserranitaentradas.service.CompraService;
import org.example.laserranitaentradas.service.CuponService;
import org.example.laserranitaentradas.service.DiaAperturaService;
import org.example.laserranitaentradas.service.TipoEntradaService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class CompraServiceImpl implements CompraService {

    private final CompraRepository compraRepository;
    private final TipoEntradaService tipoEntradaService;
    private final CuponService cuponService;
    private final DiaAperturaService diaAperturaService;
    private final ClienteService clienteService;

    public CompraServiceImpl(CompraRepository compraRepository, TipoEntradaService tipoEntradaService, CuponService cuponService, DiaAperturaService diaAperturaService, ClienteService clienteService) {
        this.compraRepository = compraRepository;
        this.tipoEntradaService = tipoEntradaService;
        this.cuponService = cuponService;
        this.diaAperturaService = diaAperturaService;
        this.clienteService = clienteService;
    }

    @Transactional
    @Override
    public Compra crearCompra(CompraRequestDTO compraRequest) {

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
        if (clienteDTO != null && clienteDTO.getDNI() != null) {
            String dniStr = String.valueOf(clienteDTO.getDNI());
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
            cupon = cuponService.obtenerCuponPorCodigo(compraRequest.getCuponCodigo()).orElse(null);
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

                BigDecimal precio = tipoEntrada.getPrecio();
                BigDecimal cantidad = BigDecimal.valueOf(d.getCantidad());
                BigDecimal subtotal = precio.multiply(cantidad);
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

            //TODO REGISTRAR USO DEL CUPON

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
                .build();


        for (CompraDetalle det : detalles) {
            det.setCompra(nuevaCompra);
        }

        return compraRepository.save(nuevaCompra);
    }

    @Override
    public Optional<Compra> obtenerCompraPorId(Long id) {
        return compraRepository.findById(id);
    }

    @Override
    public Optional<Compra> obtenerCompraPorDniAndFechaVisita(String dni, LocalDate fechaVisita) {
        return compraRepository.findByClienteDniAndFechaVisita(dni,fechaVisita);
    }

    @Override
    public List<Compra> obtenerComprasPorDni(String dni) {
        return compraRepository.findAllByClienteDni(dni);
    }

    @Override
    public List<Compra> obtenerTodasCompras() {
        return compraRepository.findAll();
    }

}
