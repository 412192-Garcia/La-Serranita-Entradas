package org.example.laserranitaentradas.service;

import org.example.laserranitaentradas.model.dto.OperacionRechazadaResponseDTO;

import java.util.List;

public interface RechazoOperacionService {

    /**
     * Registra una operación del POS rechazada en segundo plano o un mail que no salió: es el único rastro
     * para el admin. payload se guarda como JSON.
     */
    void registrar(String tipoOperacion, Object payload, String motivo, String idempotencyKey);

    /** Null = todas; true/false = sólo pendientes o sólo resueltas. */
    List<OperacionRechazadaResponseDTO> listar(Boolean resuelto);

    /** Ids de rechazos pendientes (aviso RECHAZO_OPERACION). */
    List<Long> getIdsPendientes();

    OperacionRechazadaResponseDTO resolver(Long id, String nota);

    /**
     * Para RETIRO_APORTE e INGRESO_ENTRADAS rechazados por caja cerrada: reabre la caja, aplica el
     * movimiento, la recierra con el mismo conteo y marca el rechazo resuelto.
     */
    OperacionRechazadaResponseDTO reabrirYReintentar(Long id);

    /**
     * Como reabrirYReintentar, para varios rechazos de la misma caja: la reabre y la recierra una sola vez.
     */
    List<OperacionRechazadaResponseDTO> reabrirYReintentarLote(List<Long> ids);
}
