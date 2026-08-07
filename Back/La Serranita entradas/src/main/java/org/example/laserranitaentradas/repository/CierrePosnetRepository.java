package org.example.laserranitaentradas.repository;

import org.example.laserranitaentradas.model.entity.CierrePosnet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CierrePosnetRepository extends JpaRepository<CierrePosnet, Long> {
    List<CierrePosnet> findAllByCajaIdOrderByIdAsc(Long cajaId);

    /** Usado al corregir un cierre ya hecho: se borran los cierres viejos y se cargan los nuevos de cero. */
    void deleteAllByCajaId(Long cajaId);
}
