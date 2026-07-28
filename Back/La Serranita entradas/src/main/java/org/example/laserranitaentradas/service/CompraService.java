package org.example.laserranitaentradas.service;

import org.example.laserranitaentradas.model.dto.CompraRequestDTO;
import org.example.laserranitaentradas.model.dto.CompraResponseDTO;
import org.example.laserranitaentradas.model.dto.CotizacionRequestDTO;
import org.example.laserranitaentradas.model.dto.CotizacionResponseDTO;
import org.example.laserranitaentradas.model.dto.EditarContactoRequest;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.EstadoCompra;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CompraService {
    Optional<Compra> findById(Long id);
    Optional<Compra> findByDniandFecha(String dni, LocalDate fechaVisita);
    List<Compra> getAllByDni(String dni);
    List<Compra> getAllByFechaVisita(LocalDate fechaVisita);
    List<Compra> getAll();
    Compra create(CompraRequestDTO Compra);
    Compra marcarEntradasComoUsadas(Long compraId, Long usuarioValidadorId);
    Compra confirmarPagoEfectivo(Long compraId, Long usuarioValidadorId);
    CotizacionResponseDTO cotizar(CotizacionRequestDTO cotizacionRequest);
    Optional<String> consultarEstadoCompra(Long id);
    Compra actualizarEstado(Long compraId, EstadoCompra nuevoEstado);
    CompraResponseDTO iniciarCompraConPago(CompraRequestDTO compraRequest) throws Exception;

    /**
     * Marca la compra como APROBADO y envía el comprobante, de forma idempotente.
     * Devuelve false si no había nada que hacer (no existe, o ya estaba aprobada/usada).
     */
    boolean confirmarAprobado(Long compraId);

    /**
     * Si la compra sigue PENDIENTE_PAGO, le pregunta directamente a la API de Mercado
     * Pago (por external_reference) en vez de esperar al webhook — así una compra no
     * queda colgada si la notificación nunca llegó (por ejemplo, el túnel de notificaciones
     * caído). Devuelve el estado resultante.
     */
    String verificarPagoDirecto(Long compraId);

    /** Corrige nombre/apellido del titular y su contacto (email/teléfono). No toca fecha, entradas ni montos. */
    Compra actualizarContacto(Long compraId, EditarContactoRequest request);

    /** Reenvía el comprobante ya enviado; sólo tiene sentido si la compra está APROBADO o USADO. */
    void reenviarComprobante(Long compraId);
}
