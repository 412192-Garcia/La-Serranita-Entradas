package org.example.laserranitaentradas.service;

import org.example.laserranitaentradas.model.entity.TipoEntrada;

import java.util.List;
import java.util.Optional;

public interface TipoEntradaService {
    TipoEntrada create(TipoEntrada tipoEntrada);

    Optional<TipoEntrada> findById(Long id);

    Optional<TipoEntrada> findByName(String nombre);

    List<TipoEntrada> getAll();

    TipoEntrada update(TipoEntrada tipoEntrada);

    void delete(Long id);
}

