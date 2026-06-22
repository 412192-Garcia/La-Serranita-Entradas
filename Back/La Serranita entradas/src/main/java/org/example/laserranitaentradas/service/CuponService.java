package org.example.laserranitaentradas.service;

import org.example.laserranitaentradas.model.dto.CrearCuponRequest;
import org.example.laserranitaentradas.model.entity.Cupon;

import java.util.List;
import java.util.Optional;

public interface CuponService {
    Optional<Cupon> getById(Long id);
    Optional<Cupon> getByCode(String codigo);
    List<Cupon> getAll();
    List<Cupon> getAllActive();
    Cupon create(CrearCuponRequest request);
    Cupon update(Cupon cupon);
}
