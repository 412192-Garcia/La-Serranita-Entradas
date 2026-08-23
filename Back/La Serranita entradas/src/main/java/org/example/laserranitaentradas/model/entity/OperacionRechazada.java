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
 * Registro de una operación del POS (venta, retiro/aporte de efectivo, ingreso/retiro de
 * entradas) que el servidor rechazó — dato inválido, caja ya cerrada, etc. A diferencia de la
 * cola offline del frontend (que vive sólo en el localStorage del boletero, sin que ningún admin
 * pueda verla desde otra máquina), esto queda guardado del lado del servidor para que cualquier
 * admin la revise desde Cajas, sin depender de estar parado frente a esa computadora puntual.
 *
 * quién/cuándo se registró viene gratis de BaseEntity (usuarioCreacion/fechaCreacion, vía el
 * JWT autenticado); quién/cuándo se resolvió reutiliza usuarioModificacion/fechaModificacion,
 * porque resolver es la única modificación que este registro llega a tener.
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

    /** Snapshot en JSON de lo que se intentó mandar (cantidad, motivo, monto, ítems...), para
     * poder mostrarlo tal cual se lo pidió el boletero sin tener que adivinar la forma según el
     * tipo. */
    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    /** El motivo del rechazo tal cual lo devolvió la validación (ej. "No tenés esa cantidad de
     * entradas para retirar"). */
    @Column(nullable = false, columnDefinition = "text")
    private String motivo;

    /** Para correlacionar con la entrada de la cola offline del navegador que lo generó, si vino
     * de ahí. Null si no aplica. */
    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    /** Default 'false' en la columna: permite que ddl-auto=update agregue esta columna NOT NULL
     * sobre las filas ya existentes en la base de prueba en uso. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean resuelto;

    @Column(columnDefinition = "text")
    private String notaResolucion;
}
