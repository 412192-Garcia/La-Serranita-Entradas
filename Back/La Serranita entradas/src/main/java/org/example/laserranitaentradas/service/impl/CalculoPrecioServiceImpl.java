package org.example.laserranitaentradas.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.laserranitaentradas.model.entity.DescuentoEfectivo;
import org.example.laserranitaentradas.model.entity.FormaPago;
import org.example.laserranitaentradas.model.entity.TipoEntrada;
import org.example.laserranitaentradas.repository.DescuentoEfectivoRepository;
import org.example.laserranitaentradas.service.CalculoPrecioService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CalculoPrecioServiceImpl implements CalculoPrecioService {

    private final DescuentoEfectivoRepository descuentoEfectivoRepository;

    @Override
    public BigDecimal calcularTotal(TipoEntrada tipoEntrada, int cantidad, FormaPago formaPago) {
        BigDecimal precioListaTotal = tipoEntrada.getPrecio().multiply(BigDecimal.valueOf(cantidad));

        if (formaPago == FormaPago.EFECTIVO_BOLETERIA) {
            Optional<DescuentoEfectivo> promo = descuentoEfectivoRepository
                    .findByTipoEntradaAndCantidadPases(tipoEntrada, cantidad);

            if (promo.isPresent()) {
                return promo.get().getPrecioPromocionalTotal();
            }
        }

        return precioListaTotal;
    }

    @Override
    public BigDecimal calcularAhorro(TipoEntrada tipoEntrada, int cantidad, FormaPago formaPago) {
        BigDecimal totalLista = tipoEntrada.getPrecio().multiply(BigDecimal.valueOf(cantidad));
        BigDecimal totalFinal = calcularTotal(tipoEntrada, cantidad, formaPago);

        return totalLista.subtract(totalFinal);
    }
}