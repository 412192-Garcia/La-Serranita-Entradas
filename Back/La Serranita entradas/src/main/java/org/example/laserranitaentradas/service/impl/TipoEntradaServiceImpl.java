package org.example.laserranitaentradas.service.impl;

import org.example.laserranitaentradas.model.entity.TipoEntrada;
import org.example.laserranitaentradas.repository.TipoEntradaRepository;
import org.example.laserranitaentradas.service.TipoEntradaService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TipoEntradaServiceImpl implements TipoEntradaService {

    private final TipoEntradaRepository tipoEntradaRepository;

    public TipoEntradaServiceImpl(TipoEntradaRepository tipoEntradaRepository) {
        this.tipoEntradaRepository = tipoEntradaRepository;
    }

    @Override
    public TipoEntrada create(TipoEntrada tipoEntrada) {
        return tipoEntradaRepository.save(tipoEntrada);
    }

    @Override
    public Optional<TipoEntrada> findById(Long id) {
        return tipoEntradaRepository.findById(id);
    }

    @Override
    public Optional<TipoEntrada> findByName(String nombre) {
        return tipoEntradaRepository.findByNombre(nombre);
    }

    @Override
    public List<TipoEntrada> getAll() {
        return tipoEntradaRepository.findAll();
    }

    @Override
    public TipoEntrada update(TipoEntrada tipoEntrada) {
        return tipoEntradaRepository.save(tipoEntrada);
    }

    @Override
    public void delete(Long id)
    {
        tipoEntradaRepository.findById(id).ifPresent(tipoEntrada -> {
            tipoEntrada.setActivo(false);
            tipoEntradaRepository.save(tipoEntrada);
        });
    }
}
