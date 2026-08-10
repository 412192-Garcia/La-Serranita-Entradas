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
 * Un cierre de posnet/QR cargado al cerrar la caja (TARJETA o MERCADO_PAGO_QR). Puede
 * haber más de uno del mismo tipo: si el posnet se reinicia a mitad de turno, el
 * boletero carga cada mitad como una entrada separada y se suman.
 *
 * formaPago null significa "cierre combinado" (Tarjeta+QR cargados juntos, sin
 * distinguir cuál es cuál) — ver validarCierresPosnet en CajaServiceImpl.
 */
@Entity
@Table(name = "cierres_posnet")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"caja"})
@ToString(callSuper = true, exclude = {"caja"})
@Builder
public class CierrePosnet extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_caja", nullable = false)
    private Caja caja;

    /** TARJETA, MERCADO_PAGO_QR, o null (cierre combinado). Validado en el service. */
    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pago", length = 30)
    private FormaPago formaPago;

    @Column(nullable = false)
    private BigDecimal monto;

    private String nota;

    @Column(nullable = false)
    private LocalDateTime fecha;
}
