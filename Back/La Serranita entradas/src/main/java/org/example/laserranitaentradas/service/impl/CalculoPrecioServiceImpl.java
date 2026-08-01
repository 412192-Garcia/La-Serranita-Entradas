package org.example.laserranitaentradas.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.laserranitaentradas.model.entity.DescuentoEfectivo;
import org.example.laserranitaentradas.model.entity.FormaPago;
import org.example.laserranitaentradas.model.entity.TipoEntrada;
import org.example.laserranitaentradas.repository.DescuentoEfectivoRepository;
import org.example.laserranitaentradas.service.CalculoPrecioService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

            // Grupos más grandes que el escalón máximo configurado pagan el precio por
            // persona de ese último escalón (no vuelven a precio de lista completo).
            Optional<DescuentoEfectivo> escalonMasAlto = descuentoEfectivoRepository
                    .findFirstByTipoEntradaOrderByCantidadPasesDesc(tipoEntrada);
            if (escalonMasAlto.isPresent() && cantidad > escalonMasAlto.get().getCantidadPases()) {
                DescuentoEfectivo tope = escalonMasAlto.get();
                BigDecimal precioPorPersona = tope.getPrecioPromocionalTotal()
                        .divide(BigDecimal.valueOf(tope.getCantidadPases()), 4, RoundingMode.HALF_UP);
                return precioPorPersona.multiply(BigDecimal.valueOf(cantidad)).setScale(2, RoundingMode.HALF_UP);
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