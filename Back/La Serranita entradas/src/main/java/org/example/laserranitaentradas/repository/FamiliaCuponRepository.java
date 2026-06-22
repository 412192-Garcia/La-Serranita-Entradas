package org.example.laserranitaentradas.repository;

import org.example.laserranitaentradas.model.entity.FamiliaCupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FamiliaCuponRepository extends JpaRepository<FamiliaCupon, Long> {
}

