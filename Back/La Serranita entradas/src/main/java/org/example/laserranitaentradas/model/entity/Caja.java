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
import java.util.ArrayList;
import java.util.List;

/**
 * Turno de caja de un boletero: arranca con un monto inicial declarado y, al cerrar,
 * compara el efectivo esperado (inicial + ventas en efectivo − retiros) contra lo que
 * el boletero contó de verdad. Mientras no tenga fechaCierre, está abierta.
 */
@Entity
@Table(name = "cajas")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"usuario"})
@ToString(callSuper = true, exclude = {"usuario"})
@Builder
public class Caja extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Column(name = "fecha_apertura", nullable = false)
    private LocalDateTime fechaApertura;

    @Column(name = "monto_inicial", nullable = false)
    private BigDecimal montoInicial;

    /** Null mientras la caja sigue abierta. */
    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre;

    /** Efectivo que el boletero contó al cerrar. Null hasta que cierra. */
    @Column(name = "monto_contado")
    private BigDecimal montoContado;

    /** Inicial + ventas en efectivo − retiros, calculado en el momento del cierre. */
    @Column(name = "monto_esperado")
    private BigDecimal montoEsperado;

    /** montoContado − montoEsperado: positivo es sobrante, negativo es faltante. */
    @Column(name = "diferencia")
    private BigDecimal diferencia;

    /**
     * Con cuántas entradas físicas (talonario) arranca el boletero el turno. Null en
     * cajas abiertas antes de agregar este campo: el cierre tolera esa ausencia y no
     * calcula la diferencia de entradas en ese caso.
     */
    @Column(name = "entradas_fisicas_inicial")
    private Integer entradasFisicasInicial;

    /** Con cuántas entradas físicas termina el turno. Null hasta que cierra. */
    @Column(name = "entradas_fisicas_final")
    private Integer entradasFisicasFinal;

    /** Cuántos billetes de cada denominación contó el boletero al cerrar. */
    @ElementCollection
    @CollectionTable(name = "caja_conteo_efectivo", joinColumns = @JoinColumn(name = "id_caja"))
    @Builder.Default
    private List<ConteoDenominacion> conteoEfectivo = new ArrayList<>();
}
