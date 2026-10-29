package org.example.laserranitaentradas.repository;

import org.example.laserranitaentradas.model.entity.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    /** Puede haber varios: el DNI no es único porque uno mal tipeado puede coincidir con otra persona. */
    List<Cliente> findAllByDni(String dni);
}

