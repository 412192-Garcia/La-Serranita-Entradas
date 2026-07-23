package org.example.laserranitaentradas.service;


import org.example.laserranitaentradas.model.dto.PagoResponseDTO;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.EstadoCompra;
import org.example.laserranitaentradas.model.entity.FormaPago;

public interface PagoService {
    PagoResponseDTO procesarPago(Compra compra) throws Exception;

    FormaPago getFormaPago();

    EstadoCompra getEstadoInicial();
}
