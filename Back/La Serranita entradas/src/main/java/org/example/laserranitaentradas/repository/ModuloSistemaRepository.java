package org.example.laserranitaentradas.repository;

import org.example.laserranitaentradas.model.entity.ModuloSistema;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ModuloSistemaRepository extends JpaRepository<ModuloSistema, String> {
}
