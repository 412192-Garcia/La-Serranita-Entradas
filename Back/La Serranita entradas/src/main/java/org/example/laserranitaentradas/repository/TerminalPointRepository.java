package org.example.laserranitaentradas.repository;

import org.example.laserranitaentradas.model.entity.TerminalPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TerminalPointRepository extends JpaRepository<TerminalPoint, String> {
}
