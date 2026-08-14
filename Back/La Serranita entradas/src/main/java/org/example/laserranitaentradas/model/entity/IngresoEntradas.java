package org.example.laserranitaentradas.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

/** Entradas físicas (talonario) que se suman al stock de la caja mientras está abierta, ej. cuando el boletero se queda sin y le traen más. */
@Entity
@Table(name = "ingresos_entradas")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"caja"})
@ToString(callSuper = true, exclude = {"caja"})
@Builder
public class IngresoEntradas extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_caja", nullable = false)
    private Caja caja;

    @Column(nullable = false)
    private Integer cantidad;

    @Column(nullable = false)
    private LocalDateTime fecha;

    /**
     * Clave que genera el cliente antes de mandar la operación, para que un reintento no
     * duplique: si la petición original llegó y se procesó pero la respuesta se perdió (corte
     * de conexión justo ahí), el reintento trae la misma clave y el servicio devuelve lo ya
     * guardado. Null para operaciones que no vienen de la cola offline del POS.
     */
    @Column(name = "idempotency_key", unique = true, length = 64)
    private String idempotencyKey;
}
