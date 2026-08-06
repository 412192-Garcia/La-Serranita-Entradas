package org.example.laserranitaentradas.repository;

import org.example.laserranitaentradas.model.entity.ArticuloVario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ArticuloVarioRepository extends JpaRepository<ArticuloVario, Long> {
}
