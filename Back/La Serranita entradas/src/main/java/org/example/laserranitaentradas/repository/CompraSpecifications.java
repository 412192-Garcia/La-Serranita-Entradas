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

    /** DNI, nombre/apellido del titular, código de reserva o email de contacto: cualquiera que matchee. */
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
                    cb.like(cb.lower(cb.coalesce(root.get("contactEmail"), "")), like)
            );
        };
    }

    public static Specification<Compra> fecha(LocalDate fecha) {
        if (fecha == null) return null;
        return (root, query, cb) -> cb.equal(root.get("fechaVisita"), fecha);
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
