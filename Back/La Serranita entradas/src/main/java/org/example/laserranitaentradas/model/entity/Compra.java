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
// Columnas por las que filtran reportes, cajas, boletería y las tareas de Sistema.
@Table(name = "compras", indexes = {
        @Index(name = "idx_compras_fecha_visita", columnList = "fecha_visita"),
        @Index(name = "idx_compras_fecha_creacion", columnList = "fecha_creacion"),
        @Index(name = "idx_compras_estado_fecha_creacion", columnList = "estado, fecha_creacion"),
        @Index(name = "idx_compras_fecha_validacion", columnList = "fecha_validacion"),
        @Index(name = "idx_compras_fecha_pago", columnList = "fecha_pago"),
        @Index(name = "idx_compras_caja", columnList = "id_caja"),
        @Index(name = "idx_compras_cliente", columnList = "id_cliente")
})
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

    /** Promo de venta en puerta usada, para reportar por promo. Null si no hubo. */
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
     * Ids de los pagos de MP que pagaron la compra, separados por coma. El reembolso usa estos ids porque
     * external_reference se repite si se reinicia la base. Texto para escribirlo en la misma sentencia que
     * aprueba.
     */
    @Column(name = "mp_payment_ids", length = 200)
    private String mpPaymentIds;

    /** Compras online: cómo se confirmó el pago (ver ConfirmacionPago) y cuándo. Null en las demás. */
    @Column(name = "pago_confirmado_por", length = 20)
    private String pagoConfirmadoPor;

    @Column(name = "fecha_pago")
    private LocalDateTime fechaPago;

    /** Pasa la compra a CANCELADO dejando registrado por qué, cuándo y quién. */
    public void cancelar(MotivoCancelacion motivo, String detalle, String usuario) {
        this.estado = EstadoCompra.CANCELADO;
        this.motivoCancelacion = motivo.name();
        this.detalleCancelacion = detalle;
        this.fechaCancelacion = LocalDateTime.now();
        this.canceladaPor = usuario;
    }

    public List<Long> pagosMercadoPago() {
        if (mpPaymentIds == null || mpPaymentIds.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(mpPaymentIds.split(",")).map(String::trim).map(Long::valueOf).toList();
    }

    /** Sólo en una compra CANCELADO: por qué (nombre de un MotivoCancelacion). Se limpia si se reactiva. */
    @Column(name = "motivo_cancelacion", length = 30)
    private String motivoCancelacion;

    /** Lo que escribió quien la anuló, o lo que contestó Mercado Pago en una cancelación automática. */
    @Column(name = "detalle_cancelacion", length = 300)
    private String detalleCancelacion;

    @Column(name = "fecha_cancelacion")
    private LocalDateTime fechaCancelacion;

    /** Usuario que la anuló, o SISTEMA si la canceló el barrido / el fallo al iniciar el pago. */
    @Column(name = "cancelada_por", length = 100)
    private String canceladaPor;

    /** No se guarda: true si create() devolvió un checkout reusado, que ya tiene link de pago. */
    @Transient
    private boolean reusada;

    /** Caja en la que se cobró. Null en las compras online. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_caja")
    private Caja caja;

    /** Cotización (ARS por USD) si se cobró en dólares. Null en pesos. */
    @Column(name = "cotizacion_dolar")
    private BigDecimal cotizacionDolar;

    /** Dólares que entregó el cliente, para reconstruir el vuelto. Null si pagó en pesos. */
    @Column(name = "dolares_recibidos")
    private BigDecimal dolaresRecibidos;

    /** Clave del POS para que un reintento no duplique la venta. Null fuera de la cola offline. */
    @Column(name = "idempotency_key", unique = true, length = 64)
    private String idempotencyKey;

    /**
     * Pago mixto: forma secundaria con su monto; la principal se lleva montoTotal − montoFormaPagoSecundaria.
     * Sólo formas presenciales, sin dólares ni precio de grupo.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pago_secundaria", length = 30)
    private FormaPago formaPagoSecundaria;

    @Column(name = "monto_forma_pago_secundaria")
    private BigDecimal montoFormaPagoSecundaria;

    /**
     * Parte de la compra cobrada con `forma`, con pago mixto. Con tarjeta + tarjeta da el total: no sumarla
     * una vez por forma.
     */
    public BigDecimal montoPorForma(FormaPago forma) {
        BigDecimal total = BigDecimal.ZERO;
        if (formaPago == forma) {
            total = total.add(formaPagoSecundaria != null ? montoTotal.subtract(montoFormaPagoSecundaria) : montoTotal);
        }
        if (formaPagoSecundaria == forma) {
            total = total.add(montoFormaPagoSecundaria);
        }
        return total;
    }

}
