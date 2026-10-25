package org.example.laserranitaentradas.service;

import org.example.laserranitaentradas.model.dto.AjusteCajaRequestDTO;
import org.example.laserranitaentradas.model.dto.CajaAbiertaDTO;
import org.example.laserranitaentradas.model.dto.CierrePosnetRequestDTO;
import org.example.laserranitaentradas.model.dto.CajaResponseDTO;
import org.example.laserranitaentradas.model.dto.ConteoDenominacionDTO;
import org.example.laserranitaentradas.model.dto.CajaDetalleAbiertaDTO;
import org.example.laserranitaentradas.model.dto.CajasCerradasResponseDTO;
import org.example.laserranitaentradas.model.entity.Caja;
import org.example.laserranitaentradas.model.entity.TipoMovimientoCaja;
import org.example.laserranitaentradas.model.entity.TipoMovimientoEntradas;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface CajaService {

    /** La caja abierta del usuario, o null si no tiene ninguna en curso. */
    CajaResponseDTO getActual(Long usuarioId);

    /** Caja abierta del usuario; lanza si no tiene. */
    Caja getAbiertaOrThrow(Long usuarioId);

    /** Como getAbiertaOrThrow pero sin lanzar: un admin puede cobrar en Control de Accesos sin caja. */
    java.util.Optional<Caja> getAbierta(Long usuarioId);

    CajaResponseDTO abrir(Long usuarioId, BigDecimal montoInicial, Integer entradasFisicasInicial);

    /** Abre una caja sin conteo inicial (controlOmitido). Se cierra sola a fin de día. */
    CajaResponseDTO abrirSinControl(Long usuarioId);

    /**
     * Cierra las cajas sin control que quedaron abiertas, con diferencia 0. Lo llama el job de medianoche.
     */
    void cerrarCajasSinControlAtrasadas();

    /** Cierra ya una caja sin control. Lanza si no existe, ya está cerrada o no es sin control. */
    CajaResponseDTO cerrarSinControlPorId(Long cajaId);

    /**
     * idempotencyKey y fechaOriginal vienen del POS offline: evitan duplicar reintentos y guardan cuándo pasó
     * de verdad. Null en el uso normal.
     */
    CajaResponseDTO registrarRetiro(Long usuarioId, BigDecimal monto, String motivo, TipoMovimientoCaja tipo,
                                     String idempotencyKey, LocalDateTime fechaOriginal);

    /** Como registrarRetiro, en la caja de otro usuario. Sin cola offline. */
    CajaResponseDTO registrarRetiroComoAdmin(Long cajaId, BigDecimal monto, String motivo, TipoMovimientoCaja tipo);

    /** Suma (INGRESO) o resta (RETIRO) entradas físicas al talonario de la caja abierta. */
    CajaResponseDTO registrarIngresoEntradas(Long usuarioId, Integer cantidad, String motivo, TipoMovimientoEntradas tipo,
                                              String idempotencyKey, LocalDateTime fechaOriginal);

    /** Como registrarIngresoEntradas, en la caja de otro usuario. Sin cola offline. */
    CajaResponseDTO registrarIngresoEntradasComoAdmin(Long cajaId, Integer cantidad, String motivo, TipoMovimientoEntradas tipo);

    CajaResponseDTO cerrar(Long usuarioId, List<ConteoDenominacionDTO> conteoEfectivo,
                            List<CierrePosnetRequestDTO> cierresPosnet, Integer entradasFisicasRestantes,
                            BigDecimal cambioContado, BigDecimal dolaresContado);

    /** Como cerrar, para la caja de otro usuario. */
    CajaResponseDTO cerrarComoAdmin(Long cajaId, List<ConteoDenominacionDTO> conteoEfectivo,
                                     List<CierrePosnetRequestDTO> cierresPosnet, Integer entradasFisicasRestantes,
                                     BigDecimal cambioContado, BigDecimal dolaresContado);

    /**
     * Corrige una caja cerrada en una sola transacción: reescribe el recuento y registra los traspasos entre
     * formas de pago como AjusteCaja, sin tocar las compras. `ajustes` puede venir vacío.
     */
    CajaResponseDTO corregirCaja(Long cajaId, List<ConteoDenominacionDTO> conteoEfectivo,
                                  List<CierrePosnetRequestDTO> cierresPosnet, Integer entradasFisicasRestantes,
                                  BigDecimal cambioContado, BigDecimal dolaresContado,
                                  List<AjusteCajaRequestDTO> ajustes);

    /**
     * Reabre una caja cerrada para reaplicarle una operación rechazada. No aplica nada por sí sola: quien
     * llama usa los métodos normales para que se revalide todo, y después recerrarConElUltimoConteo.
     */
    Caja reabrir(Long cajaId);

    /** Vuelve a cerrar una caja reabierta con el mismo conteo que tenía. */
    CajaResponseDTO recerrarConElUltimoConteo(Long cajaId);

    /** Detalle de cualquier caja. */
    CajaResponseDTO getDetalle(Long cajaId);

    /** Todas las cajas abiertas, de cualquier boletero. */
    List<CajaAbiertaDTO> getCajasAbiertas();

    /** Cajas abiertas desde un día anterior (aviso CAJA_ATRASADA). */
    List<Long> getIdsCajasAtrasadas();

    /**
     * Operaciones de una caja, también abierta, con el resumen de lo vendido. Sólo ADMIN: al boletero no se
     * le muestra para que no ajuste el conteo.
     */
    CajaDetalleAbiertaDTO getOperacionesCaja(Long cajaId);

    /**
     * Cajas cerradas paginadas en la base, filtrables por boletero. ordenarPor no admite "totalRetiros" (es
     * un SUM, no una columna).
     */
    CajasCerradasResponseDTO getCajasCerradas(String usuarioNombre, String ordenarPor, String direccion,
                                               int page, int size);

    /** Boleteros con alguna caja cerrada habilitada, para el filtro. */
    java.util.List<String> getBoleterosConCajasCerradas();

    /** Deshace un ajuste manual: borra la fila y recalcula el cierre. ADMIN-only. */
    CajaResponseDTO eliminarAjuste(Long cajaId, Long ajusteId);

    /** Diferencia de tarjeta + QR por caja, con ajustes (positivo = sobró). En lote para listados y KPIs. */
    java.util.Map<Long, java.math.BigDecimal> diferenciaPosnetPorCaja(java.util.List<Long> cajaIds);

    /** Saca una caja cerrada de listados, KPIs y reporte (habilitada=false). Irreversible desde la app. */
    CajaResponseDTO deshabilitarCaja(Long cajaId);
}
