package org.example.laserranitaentradas.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Factura electrónica B (Consumidor Final) de una compra, autorizada por ARCA vía AfipSDK.
 *
 * Vive aparte de la Compra a propósito: la venta se confirma siempre, aunque ARCA esté caída,
 * y la factura se emite después en segundo plano (ver FacturaServiceImpl). Por eso nace
 * PENDIENTE, sin número ni CAE.
 *
 * El número de comprobante lo asigna ARCA en orden estricto por punto de venta: no se elige,
 * se pide el último autorizado y se usa el siguiente. numeroIntentado guarda el número con el
 * que se mandó el último pedido ANTES de mandarlo, para que si la respuesta se pierde (timeout,
 * corte) el próximo intento pueda preguntarle a ARCA si ese número quedó autorizado en vez de
 * emitir la misma venta dos veces.
 */
@Entity
@Table(name = "facturas", uniqueConstraints = {
        @UniqueConstraint(name = "uk_factura_numero", columnNames = {"punto_venta", "tipo_comprobante", "numero"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"compra", "comprobanteAsociado"})
@ToString(callSuper = true, exclude = {"compra", "comprobanteAsociado"})
@Builder
public class Factura extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Una compra puede tener varios comprobantes: la factura, su nota de crédito si se canceló,
     * y una factura nueva si se editó el monto. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_compra", nullable = false)
    private Compra compra;

    /** Sólo en una nota de crédito: la factura que anula (va en CbtesAsoc del pedido a ARCA). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_comprobante_asociado")
    private Factura comprobanteAsociado;

    /**
     * La venta se canceló o cambió de monto mientras esta factura estaba en camino (pidió número a
     * ARCA y no se sabe si quedó autorizada). Al resolverse: si quedó autorizada se le emite la
     * nota de crédito; si no, pasa a ANULADA sin pedir otro número.
     */
    @Column(name = "anulacion_pedida", nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean anulacionPedida = false;

    /** Copia de los ítems al momento de facturar ("cantidad<TAB>descripción" por línea): si después
     * se edita la venta, el comprobante sigue mostrando lo que se facturó. */
    @Column(length = 2000)
    private String detalle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EstadoFactura estado = EstadoFactura.PENDIENTE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DestinoFactura destino;

    /** Sólo con destino IMPRIMIR: en qué ticketera (nombre que anuncia el agente). Null = la
     * única conectada, si hay una sola. */
    @Column(length = 60)
    private String impresora;

    /** Sólo con destino MAIL. */
    @Column(length = 150)
    private String email;

    @Column(name = "punto_venta", nullable = false)
    private Integer puntoVenta;

    /** Código ARCA del comprobante: 6 = Factura B. */
    @Column(name = "tipo_comprobante", nullable = false)
    private Integer tipoComprobante;

    /** Código ARCA del concepto: 1 = productos, 2 = servicios, 3 = ambos. */
    @Column(nullable = false)
    private Integer concepto;

    /** Número autorizado por ARCA. Null hasta que la factura queda EMITIDA. */
    private Long numero;

    /** Número con el que se mandó el último pedido a ARCA (ver comentario de la clase). */
    @Column(name = "numero_intentado")
    private Long numeroIntentado;

    @Column(length = 20)
    private String cae;

    @Column(name = "cae_vencimiento")
    private LocalDate caeVencimiento;

    /** Fecha del comprobante (CbteFch): el día en que se emitió, no el de la venta. */
    @Column(name = "fecha_emision")
    private LocalDate fechaEmision;

    /** Día del servicio (FchServDesde/Hasta): la fecha de visita de la compra. */
    @Column(name = "fecha_servicio")
    private LocalDate fechaServicio;

    @Column(name = "importe_total", nullable = false)
    private BigDecimal importeTotal;

    @Column(name = "importe_neto", nullable = false)
    private BigDecimal importeNeto;

    /** IVA contenido en el total: se imprime en el ticket por la Ley 27.743. */
    @Column(name = "importe_iva", nullable = false)
    private BigDecimal importeIva;

    @Column(nullable = false, columnDefinition = "integer default 0")
    @Builder.Default
    private Integer intentos = 0;

    @Column(name = "proximo_intento")
    private LocalDateTime proximoIntento;

    @Column(name = "ultimo_error", length = 1000)
    private String ultimoError;

    /** Cuándo salió el mail con el PDF (destino MAIL). Null = todavía no, o falló. */
    @Column(name = "mail_enviado_en")
    private LocalDateTime mailEnviadoEn;
}
