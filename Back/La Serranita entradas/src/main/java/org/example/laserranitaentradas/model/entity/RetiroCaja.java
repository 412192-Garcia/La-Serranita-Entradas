package org.example.laserranitaentradas.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Plata que sale físicamente de la caja mientras está abierta (ej. para resguardarla). */
@Entity
@Table(name = "retiros_caja")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"caja"})
@ToString(callSuper = true, exclude = {"caja"})
@Builder
public class RetiroCaja extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_caja", nullable = false)
    private Caja caja;

    /** Siempre un monto positivo: el signo lo da tipo (RETIRO resta, APORTE suma), no el monto en sí. */
    @Column(nullable = false)
    private BigDecimal monto;

    @Column(nullable = false)
    private String motivo;

    /** Default 'RETIRO' en la columna: permite que ddl-auto=update agregue esta columna NOT NULL
     *  sobre las filas ya existentes en la base de prueba en uso (sin default, Postgres rechaza
     *  el ALTER TABLE porque esas filas quedarían sin valor). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20) default 'RETIRO'")
    private TipoMovimientoCaja tipo;

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
