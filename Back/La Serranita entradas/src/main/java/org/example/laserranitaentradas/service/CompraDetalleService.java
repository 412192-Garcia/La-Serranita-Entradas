package org.example.laserranitaentradas.service;

import org.example.laserranitaentradas.model.entity.CompraDetalle;
import org.example.laserranitaentradas.model.entity.Compra;

import java.util.List;
import java.util.Optional;

public interface CompraDetalleService {
    CompraDetalle crearCompraDetalle(CompraDetalle compraDetalle);
    Optional<CompraDetalle> obtenerCompraDetallePorId(Long id);
    List<CompraDetalle> obtenerDetallesPorCompra(Compra compra);
    List<CompraDetalle> obtenerTodosCompraDetalles();
    CompraDetalle actualizarCompraDetalle(CompraDetalle compraDetalle);
    void eliminarCompraDetalle(Long id);
}

