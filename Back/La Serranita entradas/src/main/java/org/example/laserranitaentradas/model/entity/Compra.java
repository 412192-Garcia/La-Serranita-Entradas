package org.example.laserranitaentradas.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "compras")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"usuarioValidador", "cupon", "promocion", "detalles", "cliente", "caja"})
@ToString(callSuper = true, exclude = {"usuarioValidador", "cupon", "promocion", "detalles", "cliente", "caja"})
@Builder
public class Compra extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cliente")
    private Cliente cliente;

    @Column(nullable = true)
    private String contactEmail;

    @Column(nullable = true)
    private String contactPhone;

    /** Null cuando la compra es un regalo: quien lo recibe puede usarlo el día que prefiera. */
    @Column(nullable = true)
    private LocalDate fechaVisita;

    /** Código visible para el cliente/boletería: yyMMdd-N (N = orden de la reserva ese día de visita). */
    @Column(name = "codigo_reserva", nullable = false, unique = true, length = 20)
    private String codigoReserva;

    @Column(nullable = false)
    private BigDecimal montoTotal;

    @Column(nullable = false)
    @Builder.Default
    private BigDecimal descuentoAplicado = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private EstadoCompra estado = EstadoCompra.PENDIENTE_PAGO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_validador")
    private Usuario usuarioValidador;

    /** Momento exacto del check-in en boletería. Null mientras la compra no se haya usado. */
    @Column(name = "fecha_validacion")
    private LocalDateTime fechaValidacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cupon")
    private Cupon cupon;

    /**
     * Promo de venta en puerta aplicada (si hubo una): a diferencia del cupón online, hasta
     * ahora sólo quedaba el monto de descuento en descuentoAplicado, sin registro de CUÁL
     * promo se usó. Null si la venta no usó promo (cupón online, descuento manual, o sin
     * descuento). No se toca nada del cálculo existente: es sólo para poder reportar por promo.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_promocion")
    private Promocion promocion;

    // Datos de quien recibe el regalo (sólo se cargan cuando fechaVisita es null, o sea, es un regalo).
    @Column(name = "receptor_nombre")
    private String receptorNombre;

    @Column(name = "receptor_email")
    private String receptorEmail;

    @Column(name = "receptor_dni", length = 15)
    private String receptorDni;

    /** Opcional. */
    @Column(name = "receptor_telefono")
    private String receptorTelefono;

    @OneToMany(mappedBy = "compra", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CompraDetalle> detalles;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pago", nullable = false, length = 30)
    private FormaPago formaPago;

    /**
     * Turno de caja en el que se cobró (venta de puerta o cobro de una reserva en
     * efectivo). Null en las compras online: esas nunca pasan efectivo por una caja.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_caja")
    private Caja caja;

    /**
     * Pago en efectivo hecho en dólares (sigue siendo EFECTIVO_BOLETERIA: es la misma forma
     * de pago, sólo cambia la moneda física con la que se cobra). Null en pesos.
     * Cotización usada para este cobro (ARS por USD).
     */
    @Column(name = "cotizacion_dolar")
    private BigDecimal cotizacionDolar;

    /** Dólares que entregó el cliente. Junto con cotizacionDolar y montoTotal permite
     * reconstruir el vuelto en pesos que hubo que darle. Null si no pagó en dólares. */
    @Column(name = "dolares_recibidos")
    private BigDecimal dolaresRecibidos;

    /**
     * Clave que genera el cliente antes de mandar la venta, para que un reintento no duplique:
     * si la petición original llegó y se procesó pero la respuesta se perdió (corte de conexión
     * justo ahí), el reintento trae la misma clave y el servicio devuelve la compra ya guardada.
     * Null para compras que no vienen de la cola offline del POS (ej. la compra online).
     */
    @Column(name = "idempotency_key", unique = true, length = 64)
    private String idempotencyKey;

}
