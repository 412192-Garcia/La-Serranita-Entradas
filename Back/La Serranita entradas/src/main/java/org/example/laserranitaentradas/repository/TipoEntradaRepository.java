package org.example.laserranitaentradas.repository;

import org.example.laserranitaentradas.model.entity.TipoEntrada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TipoEntradaRepository extends JpaRepository<TipoEntrada, Long> {
    Optional<TipoEntrada> findByNombre(String nombre);

    /** El id como desempate estable para los que todavía no tienen orden asignado (orden=null, Postgres los deja al final en ASC). */
    List<TipoEntrada> findAllByOrderByOrdenAscIdAsc();
}
