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
 * Un cobro con tarjeta o QR mandado al Point de Mercado Pago desde el POS (una orden de MP). Se
 * cobra ANTES de registrar la venta: el POS espera a que quede APROBADO y recién ahí manda la venta
 * por la cola, con el id de este cobro, y el backend los vincula ({@link #compra}). Una venta con
 * pago mixto tarjeta + QR tiene dos. Un cobro aprobado sin compra es plata cobrada sin venta: o la
 * venta está por llegar (cola offline), o hay que devolverlo.
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

    /** La genera el POS por cada intento: si el pedido se repite (respuesta perdida), devuelve el mismo cobro. */
    @Column(name = "clave_idempotencia", nullable = false, unique = true, length = 64)
    private String claveIdempotencia;

    /** Id de la orden en Mercado Pago (ORD...). Null si no se llegó a crear. */
    @Column(name = "orden_id", unique = true, length = 64)
    private String ordenId;

    @Column(name = "terminal_id", nullable = false, length = 100)
    private String terminalId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    /** Qué parte de la venta paga: TARJETA o MERCADO_PAGO_QR (el Point muestra el QR en pantalla). */
    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pago", nullable = false, length = 30)
    private FormaPago formaPago;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoCobroPoint estado;

    /** status_detail de MP (rejected_by_issuer, insufficient_amount...) o el error, para explicar un rechazo. */
    @Column(length = 300)
    private String detalle;

    @Column(name = "pago_id", length = 64)
    private String pagoId;

    /** DEBITO o CREDITO: lo que eligió el boletero para una tarjeta, así el Point pide directo esa
     * (sin él, el Point le pregunta al cliente con qué paga, QR incluido). Null en QR. */
    @Column(name = "tipo_tarjeta", length = 10)
    private String tipoTarjeta;

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
     * El cobro se actualiza desde tres lados a la vez (el webhook, la consulta del POS y la venta
     * que lo vincula), cada uno con su copia: sin versión, el último en guardar pisaba lo de los
     * otros (por ejemplo, un webhook lento le volvía a poner id_compra = NULL a un cobro recién
     * vinculado). Con default en la base para que ddl-auto pueda agregarla a una tabla con filas.
     */
    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private Long version;
}
