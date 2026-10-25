package org.example.laserranitaentradas.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalTime;

/** Configuración global (fila única, id=1): horario por defecto y límite de compra online del día. */
@Entity
@Table(name = "configuracion_parque")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
public class ConfiguracionParque extends BaseEntity {

    @Id
    private Long id;

    @Column(name = "hora_apertura", nullable = false)
    private LocalTime horaApertura;

    @Column(name = "hora_cierre", nullable = false)
    private LocalTime horaCierre;

    /**
     * Minutos antes del cierre en que se corta la compra online para HOY (también con horario especial). 0 =
     * hasta el cierre. Con default para ddl-auto.
     */
    @Column(name = "minutos_limite_compra", nullable = false, columnDefinition = "integer default 60")
    @Builder.Default
    private Integer minutosLimiteCompra = 60;

    /** Factura que arranca marcada en el POS según la forma de pago. Sólo IMPRIMIR o MAIL. */
    @Enumerated(EnumType.STRING)
    @Column(name = "factura_efectivo", nullable = false, columnDefinition = "varchar(20) default 'MAIL'")
    @Builder.Default
    private DestinoFactura facturaEfectivo = DestinoFactura.MAIL;

    @Enumerated(EnumType.STRING)
    @Column(name = "factura_tarjeta", nullable = false, columnDefinition = "varchar(20) default 'IMPRIMIR'")
    @Builder.Default
    private DestinoFactura facturaTarjeta = DestinoFactura.IMPRIMIR;

    @Enumerated(EnumType.STRING)
    @Column(name = "factura_qr", nullable = false, columnDefinition = "varchar(20) default 'IMPRIMIR'")
    @Builder.Default
    private DestinoFactura facturaQr = DestinoFactura.IMPRIMIR;

}
