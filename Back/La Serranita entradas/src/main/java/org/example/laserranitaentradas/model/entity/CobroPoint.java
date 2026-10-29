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

/**
 * Cobro con tarjeta o QR en el Point (una orden de MP). Se cobra ANTES de registrar la venta y el backend los
 * vincula ({@link #compra}). Un pago mixto tarjeta + QR tiene dos. Aprobado sin compra = la venta está por
 * llegar o hay que devolverlo.
 */
@Entity
@Table(name = "cobros_point")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"caja", "compra"})
@ToString(callSuper = true, exclude = {"caja", "compra"})
@Builder
public class CobroPoint extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Una por intento: si el pedido se repite, devuelve el mismo cobro. */
    @Column(name = "clave_idempotencia", nullable = false, unique = true, length = 64)
    private String claveIdempotencia;

    /** Id de la orden en Mercado Pago (ORD...). Null si no se llegó a crear. */
    @Column(name = "orden_id", unique = true, length = 64)
    private String ordenId;

    @Column(name = "terminal_id", nullable = false, length = 100)
    private String terminalId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    /** Qué parte de la venta paga: TARJETA o MERCADO_PAGO_QR. Con {@link #posnet}, provisoria hasta saber con qué pagó. */
    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pago", nullable = false, length = 30)
    private FormaPago formaPago;

    /** El cliente elige tarjeta o QR en el Point. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean posnet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoCobroPoint estado;

    /** status_detail de MP o el error. */
    @Column(length = 300)
    private String detalle;

    @Column(name = "pago_id", length = 64)
    private String pagoId;

    /** visa, debmaster, account_money... */
    @Column(name = "medio_pago", length = 50)
    private String medioPago;

    /** credit_card, debit_card, qr... */
    @Column(name = "tipo_medio", length = 30)
    private String tipoMedio;

    private Integer cuotas;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_caja", nullable = false)
    private Caja caja;

    /** La venta que se pagó con este cobro. Null mientras no llegó (o si nunca llega). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_compra")
    private Compra compra;

    /** Última vez que se le preguntó a MP por la orden, para no consultarla en cada pedido del POS. */
    @Column(name = "ultima_consulta_mp")
    private LocalDateTime ultimaConsultaMp;

    /**
     * Lo actualizan el webhook, el POS y la venta a la vez: sin versión el último pisaba a los otros. Con
     * default para ddl-auto.
     */
    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private Long version;
}
