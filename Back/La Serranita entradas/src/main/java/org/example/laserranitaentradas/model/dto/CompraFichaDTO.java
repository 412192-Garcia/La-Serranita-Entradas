package org.example.laserranitaentradas.model.dto;

import lombok.Data;
import org.example.laserranitaentradas.model.entity.FormaPago;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Todo lo que se sabe de una compra, para el "Ver detalle" de Control de Accesos y del POS. */
@Data
public class CompraFichaDTO {
    private CompraResponseDTO compra;

    private LocalDateTime fechaCompra;

    private String clienteLocalidad;
    private Integer clienteEdad;

    private String promocionNombre;

    // Pago online.
    private LocalDateTime fechaPago;
    private String pagoConfirmadoPor;
    private List<String> pagosMercadoPago;
    private String dispositivo;
    private Boolean enIframe;

    // Cobro con el Point: visa, debmaster... / credit_card, debit_card, qr...
    private String pointMedioPago;
    private String pointTipoMedio;
    private Integer pointCuotas;

    /** "Factura B 0010-00000123"; null si no tiene. */
    private String factura;
    private String facturaEstado;

    // Venta en caja.
    private Long cajaId;
    private String cajaUsuario;
    private LocalDateTime cajaFechaApertura;
    private FormaPago formaPagoSecundaria;
    private BigDecimal montoFormaPagoSecundaria;
    private BigDecimal cotizacionDolar;
    private BigDecimal dolaresRecibidos;

    // Cancelación.
    private String motivoCancelacion;
    private String detalleCancelacion;
    private LocalDateTime fechaCancelacion;
    private String canceladaPor;
}
