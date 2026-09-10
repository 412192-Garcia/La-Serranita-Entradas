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
    /** Cupones creados de forma individual, sin pertenecer a ningún lote/familia. */
    List<Cupon> getAllIndividuales();
    Cupon create(CrearCuponRequest request);
    Cupon update(Cupon cupon);

    /**
     * Consume un uso del cupón de forma atómica. Devuelve true si quedaba disponible (y lo
     * descontó), false si estaba agotado, inactivo o vencido. Es el único punto que puede
     * autorizar el uso de un cupón: validarlo leyéndolo y escribirlo después deja pasar dos
     * compras simultáneas con el mismo cupón de un solo uso.
     */
    boolean consumirUso(Long cuponId);
}
