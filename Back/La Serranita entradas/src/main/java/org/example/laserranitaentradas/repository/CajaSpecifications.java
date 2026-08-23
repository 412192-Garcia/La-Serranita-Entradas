package org.example.laserranitaentradas.repository;

import jakarta.persistence.criteria.Join;
import org.example.laserranitaentradas.model.entity.Caja;
import org.example.laserranitaentradas.model.entity.Usuario;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

/** Predicados reutilizables para el listado paginado de "Cajas cerradas" (GET /api/interno/caja/cerradas). */
public class CajaSpecifications {

    private CajaSpecifications() {}

    public static Specification<Caja> cerradaEntre(LocalDateTime desde, LocalDateTime hasta) {
        return (root, query, cb) -> cb.and(cb.isNotNull(root.get("fechaCierre")), cb.between(root.get("fechaCierre"), desde, hasta));
    }

    /** Mismo criterio de identidad que ya usa el resto de la pantalla de Cajas (nombre + apellido
     * concatenados, ver CajaAbiertaDTO/CajaResumenReporteDTO): no hay un filtro por id de usuario
     * expuesto todavía en esa pantalla, así que se compara contra el texto que ya manda el chip. */
    public static Specification<Caja> deUsuarioNombre(String usuarioNombre) {
        if (usuarioNombre == null || usuarioNombre.isBlank()) return null;
        return (root, query, cb) -> {
            Join<Caja, Usuario> usuario = root.join("usuario");
            return cb.equal(cb.concat(cb.concat(usuario.get("nombre"), " "), usuario.get("apellido")), usuarioNombre);
        };
    }
}
