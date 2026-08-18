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

/**
 * Predicados reutilizables para armar dinámicamente la búsqueda paginada de boletería
 * (GET /api/compras/buscar). Cada uno devuelve null cuando el filtro no aplica, así
 * Specification.allOf(...) los puede combinar sin condicionales sueltos en el service.
 */
public class CompraSpecifications {

    private CompraSpecifications() {}

    /** DNI, nombre/apellido del titular, código de reserva, email de contacto, o los datos del
     * receptor de un regalo: cualquiera que matchee. El receptor hace falta porque en un regalo
     * quien se presenta en la puerta es esa persona, no quien compró — sin esto, buscar por el
     * DNI/nombre de quien está literalmente parado frente al boletero no encontraba nada. */
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

    /** Regalos: fechaVisita es null porque quien lo recibe puede usarlo el día que prefiera.
     * Hace falta como predicado aparte porque fechaDesde/fechaHasta (>=/<=) nunca matchean
     * NULL en SQL, así que no hay forma de traerlos con esos dos. */
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
