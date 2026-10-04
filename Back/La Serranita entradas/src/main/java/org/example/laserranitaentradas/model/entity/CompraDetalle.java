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
     * Líneas de artículo (catálogo o libre): el precio unitario cargado al vender, congelado
     * en la compra.
     * Líneas de entrada: el precio de LISTA que tenía el tipo cuando se armó la compra. Sirve
     * para que una reserva "a cobrar en boletería" se cobre al precio con el que se reservó
     * aunque el tipo haya subido después (ver CompraServiceImpl#cobrarReservaComoVentaPos).
     * Null en las compras anteriores a este campo: ahí se usa el precio actual del tipo, como
     * siempre. Reportes y cierre de caja sólo lo usan como peso de reparto, nunca para
     * recalcular lo cobrado (eso es montoTotal).
     */
    @Column(name = "precio_unitario")
    private BigDecimal precioUnitario;

    @Column(nullable = false)
    private Integer cantidad;

}

