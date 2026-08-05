package org.example.laserranitaentradas.repository;

import org.example.laserranitaentradas.model.entity.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    /**
     * Puede haber más de un cliente con el mismo DNI: no hay una restricción de unicidad
     * porque un DNI mal tipeado puede coincidir por casualidad con el de otra persona real
     * (ver CompraServiceImpl.create, que decide con cuál de éstos quedarse según el nombre).
     */
    List<Cliente> findAllByDni(String dni);
}

