package org.example.laserranitaentradas.service;

import org.example.laserranitaentradas.model.dto.ActualizarCuponRequest;
import org.example.laserranitaentradas.model.dto.CrearCuponRequest;
import org.example.laserranitaentradas.model.entity.Cupon;

import java.util.List;
import java.util.Optional;

public interface CuponService {
    Optional<Cupon> getById(Long id);
    Optional<Cupon> getByCode(String codigo);
    List<Cupon> getAll();
    List<Cupon> getAllActive();
    /** Cupones creados de forma individual, sin pertenecer a ningún lote/familia. */
    List<Cupon> getAllIndividuales();
    Cupon create(CrearCuponRequest request);
    Cupon update(Cupon cupon);

    /** Cambia usos máximos, vencimiento y activo de un cupón, con las validaciones de coherencia. */
    Cupon actualizar(Long id, ActualizarCuponRequest request);

    /**
     * Consume un uso de forma atómica. false si estaba agotado, inactivo o vencido. Es lo único que autoriza
     * el uso.
     */
    boolean consumirUso(Long cuponId);

    /** Devuelve un uso y reactiva el cupón si vuelve a haber lugar. Idempotente. */
    void liberarUso(Long cuponId);
}
