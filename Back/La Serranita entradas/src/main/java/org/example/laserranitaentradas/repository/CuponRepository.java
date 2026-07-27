package org.example.laserranitaentradas.repository;

import org.example.laserranitaentradas.model.entity.Cupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CuponRepository extends JpaRepository<Cupon, Long> {
    Optional<Cupon> findByCodigo(String codigo);
    /** Cupones creados de forma individual, sin pertenecer a ningún lote/familia. */
    List<Cupon> findAllByFamiliaCuponIsNull();
}

