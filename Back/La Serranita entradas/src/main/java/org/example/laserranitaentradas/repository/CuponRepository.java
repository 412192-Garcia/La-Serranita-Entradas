package org.example.laserranitaentradas.repository;

import org.example.laserranitaentradas.model.entity.Cupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CuponRepository extends JpaRepository<Cupon, Long> {
    Optional<Cupon> findByCodigo(String codigo);
    /** Cupones creados de forma individual, sin pertenecer a ningún lote/familia. */
    List<Cupon> findAllByFamiliaCuponIsNull();

    /**
     * Consume un uso sólo si queda alguno: chequeo e incremento en la misma sentencia, así dos compras
     * simultáneas no pasan las dos. Devuelve 1 si se pudo, 0 si estaba agotado, inactivo o vencido.
     */
    // Sin clearAutomatically: desprendería entidades que create() todavía usa.
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE Cupon c
               SET c.usosActuales = c.usosActuales + 1,
                   c.activo = CASE WHEN c.usosMaximos IS NOT NULL AND c.usosActuales + 1 >= c.usosMaximos THEN false ELSE c.activo END
             WHERE c.id = :id
               AND c.activo = true
               AND (c.usosMaximos IS NULL OR c.usosActuales < c.usosMaximos)
               AND (c.fechaExpiracion IS NULL OR c.fechaExpiracion >= :hoy)
               AND (c.fechaDesde IS NULL OR c.fechaDesde <= :hoy)
            """)
    int consumirUso(@Param("id") Long id, @Param("hoy") LocalDate hoy);

    /**
     * Devuelve un uso al cancelar o reembolsar, y reactiva el cupón si vuelve a haber lugar y no venció.
     * Atómico para no pisar un uso concurrente; el guard usosActuales > 0 lo hace idempotente.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE Cupon c
               SET c.usosActuales = c.usosActuales - 1,
                   c.activo = CASE WHEN (c.usosMaximos IS NULL OR c.usosActuales - 1 < c.usosMaximos)
                                    AND (c.fechaExpiracion IS NULL OR c.fechaExpiracion >= :hoy)
                                   THEN true ELSE c.activo END
             WHERE c.id = :id
               AND c.usosActuales > 0
            """)
    int liberarUso(@Param("id") Long id, @Param("hoy") LocalDate hoy);

    /** Cambia usos máximos, vencimiento y activo sin tocar usosActuales. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Cupon c
               SET c.usosMaximos = :usosMaximos,
                   c.fechaExpiracion = :fechaExpiracion,
                   c.activo = :activo
             WHERE c.id = :id
            """)
    int actualizarVigencia(@Param("id") Long id, @Param("usosMaximos") Integer usosMaximos,
                           @Param("fechaExpiracion") LocalDate fechaExpiracion, @Param("activo") boolean activo);

    /** Apaga todos los cupones de un lote. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Cupon c SET c.activo = false WHERE c.familiaCupon.id = :familiaId")
    int desactivarFamilia(@Param("familiaId") Long familiaId);

    /** Prende los cupones del lote que todavía se pueden usar. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Cupon c
               SET c.activo = true
             WHERE c.familiaCupon.id = :familiaId
               AND (c.usosMaximos IS NULL OR c.usosActuales < c.usosMaximos)
               AND (c.fechaExpiracion IS NULL OR c.fechaExpiracion >= :hoy)
            """)
    int activarFamilia(@Param("familiaId") Long familiaId, @Param("hoy") LocalDate hoy);

    /** Cambia el vencimiento de todos los cupones de un lote (null = sin vencimiento). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Cupon c SET c.fechaExpiracion = :fechaExpiracion WHERE c.familiaCupon.id = :familiaId")
    int cambiarVencimientoFamilia(@Param("familiaId") Long familiaId, @Param("fechaExpiracion") LocalDate fechaExpiracion);
}
