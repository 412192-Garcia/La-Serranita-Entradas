package org.example.laserranitaentradas.service;

import org.example.laserranitaentradas.model.entity.FamiliaCupon;
import org.example.laserranitaentradas.model.dto.CrearFamiliaCuponRequest;

import java.util.List;
import java.util.Optional;

public interface FamiliaCuponService {
    Optional<FamiliaCupon> getById(Long id);
    List<FamiliaCupon> getAll();
    FamiliaCupon create(CrearFamiliaCuponRequest request);
}
