package org.example.laserranitaentradas.repository;

import jakarta.persistence.criteria.Join;
import org.example.laserranitaentradas.model.entity.Caja;
import org.example.laserranitaentradas.model.entity.Usuario;
import org.springframework.data.jpa.domain.Specification;

/** Predicados del listado de cajas cerradas. */
public class CajaSpecifications {

    private CajaSpecifications() {}

    /** Sólo cajas ya cerradas. El listado no filtra por fechas: se pagina sobre todas. */
    public static Specification<Caja> cerrada() {
        return (root, query, cb) -> cb.isNotNull(root.get("fechaCierre"));
    }

    /** Compara contra "nombre apellido", que es lo que manda el filtro. */
    public static Specification<Caja> deUsuarioNombre(String usuarioNombre) {
        if (usuarioNombre == null || usuarioNombre.isBlank()) return null;
        return (root, query, cb) -> {
            Join<Caja, Usuario> usuario = root.join("usuario");
            return cb.equal(cb.concat(cb.concat(usuario.get("nombre"), " "), usuario.get("apellido")), usuarioNombre);
        };
    }

    /** Deja fuera las cajas deshabilitadas por un admin (null cuenta como habilitada). */
    public static Specification<Caja> habilitada() {
        return (root, query, cb) -> cb.or(cb.isNull(root.get("habilitada")), cb.isTrue(root.get("habilitada")));
    }
}
