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
     * Consume un uso del cupón, pero sólo si todavía queda alguno. Chequeo e incremento pasan
     * a ser la MISMA sentencia: la base evalúa el WHERE sobre la fila que acaba de bloquear,
     * así que dos compras simultáneas no pueden pasar las dos.
     *
     * Antes esto eran dos pasos separados (leer usosActuales, validar, y más abajo escribir
     * usosActuales + 1). Con diez compras a la vez, las diez leían 0, las diez pasaban la
     * validación y las diez se llevaban el descuento: un cupón de un solo uso se canjeaba diez
     * veces y el contador terminaba en 1, sin ningún error ni log que lo delatara.
     *
     * Devuelve la cantidad de filas afectadas: 1 si se pudo consumir, 0 si el cupón ya estaba
     * agotado, inactivo o vencido — quien llama tiene que rechazar la compra en ese caso.
     */
    /* flushAutomatically sí, clearAutomatically NO: limpiar el contexto acá desprendería el
       cliente y los tipos de entrada que create() ya tiene cargados y todavía usa para armar
       la compra. No hace falta: después de esto nadie vuelve a leer los contadores del cupón
       en memoria, y como ya no se lo modifica desde Java, el dirty checking no puede pisar
       el incremento que hizo esta sentencia. */
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE Cupon c
               SET c.usosActuales = c.usosActuales + 1,
                   c.activo = CASE WHEN c.usosActuales + 1 >= c.usosMaximos THEN false ELSE c.activo END
             WHERE c.id = :id
               AND c.activo = true
               AND c.usosActuales < c.usosMaximos
               AND c.fechaExpiracion >= :hoy
            """)
    int consumirUso(@Param("id") Long id, @Param("hoy") LocalDate hoy);

    /**
     * Devuelve un uso del cupón, cuando la compra que lo había consumido se cancela o se
     * reembolsa. Si al liberarlo vuelve a haber lugar y no está vencido, se reactiva (un cupón
     * se apaga solo al agotarse; ver consumirUso).
     *
     * Atómico por el mismo motivo que consumirUso, pero al revés: antes esto se hacía leyendo
     * la entidad, restándole uno en Java y guardándola entera. Si en el medio otra compra
     * consumía un uso, ese save pisaba el incremento con un valor viejo — el uso de esa otra
     * compra desaparecía del contador y el cupón podía revivir antes de tiempo.
     *
     * El guard usosActuales > 0 lo hace idempotente: liberar dos veces no deja el contador en
     * negativo ni regala usos.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE Cupon c
               SET c.usosActuales = c.usosActuales - 1,
                   c.activo = CASE WHEN c.usosActuales - 1 < c.usosMaximos
                                    AND c.fechaExpiracion >= :hoy
                                   THEN true ELSE c.activo END
             WHERE c.id = :id
               AND c.usosActuales > 0
            """)
    int liberarUso(@Param("id") Long id, @Param("hoy") LocalDate hoy);
}

