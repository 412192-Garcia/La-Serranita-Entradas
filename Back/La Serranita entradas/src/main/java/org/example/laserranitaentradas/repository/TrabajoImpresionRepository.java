package org.example.laserranitaentradas.repository;

import org.example.laserranitaentradas.model.entity.EstadoTrabajoImpresion;
import org.example.laserranitaentradas.model.entity.TrabajoImpresion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Los cambios de estado de un trabajo son UPDATEs condicionales y no "leer, cambiar, guardar": el
 * agente puede confirmar un ticket milisegundos después de recibirlo, mientras el despacho todavía
 * está terminando. Con leer-y-guardar, el despacho pisaba el IMPRESO con ENVIADO y la tablet se
 * quedaba esperando un ticket que ya había salido.
 */
@Repository
public interface TrabajoImpresionRepository extends JpaRepository<TrabajoImpresion, Long> {

    List<TrabajoImpresion> findByEstadoInAndImpresoraInOrderByIdAsc(Collection<EstadoTrabajoImpresion> estados, Collection<String> impresoras);

    Optional<TrabajoImpresion> findFirstByFacturaIdOrderByIdDesc(Long facturaId);

    List<TrabajoImpresion> findByFacturaIdIn(Collection<Long> facturaIds);

    /** Se marca ENVIADO antes de mandarlo, y sólo si todavía no tiene resultado. 0 = ya tenía. */
    @Modifying
    @Transactional
    @Query("UPDATE TrabajoImpresion t SET t.estado = :enviado, t.impresora = :impresora, t.fechaEnvio = :ahora " +
            "WHERE t.id = :id AND t.estado IN :sinResultado")
    int marcarEnviado(@Param("id") Long id, @Param("impresora") String impresora, @Param("ahora") LocalDateTime ahora,
                      @Param("enviado") EstadoTrabajoImpresion enviado,
                      @Param("sinResultado") Collection<EstadoTrabajoImpresion> sinResultado);

    /** Falló el envío al agente: vuelve a PENDIENTE, salvo que mientras tanto haya llegado un resultado. */
    @Modifying
    @Transactional
    @Query("UPDATE TrabajoImpresion t SET t.estado = :pendiente WHERE t.id = :id AND t.estado = :enviado")
    int volverAPendiente(@Param("id") Long id, @Param("pendiente") EstadoTrabajoImpresion pendiente,
                         @Param("enviado") EstadoTrabajoImpresion enviado);

    /** Resultado del agente. Un IMPRESO nunca se pisa (puede llegar repetido por un reenvío). */
    @Modifying
    @Transactional
    @Query("UPDATE TrabajoImpresion t SET t.estado = :nuevo, t.error = :error, t.fechaResultado = :ahora " +
            "WHERE t.id = :id AND t.estado <> :impreso")
    int registrarResultado(@Param("id") Long id, @Param("nuevo") EstadoTrabajoImpresion nuevo, @Param("error") String error,
                           @Param("ahora") LocalDateTime ahora, @Param("impreso") EstadoTrabajoImpresion impreso);

    /** Vence los que quedaron sin resultado desde antes de `limite`. */
    @Modifying
    @Transactional
    @Query("UPDATE TrabajoImpresion t SET t.estado = :error, t.error = :mensaje, t.fechaResultado = :ahora " +
            "WHERE t.estado IN :sinResultado AND t.fechaCreacion < :limite")
    int vencer(@Param("limite") LocalDateTime limite, @Param("mensaje") String mensaje, @Param("ahora") LocalDateTime ahora,
               @Param("error") EstadoTrabajoImpresion error,
               @Param("sinResultado") Collection<EstadoTrabajoImpresion> sinResultado);
}
