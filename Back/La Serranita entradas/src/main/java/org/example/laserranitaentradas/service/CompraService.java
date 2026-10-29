package org.example.laserranitaentradas.service;

import com.mercadopago.resources.payment.Payment;
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
    /** El código de reserva es también el external_reference de Mercado Pago. */
    Optional<Compra> findByCodigoReserva(String codigoReserva);
    Optional<Compra> findByDniandFecha(String dni, LocalDate fechaVisita);

    /** Búsqueda paginada de boletería. */
    Page<Compra> buscar(BusquedaComprasFiltroDTO filtro, Pageable pageable);

    /**
     * Días distintos con compras que matchean los filtros de `buscar`, paginados por día para no partir un
     * día entre páginas.
     */
    Page<LocalDate> fechasDistintas(BusquedaComprasFiltroDTO filtro, Pageable pageable);

    Compra create(CompraRequestDTO Compra);
    Compra marcarEntradasComoUsadas(Long compraId, Long usuarioValidadorId);

    /**
     * Cobra en efectivo una reserva y habilita el ingreso. Con `permitirSinCaja` (ADMIN) la compra queda sin
     * caja, fuera de cualquier cierre.
     */
    Compra confirmarPagoEfectivo(Long compraId, Long usuarioValidadorId, boolean permitirSinCaja);

    /** Deshace una validación reciente: vuelve al estado previo y limpia validador, fecha y caja. */
    Compra deshacerValidacion(Long compraId);
    CotizacionResponseDTO cotizar(CotizacionRequestDTO cotizacionRequest);
    Optional<String> consultarEstadoCompra(Long id);
    Compra actualizarEstado(Long compraId, EstadoCompra nuevoEstado);
    CompraResponseDTO iniciarCompraConPago(CompraRequestDTO compraRequest) throws Exception;

    /**
     * Marca la compra APROBADO, guarda los pagos de Mercado Pago (para reembolsar) y manda el comprobante.
     * Idempotente: false si no había nada que hacer.
     */
    boolean confirmarAprobado(Long compraId, List<Long> pagosMercadoPago, org.example.laserranitaentradas.model.entity.ConfirmacionPago origen);

    /**
     * Confirma la compra de un pago avisado por el webhook. Los pagos aprobados tienen que cubrir exactamente
     * el total, solos o sumados.
     */
    boolean confirmarPagoMercadoPago(Payment pago);

    /**
     * Si la compra sigue PENDIENTE_PAGO, consulta a Mercado Pago directamente por si el webhook no llegó.
     * Devuelve el estado resultante.
     */
    String verificarPagoDirecto(Long compraId);

    /** Compras en PENDIENTE_PAGO hace más de `horasAntiguedad`. */
    List<Long> idsCheckoutsAbandonados(int horasAntiguedad);

    /**
     * Cierra un checkout abandonado: si el pago entró igual la confirma; si no, la cancela y devuelve el uso
     * del cupón.
     */
    void expirarCheckoutAbandonado(Long compraId);

    /** Corrige titular y contacto. No toca fecha, entradas ni montos. */
    Compra actualizarContacto(Long compraId, EditarContactoRequest request);

    /** Reenvía el comprobante; sólo para APROBADO o USADO. */
    void reenviarComprobante(Long compraId);

    /** Venta en boletería: nace USADO y sin datos de cliente. */
    Compra registrarVentaPos(VentaPosRequestDTO request, Long usuarioVendedorId);

    /** Como registrarVentaPos, en la caja de otro usuario. Queda a nombre del dueño de la caja. */
    Compra registrarVentaPosComoAdmin(Long cajaId, VentaPosRequestDTO request);

    /** Reembolsa por Mercado Pago una compra APROBADO y la pasa a REEMBOLSADA. */
    Compra reembolsarCompra(Long compraId);

    /** Cancela una RESERVA_ADMIN que no ingresó. No hay reembolso. */
    Compra cancelarReservaAdmin(Long compraId);

    /**
     * Cambia el día de una reserva que no ingresó. El día nuevo tiene que estar abierto y con cupo. Un regalo
     * no recibe fecha. Código y precio no cambian.
     */
    Compra cambiarFechaVisita(Long compraId, LocalDate nuevaFecha, boolean avisarPorMail);

    /** Cancela una venta de caja (queda CANCELADO, no se borra) y deja de contar en cupo, caja y reportes. */
    Compra cancelarVenta(Long compraId, String motivo);

    /**
     * Corrige entradas y forma de pago de una venta de caja. Revalida el cupo y recalcula el precio; el
     * descuento queda igual. No toca artículos varios.
     */
    Compra editarVenta(Long compraId, EditarVentaRequestDTO request);
}
