package org.example.laserranitaentradas.service;

import org.example.laserranitaentradas.model.dto.CrearArticuloVarioRequest;
import org.example.laserranitaentradas.model.entity.ArticuloVario;

import java.util.List;
import java.util.Optional;

public interface ArticuloVarioService {
    List<ArticuloVario> getAll();
    Optional<ArticuloVario> findById(Long id);
    ArticuloVario create(CrearArticuloVarioRequest request);
    ArticuloVario update(Long id, CrearArticuloVarioRequest request);
    /** Baja lógica: desactiva en vez de borrar (para reactivarlo se usa update con activo=true). */
    void delete(Long id);
}
