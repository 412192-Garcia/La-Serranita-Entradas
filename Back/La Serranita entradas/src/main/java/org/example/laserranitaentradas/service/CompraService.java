package org.example.laserranitaentradas.service;

import org.example.laserranitaentradas.model.dto.CompraRequestDTO;
import org.example.laserranitaentradas.model.entity.Compra;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CompraService {
    Optional<Compra> obtenerCompraPorId(Long id);
    Optional<Compra> obtenerCompraPorDniAndFechaVisita(String dni, LocalDate fechaVisita);
    List<Compra> obtenerComprasPorDni(String dni);
    List<Compra> obtenerTodasCompras();
    Compra crearCompra(CompraRequestDTO Compra);
}
