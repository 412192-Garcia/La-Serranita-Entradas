package org.example.laserranitaentradas.service.impl;

import org.example.laserranitaentradas.model.dto.PagoResponseDTO;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.EstadoCompra;
import org.example.laserranitaentradas.model.entity.FormaPago;
import org.example.laserranitaentradas.service.PagoService;
import org.springframework.stereotype.Service;

@Service
public class EfectivoBoleteriaServiceImpl implements PagoService {

    @Override
    public PagoResponseDTO procesarPago(Compra compra) {
        return PagoResponseDTO.builder()
                .exitoso(true)
                .compraId(compra.getId())
                .initPoint(null)
                .preferenceId(null)
                .mensaje("Reserva registrada con éxito. Presentar DNI en boletería para abonar en efectivo.")
                .build();
    }

    @Override
    public FormaPago getFormaPago() {
        return FormaPago.EFECTIVO_BOLETERIA;
    }

    @Override
    public EstadoCompra getEstadoInicial() {
        return EstadoCompra.RESERVADO_EFECTIVO;
    }
}
