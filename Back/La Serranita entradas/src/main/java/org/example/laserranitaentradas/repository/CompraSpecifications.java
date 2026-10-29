package org.example.laserranitaentradas.repository;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.example.laserranitaentradas.model.entity.Cliente;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.EstadoCompra;
import org.example.laserranitaentradas.model.entity.FormaPago;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.Collection;

/** Predicados de la búsqueda de boletería. Devuelven null si el filtro no aplica. */
public class CompraSpecifications {

    private CompraSpecifications() {}

    /** DNI, nombre, código, email, o los datos del receptor de un regalo (es quien se presenta). */
    public static Specification<Compra> textoLibre(String q) {
        if (q == null || q.isBlank()) return null;
        String like = "%" + q.trim().toLowerCase() + "%";
        return (root, query, cb) -> {
            Join<Compra, Cliente> cliente = root.join("cliente", JoinType.LEFT);
            return cb.or(
                    cb.like(cb.lower(cliente.get("dni")), like),
                    cb.like(cb.lower(cliente.get("nombre")), like),
                    cb.like(cb.lower(cliente.get("apellido")), like),
                    cb.like(cb.lower(root.get("codigoReserva")), like),
                    cb.like(cb.lower(cb.coalesce(root.get("contactEmail"), "")), like),
                    cb.like(cb.lower(cb.coalesce(root.get("receptorDni"), "")), like),
                    cb.like(cb.lower(cb.coalesce(root.get("receptorNombre"), "")), like),
                    cb.like(cb.lower(cb.coalesce(root.get("receptorEmail"), "")), like)
            );
        };
    }

    public static Specification<Compra> fecha(LocalDate fecha) {
        if (fecha == null) return null;
        return (root, query, cb) -> cb.equal(root.get("fechaVisita"), fecha);
    }

    /** Rango abierto o cerrado: usar sólo desde, sólo hasta, o ambos. */
    public static Specification<Compra> fechaDesde(LocalDate desde) {
        if (desde == null) return null;
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("fechaVisita"), desde);
    }

    public static Specification<Compra> fechaHasta(LocalDate hasta) {
        if (hasta == null) return null;
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("fechaVisita"), hasta);
    }

    /** Regalos (fechaVisita null): fechaDesde/fechaHasta nunca matchean NULL. */
    public static Specification<Compra> sinFecha(Boolean sinFecha) {
        if (sinFecha == null || !sinFecha) return null;
        return (root, query, cb) -> cb.isNull(root.get("fechaVisita"));
    }

    public static Specification<Compra> estadoIn(Collection<EstadoCompra> estados) {
        if (estados == null || estados.isEmpty()) return null;
        return (root, query, cb) -> root.get("estado").in(estados);
    }

    public static Specification<Compra> formaPago(FormaPago formaPago) {
        if (formaPago == null) return null;
        return (root, query, cb) -> cb.equal(root.get("formaPago"), formaPago);
    }
}
