package org.example.laserranitaentradas.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Operación del POS que el servidor rechazó, guardada para que un admin la vea desde cualquier máquina (la
 * cola offline vive sólo en el navegador). Quién la resolvió va en usuarioModificacion/fechaModificacion.
 */
@Entity
@Table(name = "operaciones_rechazadas")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
public class OperacionRechazada extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** "VENTA" | "RETIRO_APORTE" | "INGRESO_ENTRADAS" — mismos tipos que la cola offline del frontend. */
    @Column(nullable = false, length = 30)
    private String tipoOperacion;

    /** JSON de lo que se intentó mandar. */
    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    /** El motivo tal cual lo devolvió la validación. */
    @Column(nullable = false, columnDefinition = "text")
    private String motivo;

    /** La clave de la cola offline que lo generó, si vino de ahí. */
    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    /** Con default para que ddl-auto la pueda agregar como NOT NULL. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean resuelto;

    @Column(columnDefinition = "text")
    private String notaResolucion;
}
