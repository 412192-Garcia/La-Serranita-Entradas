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
 * Corrección manual de la repartición por forma de pago de una caja YA CERRADA. El admin,
 * revisando el cierre, ajusta a mano cuánto cuenta cada forma de pago sin tocar las compras:
 * queda como un registro aparte de "esto se ajustó a mano", con quién y cuándo (auditoría de
 * BaseEntity), y el cierre recalcula sus esperados/diferencias aplicando estos ajustes.
 *
 * Tres casos, según qué formas vengan seteadas:
 * - reubicar: origen + destino → la cajera cobró de una forma y tocó otra en el POS.
 * - quitar:   sólo origen      → se registró de más en esa forma (venta fantasma / duplicada).
 * - agregar:  sólo destino     → se cobró y no se registró (falta esa venta en esa forma).
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

    /** Ids de las compras que el admin marcó como mal clasificadas — trazabilidad, no se usan en el cálculo. */
    @ElementCollection
    @CollectionTable(name = "ajuste_caja_compras", joinColumns = @JoinColumn(name = "id_ajuste"))
    @Column(name = "id_compra")
    @Builder.Default
    private List<Long> comprasMovidas = new ArrayList<>();

    /**
     * Composición de UNA venta de este ajuste: id de tipo de entrada → cantidad de pases.
     * Con esto el cierre recalcula el uso del talonario y los conteos de entradas (esperadas,
     * pagas, por tipo) además de la plata por forma de pago. Vacío si el ajuste es un monto
     * suelto o una firma que el front no pudo descomponer (ej. incluye un artículo vario).
     */
    @ElementCollection
    @CollectionTable(name = "ajuste_caja_lineas", joinColumns = @JoinColumn(name = "id_ajuste"))
    @MapKeyColumn(name = "id_tipo_entrada")
    @Column(name = "cantidad")
    @Builder.Default
    private Map<Long, Integer> lineas = new HashMap<>();
}
