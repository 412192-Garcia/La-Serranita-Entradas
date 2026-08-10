package org.example.laserranitaentradas.service;

import org.example.laserranitaentradas.model.dto.CajaAbiertaDTO;
import org.example.laserranitaentradas.model.dto.CierrePosnetRequestDTO;
import org.example.laserranitaentradas.model.dto.CajaResponseDTO;
import org.example.laserranitaentradas.model.dto.ConteoDenominacionDTO;
import org.example.laserranitaentradas.model.entity.Caja;
import org.example.laserranitaentradas.model.entity.TipoMovimientoCaja;

import java.math.BigDecimal;
import java.util.List;

public interface CajaService {

    /** La caja abierta del usuario, o null si no tiene ninguna en curso. */
    CajaResponseDTO getActual(Long usuarioId);

    /** Devuelve la caja abierta de ese usuario o lanza si no tiene ninguna: la usan las ventas para bloquearse sin caja. */
    Caja getAbiertaOrThrow(Long usuarioId);

    CajaResponseDTO abrir(Long usuarioId, BigDecimal montoInicial, Integer entradasFisicasInicial);

    CajaResponseDTO registrarRetiro(Long usuarioId, BigDecimal monto, String motivo, TipoMovimientoCaja tipo);

    /** Igual que registrarRetiro, pero para que un ADMIN lo cargue en la caja de OTRO usuario (ADMIN-only, gateado en SecurityConfig). */
    CajaResponseDTO registrarRetiroComoAdmin(Long cajaId, BigDecimal monto, String motivo, TipoMovimientoCaja tipo);

    /** Suma entradas físicas al talonario de la caja abierta, ej. cuando el boletero se quedó sin y le traen más. */
    CajaResponseDTO registrarIngresoEntradas(Long usuarioId, Integer cantidad);

    CajaResponseDTO cerrar(Long usuarioId, List<ConteoDenominacionDTO> conteoEfectivo,
                            List<CierrePosnetRequestDTO> cierresPosnet, Integer entradasFisicasCortadas,
                            BigDecimal cambioContado, BigDecimal dolaresContado);

    /** Igual que cerrar, pero para que un ADMIN cierre la caja de OTRO usuario por id (ADMIN-only, gateado en SecurityConfig). */
    CajaResponseDTO cerrarComoAdmin(Long cajaId, List<ConteoDenominacionDTO> conteoEfectivo,
                                     List<CierrePosnetRequestDTO> cierresPosnet, Integer entradasFisicasCortadas,
                                     BigDecimal cambioContado, BigDecimal dolaresContado);

    /**
     * Corrige los datos de un cierre ya hecho (ej. un billete mal contado). Sólo si ya está
     * cerrada — no reemplaza a `cerrar`. ADMIN-only (gateado en SecurityConfig): no valida
     * dueño acá, cualquier caja cerrada se puede corregir.
     */
    CajaResponseDTO corregirCierre(Long cajaId, List<ConteoDenominacionDTO> conteoEfectivo,
                                    List<CierrePosnetRequestDTO> cierresPosnet, Integer entradasFisicasCortadas,
                                    BigDecimal cambioContado, BigDecimal dolaresContado);

    /** Detalle completo de cualquier caja (para que el admin la revise sin importar quién la abrió). */
    CajaResponseDTO getDetalle(Long cajaId);

    /** Todas las cajas abiertas ahora mismo, sin importar de qué boletero — para el dashboard del admin. */
    List<CajaAbiertaDTO> getCajasAbiertas();
}
