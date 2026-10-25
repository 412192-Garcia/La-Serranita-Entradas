package org.example.laserranitaentradas.model.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Una caja abierta ahora mismo, para el vistazo del admin de quién está trabajando. */
@Data
@Builder
public class CajaAbiertaDTO {
    private Long id;
    private String usuarioNombre;
    private LocalDateTime fechaApertura;
    private BigDecimal montoInicial;
    /** Vendido hasta el momento (efectivo + tarjeta + QR), en vivo. */
    private BigDecimal totalVendido;
    /** Unidades vendidas de tipos de entrada con precio > 0 (excluye gratis, extras y artículos). */
    private Integer totalEntradasPagas;
    /** Todos los pases que entraron por esta caja, también los gratis. */
    private Integer personasIngresadas;
    /** true = se abrió con "vender sin control de caja" (sólo ADMIN vendiendo él mismo). */
    private Boolean controlOmitido;
}
