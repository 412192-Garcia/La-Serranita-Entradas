package org.example.laserranitaentradas.service;

import org.example.laserranitaentradas.model.dto.CajaResponseDTO;
import org.example.laserranitaentradas.model.entity.Caja;

import java.math.BigDecimal;

public interface CajaService {

    /** La caja abierta del usuario, o null si no tiene ninguna en curso. */
    CajaResponseDTO getActual(Long usuarioId);

    /** Devuelve la caja abierta de ese usuario o lanza si no tiene ninguna: la usan las ventas para bloquearse sin caja. */
    Caja getAbiertaOrThrow(Long usuarioId);

    CajaResponseDTO abrir(Long usuarioId, BigDecimal montoInicial);

    CajaResponseDTO registrarRetiro(Long usuarioId, BigDecimal monto, String motivo);

    CajaResponseDTO cerrar(Long usuarioId, BigDecimal montoContado);
}
