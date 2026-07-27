package org.example.laserranitaentradas.service.impl;

import org.example.laserranitaentradas.model.dto.CrearDescuentoEfectivoRequest;
import org.example.laserranitaentradas.model.entity.DescuentoEfectivo;
import org.example.laserranitaentradas.model.entity.TipoEntrada;
import org.example.laserranitaentradas.repository.DescuentoEfectivoRepository;
import org.example.laserranitaentradas.service.DescuentoEfectivoService;
import org.example.laserranitaentradas.service.TipoEntradaService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DescuentoEfectivoServiceImpl implements DescuentoEfectivoService {

    private final DescuentoEfectivoRepository descuentoEfectivoRepository;
    private final TipoEntradaService tipoEntradaService;

    public DescuentoEfectivoServiceImpl(DescuentoEfectivoRepository descuentoEfectivoRepository,
                                         TipoEntradaService tipoEntradaService) {
        this.descuentoEfectivoRepository = descuentoEfectivoRepository;
        this.tipoEntradaService = tipoEntradaService;
    }

    @Override
    public List<DescuentoEfectivo> getAll() {
        return descuentoEfectivoRepository.findAll();
    }

    @Override
    public DescuentoEfectivo create(CrearDescuentoEfectivoRequest request) {
        TipoEntrada tipoEntrada = resolverTipoEntrada(request.getTipoEntradaId());

        if (descuentoEfectivoRepository.findByTipoEntradaAndCantidadPases(tipoEntrada, request.getCantidadPases()).isPresent()) {
            throw new IllegalArgumentException(
                    "Ya existe un precio de grupo para " + tipoEntrada.getNombre() + " con " + request.getCantidadPases() + " pases"
            );
        }

        DescuentoEfectivo descuento = new DescuentoEfectivo();
        descuento.setTipoEntrada(tipoEntrada);
        descuento.setCantidadPases(request.getCantidadPases());
        descuento.setPrecioPromocionalTotal(request.getPrecioPromocionalTotal());
        return descuentoEfectivoRepository.save(descuento);
    }

    @Override
    public DescuentoEfectivo update(Long id, CrearDescuentoEfectivoRequest request) {
        DescuentoEfectivo existente = descuentoEfectivoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Descuento por grupo no encontrado para id: " + id));

        existente.setTipoEntrada(resolverTipoEntrada(request.getTipoEntradaId()));
        existente.setCantidadPases(request.getCantidadPases());
        existente.setPrecioPromocionalTotal(request.getPrecioPromocionalTotal());
        return descuentoEfectivoRepository.save(existente);
    }

    @Override
    public void delete(Long id) {
        descuentoEfectivoRepository.deleteById(id);
    }

    private TipoEntrada resolverTipoEntrada(Long tipoEntradaId) {
        return tipoEntradaService.findById(tipoEntradaId)
                .orElseThrow(() -> new IllegalArgumentException("Tipo de entrada no encontrado para id: " + tipoEntradaId));
    }
}
