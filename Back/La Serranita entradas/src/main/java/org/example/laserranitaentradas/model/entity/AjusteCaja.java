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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Corrección manual de una caja cerrada entre formas de pago, sin tocar las compras. El cierre recalcula con
 * estos ajustes.
 * - reubicar: origen + destino (se cobró de una forma y se anotó otra).
 * - quitar: sólo origen (venta de más).
 * - agregar: sólo destino (venta que faltó).
 */
@Entity
@Table(name = "ajustes_caja")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"caja"})
@ToString(callSuper = true, exclude = {"caja"})
@Builder
public class AjusteCaja extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_caja", nullable = false)
    private Caja caja;

    /** Forma de pago de la que se saca el monto. Null cuando el ajuste sólo agrega. */
    @Enumerated(EnumType.STRING)
    @Column(name = "forma_origen", length = 30)
    private FormaPago formaOrigen;

    /** Forma de pago a la que va el monto. Null cuando el ajuste sólo quita. */
    @Enumerated(EnumType.STRING)
    @Column(name = "forma_destino", length = 30)
    private FormaPago formaDestino;

    /** Suma de los montos de las ventas traspasadas, tal cual se cobraron. Siempre positivo. */
    @Column(nullable = false)
    private BigDecimal monto;

    /** Cuántas ventas abarca el traspaso (informativo, para mostrar en el resumen). */
    @Column(name = "cantidad_ventas", nullable = false)
    private int cantidadVentas;

    /** La firma del grupo traspasado ("2x Pase General") o "varios". Informativo. */
    @Column(length = 255)
    private String detalle;

    /** Nota opcional que el admin puede dejar explicando el ajuste. */
    @Column(length = 500)
    private String nota;

    @Column(nullable = false)
    private LocalDateTime fecha;

    /** Compras marcadas como mal clasificadas. Sólo trazabilidad. */
    @ElementCollection
    @CollectionTable(name = "ajuste_caja_compras", joinColumns = @JoinColumn(name = "id_ajuste"))
    @Column(name = "id_compra")
    @Builder.Default
    private List<Long> comprasMovidas = new ArrayList<>();

    /**
     * Tipo de entrada → cantidad de UNA venta del ajuste, para recalcular el talonario y los conteos. Vacío
     * si es un monto suelto.
     */
    @ElementCollection
    @CollectionTable(name = "ajuste_caja_lineas", joinColumns = @JoinColumn(name = "id_ajuste"))
    @MapKeyColumn(name = "id_tipo_entrada")
    @Column(name = "cantidad")
    @Builder.Default
    private Map<Long, Integer> lineas = new HashMap<>();
}
