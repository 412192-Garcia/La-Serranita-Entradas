package org.example.laserranitaentradas.service;

import org.example.laserranitaentradas.model.dto.CompraRequestDTO;
import org.example.laserranitaentradas.model.dto.CompraResponseDTO;
import org.example.laserranitaentradas.model.dto.CotizacionRequestDTO;
import org.example.laserranitaentradas.model.dto.CotizacionResponseDTO;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.EstadoCompra;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CompraService {
    Optional<Compra> findById(Long id);
    Optional<Compra> findByDniandFecha(String dni, LocalDate fechaVisita);
    List<Compra> getAllByDni(String dni);
    List<Compra> getAllByFechaVisita(LocalDate fechaVisita);
    List<Compra> getAll();
    Compra create(CompraRequestDTO Compra);
    Compra marcarEntradasComoUsadas(Long compraId, Long usuarioValidadorId);
    Compra confirmarPagoEfectivo(Long compraId, Long usuarioValidadorId);
    CotizacionResponseDTO cotizar(CotizacionRequestDTO cotizacionRequest);
    Optional<String> consultarEstadoCompra(Long id);
    Compra actualizarEstado(Long compraId, EstadoCompra nuevoEstado);
    CompraResponseDTO iniciarCompraConPago(CompraRequestDTO compraRequest) throws Exception;
}
