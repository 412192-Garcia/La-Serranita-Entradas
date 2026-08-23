package org.example.laserranitaentradas.service;

import org.example.laserranitaentradas.model.dto.BusquedaComprasFiltroDTO;
import org.example.laserranitaentradas.model.dto.CompraRequestDTO;
import org.example.laserranitaentradas.model.dto.CompraResponseDTO;
import org.example.laserranitaentradas.model.dto.CotizacionRequestDTO;
import org.example.laserranitaentradas.model.dto.CotizacionResponseDTO;
import org.example.laserranitaentradas.model.dto.EditarContactoRequest;
import org.example.laserranitaentradas.model.dto.EditarVentaRequestDTO;
import org.example.laserranitaentradas.model.dto.VentaPosRequestDTO;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.EstadoCompra;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CompraService {
    Optional<Compra> findById(Long id);
    Optional<Compra> findByDniandFecha(String dni, LocalDate fechaVisita);

    /** Búsqueda paginada de boletería: texto libre, fecha, boletería/anticipada, estado(s) y forma de pago. */
    Page<Compra> buscar(BusquedaComprasFiltroDTO filtro, Pageable pageable);

    /**
     * Días distintos (fechaVisita) con al menos una compra que matchee los mismos filtros que
     * `buscar`, paginados por cantidad de días (no de compras). Lo usa la vista agrupada por
     * día de Boletería para paginar sin partir un día a la mitad entre dos páginas: primero se
     * pide la página de días con este método, y después se trae todo lo de esos días con
     * `buscar` (fechaDesde/fechaHasta = primer/último día de la página).
     */
    Page<LocalDate> fechasDistintas(BusquedaComprasFiltroDTO filtro, Pageable pageable);

    Compra create(CompraRequestDTO Compra);
    Compra marcarEntradasComoUsadas(Long compraId, Long usuarioValidadorId);
    Compra confirmarPagoEfectivo(Long compraId, Long usuarioValidadorId);

    /**
     * Deshace una validación reciente (ventana corta, ver implementación): vuelve la compra
     * al estado previo (APROBADO o RESERVADO_EFECTIVO según la forma de pago) y limpia
     * validador/fecha/caja. Pensado para el "cancelar validación" que ofrece boletería
     * mientras la fila todavía no desapareció de la lista.
     */
    Compra deshacerValidacion(Long compraId);
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

    /**
     * Venta presencial en boletería: cobra y habilita el ingreso en un solo paso, así que
     * la compra nace directamente en USADO y sin datos de cliente.
     */
    Compra registrarVentaPos(VentaPosRequestDTO request, Long usuarioVendedorId);

    /**
     * Reembolsa una compra pagada online (Mercado Pago) que todavía no fue utilizada:
     * llama a la API de reembolsos de Mercado Pago y, si funciona, pasa la compra a
     * REEMBOLSADA. Sólo válido en estado APROBADO.
     */
    Compra reembolsarCompra(Long compraId);

    /**
     * Cancela una venta de puerta mal cargada (ADMIN, desde el detalle de una caja): la marca
     * CANCELADO en vez de borrarla, para no perder el rastro por si hay que auditar el error
     * después. Al quedar CANCELADO, deja de contar para el cupo diario, los totales de caja y
     * los reportes (ver construirDetalles/CajaServiceImpl/ReporteServiceImpl, que ya excluyen
     * este estado). Sólo para compras que pasaron por una caja (compra.caja != null); una
     * reserva online no se cancela por acá.
     */
    Compra cancelarVenta(Long compraId);

    /**
     * Corrige las entradas y/o la forma de pago de una venta de puerta ya cargada (ADMIN, desde
     * el detalle de una caja) — ej. el cajero tocó 3 en vez de 2, o cobró Efectivo en vez de
     * Tarjeta. Revalida el cupo diario contra las nuevas cantidades (sin contar dos veces lo que
     * esta misma compra ya tenía) y recalcula el monto de las entradas con el motor de precios
     * real; el descuento ya aplicado se mantiene como estaba. No toca artículos varios: si el
     * error está ahí, se cancela la venta y se vuelve a cargar entera.
     */
    Compra editarVenta(Long compraId, EditarVentaRequestDTO request);
}
