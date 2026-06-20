package org.example.laserranitaentradas.service.impl;

import org.example.laserranitaentradas.model.entity.CompraDetalle;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.repository.CompraDetalleRepository;
import org.example.laserranitaentradas.service.CompraDetalleService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CompraDetalleServiceImpl implements CompraDetalleService {

    private final CompraDetalleRepository compraDetalleRepository;

    public CompraDetalleServiceImpl(CompraDetalleRepository compraDetalleRepository) {
        this.compraDetalleRepository = compraDetalleRepository;
    }

    @Override
    public CompraDetalle crearCompraDetalle(CompraDetalle compraDetalle) {
        return compraDetalleRepository.save(compraDetalle);
    }

    @Override
    public Optional<CompraDetalle> obtenerCompraDetallePorId(Long id) {
        return compraDetalleRepository.findById(id);
    }

    @Override
    public List<CompraDetalle> obtenerDetallesPorCompra(Compra compra) {
        return compraDetalleRepository.findByCompra(compra);
    }

    @Override
    public List<CompraDetalle> obtenerTodosCompraDetalles() {
        return compraDetalleRepository.findAll();
    }

    @Override
    public CompraDetalle actualizarCompraDetalle(CompraDetalle compraDetalle) {
        return compraDetalleRepository.save(compraDetalle);
    }

    @Override
    public void eliminarCompraDetalle(Long id) {
        compraDetalleRepository.deleteById(id);
    }
}
