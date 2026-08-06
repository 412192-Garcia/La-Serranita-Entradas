package org.example.laserranitaentradas.repository;

import org.example.laserranitaentradas.model.entity.Promocion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PromocionRepository extends JpaRepository<Promocion, Long> {
}
