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
 * Una línea de una compra: entrada (tipoEntrada) o artículo vario (articuloVario o descripcionLibre).
 * Exactamente uno, validado en el service.
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
     * Artículos: el precio cargado al vender. Entradas: el precio de lista al armar la compra, para cobrar
     * una reserva al precio reservado. Null en compras viejas. Sólo pesa para repartir; lo cobrado es
     * montoTotal.
     */
    @Column(name = "precio_unitario")
    private BigDecimal precioUnitario;

    @Column(nullable = false)
    private Integer cantidad;

}

