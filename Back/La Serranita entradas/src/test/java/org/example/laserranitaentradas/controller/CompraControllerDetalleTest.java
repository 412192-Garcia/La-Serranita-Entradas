package org.example.laserranitaentradas.controller;

import org.example.laserranitaentradas.model.dto.CompraDetalleResponseDTO;
import org.example.laserranitaentradas.model.entity.CompraDetalle;
import org.example.laserranitaentradas.model.entity.Tipo;
import org.example.laserranitaentradas.model.entity.TipoEntrada;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El POS arma el cobro de una reserva con lo que devuelve el detalle: tiene que ver el precio
 * con el que se reservó, no el que tiene hoy el tipo.
 */
class CompraControllerDetalleTest {

    private TipoEntrada generalQueHoyVale150() {
        return TipoEntrada.builder().id(1L).nombre("General").tipo(Tipo.ENTRADA)
                .precio(new BigDecimal("150")).build();
    }

    @Test
    void lineaDeEntradaConPrecioCongelado_muestraElCongeladoYNoElActual() {
        CompraDetalle detalle = CompraDetalle.builder()
                .tipoEntrada(generalQueHoyVale150()).cantidad(2)
                .precioUnitario(new BigDecimal("100")).build();

        CompraDetalleResponseDTO dto = CompraController.detalleEntityToDto(detalle);

        assertThat(dto.getPrecioUnitario()).isEqualByComparingTo("100");
        // El tipo en sí sigue mostrando su precio actual (es el catálogo, no la reserva).
        assertThat(dto.getTipoEntrada().getPrecio()).isEqualByComparingTo("150");
    }

    @Test
    void lineaDeEntradaAnteriorAlPrecioCongelado_caeAlPrecioActualDelTipo() {
        CompraDetalle detalle = CompraDetalle.builder()
                .tipoEntrada(generalQueHoyVale150()).cantidad(2).build();

        CompraDetalleResponseDTO dto = CompraController.detalleEntityToDto(detalle);

        assertThat(dto.getPrecioUnitario()).isEqualByComparingTo("150");
    }
}
