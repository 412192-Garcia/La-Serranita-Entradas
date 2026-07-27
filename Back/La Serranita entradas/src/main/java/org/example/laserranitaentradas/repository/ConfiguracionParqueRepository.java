package org.example.laserranitaentradas.repository;

import org.example.laserranitaentradas.model.entity.ConfiguracionParque;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConfiguracionParqueRepository extends JpaRepository<ConfiguracionParque, Long> {
}
