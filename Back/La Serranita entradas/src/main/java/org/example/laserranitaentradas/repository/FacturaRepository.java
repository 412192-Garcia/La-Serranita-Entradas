package org.example.laserranitaentradas.repository;

import org.example.laserranitaentradas.model.entity.EstadoFactura;
import org.example.laserranitaentradas.model.entity.Factura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FacturaRepository extends JpaRepository<Factura, Long> {

    Optional<Factura> findByCompraId(Long compraId);

    boolean existsByCompraId(Long compraId);

    /** Pendientes cuyo próximo intento ya llegó, de la más vieja a la más nueva (así se respeta
     * el orden de venta en la numeración, dentro de lo posible). */
    @Query("SELECT f.id FROM Factura f WHERE f.estado = :estado " +
            "AND (f.proximoIntento IS NULL OR f.proximoIntento <= :ahora) ORDER BY f.id")
    List<Long> idsListosParaEmitir(@Param("estado") EstadoFactura estado, @Param("ahora") LocalDateTime ahora);
}
