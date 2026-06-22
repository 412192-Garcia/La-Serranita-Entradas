package org.example.laserranitaentradas.service;

import org.example.laserranitaentradas.model.dto.CompraRequestDTO;
import org.example.laserranitaentradas.model.entity.Compra;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CompraService {
    Optional<Compra> findById(Long id);
    Optional<Compra> findByDniandFecha(String dni, LocalDate fechaVisita);
    List<Compra> getAllByDni(String dni);
    List<Compra> getAll();
    Compra create(CompraRequestDTO Compra);
    Compra marcarEntradasComoUsadas(Long compraId, Long usuarioValidadorId);
}
