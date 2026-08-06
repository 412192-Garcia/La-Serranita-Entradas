package org.example.laserranitaentradas.service;

import org.example.laserranitaentradas.model.dto.CrearPromocionRequest;
import org.example.laserranitaentradas.model.entity.Promocion;

import java.util.List;
import java.util.Optional;

public interface PromocionService {
    List<Promocion> getAll();
    Optional<Promocion> findById(Long id);
    Promocion create(CrearPromocionRequest request);
    Promocion update(Long id, CrearPromocionRequest request);
    /** Baja lógica: desactiva en vez de borrar (para reactivarla se usa update con activo=true). */
    void delete(Long id);
}
