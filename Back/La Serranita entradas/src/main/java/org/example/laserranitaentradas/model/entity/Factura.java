package org.example.laserranitaentradas.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Factura electrónica B de una compra, autorizada por ARCA. Una manual también puede ser A, con receptor por
 * CUIT.
 *
 * Va aparte de la Compra: la venta se confirma aunque ARCA esté caída y la factura se emite después, por eso
 * nace PENDIENTE.
 *
 * numeroIntentado guarda el número del último pedido antes de mandarlo, para consultar a ARCA si se pierde la
 * respuesta en vez de facturar dos veces.
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

    /**
     * Una compra puede tener factura, nota de crédito y factura nueva. Null en las manuales y sus notas de
     * crédito.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_compra")
    private Compra compra;

    /** Sólo en una nota de crédito: la factura que anula (va en CbtesAsoc del pedido a ARCA). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_comprobante_asociado")
    private Factura comprobanteAsociado;

    /**
     * La venta cambió mientras la factura estaba en camino: si quedó autorizada se le emite nota de crédito;
     * si no, pasa a ANULADA.
     */
    @Column(name = "anulacion_pedida", nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean anulacionPedida = false;

    /**
     * Ítems al facturar ("cantidad<TAB>descripción"), para que una edición posterior no cambie lo facturado.
     */
    @Column(length = 2000)
    private String detalle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EstadoFactura estado = EstadoFactura.PENDIENTE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DestinoFactura destino;

    /** Sólo con IMPRIMIR: la ticketera. Null = la única conectada. */
    @Column(length = 60)
    private String impresora;

    /** Sólo con destino MAIL. */
    @Column(length = 150)
    private String email;

    @Column(name = "punto_venta", nullable = false)
    private Integer puntoVenta;

    /** Código ARCA: 1 Factura A, 3 NC A, 6 Factura B, 8 NC B. */
    @Column(name = "tipo_comprobante", nullable = false)
    private Integer tipoComprobante;

    /** Sólo en Factura A y su NC: CUIT de 11 dígitos sin guiones. Null = Consumidor Final. */
    @Column(name = "receptor_cuit", length = 11)
    private String receptorCuit;

    @Column(name = "receptor_razon_social", length = 150)
    private String receptorRazonSocial;

    /** Domicilio comercial del receptor: la Factura A lo tiene que llevar impreso (RG 1415). */
    @Column(name = "receptor_domicilio", length = 200)
    private String receptorDomicilio;

    /** Código ARCA de la condición frente al IVA del receptor (1 = Resp. Inscripto, 6 = Monotributo...). */
    @Column(name = "receptor_condicion_iva")
    private Integer receptorCondicionIva;

    /** Clave del lote manual: si se repite el pedido, se devuelven las mismas facturas. */
    @Column(name = "lote_clave", length = 64)
    private String loteClave;

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

    /** Cuándo la autorizó ARCA, en hora argentina. Null en facturas viejas. */
    @Column(name = "emitida_en")
    private LocalDateTime emitidaEn;

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
