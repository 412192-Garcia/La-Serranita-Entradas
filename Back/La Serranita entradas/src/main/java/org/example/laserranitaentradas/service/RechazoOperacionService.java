package org.example.laserranitaentradas.service;

import org.example.laserranitaentradas.model.dto.OperacionRechazadaResponseDTO;

import java.util.List;

public interface RechazoOperacionService {

    /**
     * Registra que algo desatendido falló sin que nadie lo estuviera mirando en pantalla: una
     * operación del POS rechazada en un reintento en segundo plano (venta, retiro/aporte, ingreso
     * o retiro de entradas), o el envío de un email de confirmación que no se pudo completar.
     * En ambos casos no queda ningún otro rastro, así que esto es lo único que le avisa a un admin.
     * payload se serializa tal cual a JSON para poder mostrarlo después sin adivinar su forma.
     */
    void registrar(String tipoOperacion, Object payload, String motivo, String idempotencyKey);

    /** Null = todas; true/false = sólo pendientes o sólo resueltas. */
    List<OperacionRechazadaResponseDTO> listar(Boolean resuelto);

    OperacionRechazadaResponseDTO resolver(Long id, String nota);

    /**
     * Sólo para RETIRO_APORTE e INGRESO_ENTRADAS rechazados porque la caja de origen ya no
     * estaba abierta (guardada en el payload como cajaId, ver RetiroCajaRequestDTO/
     * IngresoEntradasRequestDTO): reabre esa caja, aplica el movimiento que se había perdido, y
     * la vuelve a cerrar con el mismo conteo que ya tenía — sin pedirle a nadie que cuente
     * billetes de nuevo. Si funciona, marca el rechazo resuelto solo (reabrir y reintentar YA ES
     * la resolución). Lanza si el rechazo no existe, ya está resuelto, es de un tipo que no
     * soporta este camino, o no tiene guardado de qué caja se trata (rechazos de antes de este
     * campo). Atajo de reabrirYReintentarLote con un solo id.
     */
    OperacionRechazadaResponseDTO reabrirYReintentar(Long id);

    /**
     * Igual que reabrirYReintentar, pero para VARIOS rechazos a la vez, siempre que sean todos
     * RETIRO_APORTE/INGRESO_ENTRADAS de la MISMA caja: si se encolaron varias operaciones sin
     * conexión y la caja se cerró antes de que se reintentaran todas, cada una queda como un
     * rechazo separado (uno por request) — sin este método, resolverlas de a una reabriría y
     * volvería a cerrar la misma caja una vez por cada una. Lanza si algún id no existe, alguno
     * ya está resuelto, no son todos de la misma caja, o alguno no tiene la caja guardada.
     */
    List<OperacionRechazadaResponseDTO> reabrirYReintentarLote(List<Long> ids);
}
