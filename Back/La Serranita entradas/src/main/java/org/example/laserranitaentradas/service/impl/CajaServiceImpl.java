package org.example.laserranitaentradas.service.impl;

import jakarta.transaction.Transactional;
import org.example.laserranitaentradas.model.dto.CajaResponseDTO;
import org.example.laserranitaentradas.model.dto.RetiroCajaResponseDTO;
import org.example.laserranitaentradas.model.entity.Caja;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.EstadoCompra;
import org.example.laserranitaentradas.model.entity.FormaPago;
import org.example.laserranitaentradas.model.entity.RetiroCaja;
import org.example.laserranitaentradas.model.entity.Usuario;
import org.example.laserranitaentradas.repository.CajaRepository;
import org.example.laserranitaentradas.repository.CompraRepository;
import org.example.laserranitaentradas.repository.RetiroCajaRepository;
import org.example.laserranitaentradas.service.CajaService;
import org.example.laserranitaentradas.service.UsuarioService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class CajaServiceImpl implements CajaService {

    private final CajaRepository cajaRepository;
    private final RetiroCajaRepository retiroCajaRepository;
    private final CompraRepository compraRepository;
    private final UsuarioService usuarioService;

    public CajaServiceImpl(CajaRepository cajaRepository,
                            RetiroCajaRepository retiroCajaRepository,
                            CompraRepository compraRepository,
                            UsuarioService usuarioService) {
        this.cajaRepository = cajaRepository;
        this.retiroCajaRepository = retiroCajaRepository;
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
    public CajaResponseDTO abrir(Long usuarioId, BigDecimal montoInicial) {
        if (montoInicial == null || montoInicial.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El monto inicial no puede ser negativo");
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
    public CajaResponseDTO cerrar(Long usuarioId, BigDecimal montoContado) {
        if (montoContado == null || montoContado.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El monto contado no puede ser negativo");
        }

        Caja caja = getAbiertaOrThrow(usuarioId);

        BigDecimal totalVentasEfectivo = sumVentasEfectivo(caja.getId());
        BigDecimal totalRetiros = sumRetiros(caja.getId());
        BigDecimal montoEsperado = caja.getMontoInicial().add(totalVentasEfectivo).subtract(totalRetiros);

        caja.setFechaCierre(LocalDateTime.now());
        caja.setMontoContado(montoContado);
        caja.setMontoEsperado(montoEsperado);
        caja.setDiferencia(montoContado.subtract(montoEsperado));

        return toDto(cajaRepository.save(caja));
    }

    private BigDecimal sumVentasEfectivo(Long cajaId) {
        return compraRepository.findAllByCajaId(cajaId).stream()
                .filter(c -> c.getFormaPago() == FormaPago.EFECTIVO_BOLETERIA && c.getEstado() != EstadoCompra.CANCELADO)
                .map(Compra::getMontoTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumRetiros(Long cajaId) {
        return retiroCajaRepository.findAllByCajaIdOrderByFechaAsc(cajaId).stream()
                .map(RetiroCaja::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private CajaResponseDTO toDto(Caja caja) {
        // Mientras la caja sigue abierta, el total vendido en efectivo y el esperado NO
        // se exponen: si el boletero pudiera verlos antes de cerrar, alcanzaría con anotar
        // ese mismo número como "contado" para que el cierre le dé perfecto aunque haya
        // plata de menos. Recién se revelan como parte de la respuesta del cierre.
        boolean cerrada = caja.getFechaCierre() != null;
        BigDecimal totalRetiros = sumRetiros(caja.getId());
        BigDecimal totalVentasEfectivo = null;
        BigDecimal efectivoEsperado = null;
        if (cerrada) {
            totalVentasEfectivo = sumVentasEfectivo(caja.getId());
            efectivoEsperado = caja.getMontoInicial().add(totalVentasEfectivo).subtract(totalRetiros);
        }

        List<RetiroCajaResponseDTO> retiros = retiroCajaRepository.findAllByCajaIdOrderByFechaAsc(caja.getId()).stream()
                .map(r -> RetiroCajaResponseDTO.builder()
                        .id(r.getId())
                        .monto(r.getMonto())
                        .motivo(r.getMotivo())
                        .fecha(r.getFecha())
                        .build())
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
                .build();
    }
}
