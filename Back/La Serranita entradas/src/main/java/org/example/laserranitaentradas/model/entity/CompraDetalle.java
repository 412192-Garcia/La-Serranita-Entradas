package org.example.laserranitaentradas.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;

/**
 * Una fila de una compra: o bien una entrada (tipoEntrada seteado, precio siempre derivado
 * en vivo de tipoEntrada.precio — sin cambios) o bien un artículo vario (venta en puerta:
 * articuloVario si viene de catálogo, o sólo descripcionLibre si es una línea suelta que el
 * cajero tipeó sin guardarla en ningún catálogo). Exactamente uno de tipoEntrada /
 * articuloVario / descripcionLibre está seteado por fila — se valida en el service, no acá.
 */
@Entity
@Table(name = "compras_detalle")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"compra", "tipoEntrada", "articuloVario"})
@ToString(callSuper = true, exclude = {"compra", "tipoEntrada", "articuloVario"})
@Builder
public class CompraDetalle extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_compra", nullable = false)
    private Compra compra;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tipo_entrada")
    private TipoEntrada tipoEntrada;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_articulo_vario")
    private ArticuloVario articuloVario;

    /** Sólo para una línea de artículo libre (sin articuloVario ni tipoEntrada). */
    @Column(name = "descripcion_libre")
    private String descripcionLibre;

    /**
     * Sólo para líneas de artículo (catálogo o libre): el precio unitario cargado al vender,
     * congelado en la compra. Las líneas de entrada NUNCA lo usan — siguen derivando el
     * precio en vivo de tipoEntrada.precio, sin cambios de comportamiento.
     */
    @Column(name = "precio_unitario")
    private BigDecimal precioUnitario;

    @Column(nullable = false)
    private Integer cantidad;

}

