package org.example.laserranitaentradas.service;

import org.example.laserranitaentradas.model.entity.FamiliaCupon;
import java.time.LocalDate;
import org.example.laserranitaentradas.model.dto.CrearFamiliaCuponRequest;

import java.util.List;
import java.util.Optional;

public interface FamiliaCuponService {
    Optional<FamiliaCupon> getById(Long id);
    List<FamiliaCupon> getAll();
    FamiliaCupon create(CrearFamiliaCuponRequest request);

    /** Prende o apaga todos los cupones del lote (al prender, sólo los que todavía pueden usarse). */
    FamiliaCupon cambiarActivo(Long id, boolean activo);

    /** Cambia el vencimiento de todos los cupones del lote; null = sin vencimiento. */
    FamiliaCupon cambiarVencimiento(Long id, LocalDate fechaExpiracion);
}
