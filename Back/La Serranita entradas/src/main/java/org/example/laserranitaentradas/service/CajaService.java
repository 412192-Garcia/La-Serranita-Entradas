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

    /** Devuelve la caja abierta de ese usuario o lanza si no tiene ninguna: la usan las ventas para bloquearse sin caja. */
    Caja getAbiertaOrThrow(Long usuarioId);

    CajaResponseDTO abrir(Long usuarioId, BigDecimal montoInicial, Integer entradasFisicasInicial);

    /**
     * idempotencyKey/fechaOriginal vienen sólo del POS con cola offline: la clave evita duplicar
     * si se reintenta un movimiento cuya respuesta se perdió en un corte, y la fecha registra
     * cuándo pasó de verdad en vez de cuándo se sincronizó. Ambos null desde el uso normal.
     */
    CajaResponseDTO registrarRetiro(Long usuarioId, BigDecimal monto, String motivo, TipoMovimientoCaja tipo,
                                     String idempotencyKey, LocalDateTime fechaOriginal);

    /** Igual que registrarRetiro, pero para que un ADMIN lo cargue en la caja de OTRO usuario (ADMIN-only, gateado en SecurityConfig). Sin cola offline: el admin siempre opera con conexión. */
    CajaResponseDTO registrarRetiroComoAdmin(Long cajaId, BigDecimal monto, String motivo, TipoMovimientoCaja tipo);

    /**
     * Suma o resta entradas físicas al talonario de la caja abierta: INGRESO cuando el boletero
     * se quedó sin y le traen más, RETIRO para sacarle entradas y dárselas a otro boletero (motivo
     * opcional). Ver registrarRetiro por idempotencyKey/fechaOriginal.
     */
    CajaResponseDTO registrarIngresoEntradas(Long usuarioId, Integer cantidad, String motivo, TipoMovimientoEntradas tipo,
                                              String idempotencyKey, LocalDateTime fechaOriginal);

    /** Igual que registrarIngresoEntradas, pero para que un ADMIN lo cargue en la caja de OTRO usuario por id (ADMIN-only, gateado en SecurityConfig). Sin cola offline. */
    CajaResponseDTO registrarIngresoEntradasComoAdmin(Long cajaId, Integer cantidad, String motivo, TipoMovimientoEntradas tipo);

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

    /**
     * Reabre momentáneamente una caja ya cerrada (fechaCierre → null) para poder aplicarle una
     * operación que se había rechazado porque esa caja ya no estaba abierta — retiro/aporte,
     * ingreso de entradas, o una venta. No aplica ningún movimiento por sí sola: eso lo hace
     * quien llama, usando los métodos normales de siempre (registrarRetiro,
     * registrarIngresoEntradas, CompraService.registrarVentaPos) contra ESTA caja ya reabierta
     * — así cada operación se revalida en vivo con las reglas reales (cupo, promo vigente, etc.)
     * en vez de forzarse a ciegas. Lanza si la caja no existe o si sigue abierta (nada que
     * reabrir). ADMIN-only (gateado en SecurityConfig, vía RechazoOperacionController), que es
     * quien orquesta reabrir → reaplicar (una o varias operaciones de la misma caja) →
     * recerrarConElUltimoConteo.
     */
    Caja reabrir(Long cajaId);

    /**
     * Vuelve a cerrar una caja que se reabrió con reabrir(), reutilizando el mismo conteo
     * (denominaciones, posnet, entradas cortadas, cambio, dólares) que ya tenía guardado de
     * antes — no hace falta pedirle a nadie que cuente billetes de nuevo por operaciones que ni
     * siquiera vio. Lanza si la caja no existe o si ya está cerrada.
     */
    CajaResponseDTO recerrarConElUltimoConteo(Long cajaId);

    /** Detalle completo de cualquier caja (para que el admin la revise sin importar quién la abrió). */
    CajaResponseDTO getDetalle(Long cajaId);

    /** Todas las cajas abiertas ahora mismo, sin importar de qué boletero — para el dashboard del admin. */
    List<CajaAbiertaDTO> getCajasAbiertas();

    /**
     * Ventas, retiros/aportes e ingresos de entradas de una caja, en orden cronológico, más un
     * resumen de lo vendido hasta el momento (por forma de pago y por tipo de entrada) — a
     * diferencia de getDetalle(), funciona con la caja todavía ABIERTA (no espera al cierre):
     * lo usa el admin para revisar y corregir una venta mal cargada mientras el boletero sigue
     * trabajando. ADMIN-only (gateado en SecurityConfig): a un boletero no se le puede mostrar
     * esto de su propia caja en curso, se prestaría a ajustar el conteo para que cierre justo.
     */
    CajaDetalleAbiertaDTO getOperacionesCaja(Long cajaId);

    /**
     * Cajas cerradas dentro del rango, paginadas y opcionalmente filtradas por boletero — para el
     * listado de "Cajas cerradas" en la pantalla de Cajas. A diferencia del reporte agregado
     * (ReporteService.getResumen, que trae TODAS las cajas del rango de una para calcular varios
     * KPIs a la vez), esto pagina en la base: soporta boleteros con meses de turnos sin traerlos
     * todos a memoria. Los totales de retiros/faltantes/sobrantes son de TODO lo que matchea el
     * filtro, no sólo la página (ver CajasCerradasResponseDTO). ordenarPor admite cualquier campo
     * de CajaResumenReporteDTO excepto "totalRetiros" (se computa con un JOIN + SUM, no es una
     * columna propia de Caja para ordenar en la base sin recorrer todo el rango).
     */
    CajasCerradasResponseDTO getCajasCerradas(LocalDate desde, LocalDate hasta, String usuarioNombre,
                                               String ordenarPor, String direccion, int page, int size);

    /**
     * Registra uno o más traspasos manuales de monto entre formas de pago sobre una caja YA
     * CERRADA (el admin corrigiendo la repartición cuando la cajera cobró de una forma y tocó
     * otra). No toca las compras: cada traspaso queda como un AjusteCaja aparte. Recalcula y
     * persiste montoEsperado/diferencia para que el listado de cajas cerradas y el ranking
     * queden consistentes. ADMIN-only (gateado en SecurityConfig).
     */
    CajaResponseDTO registrarAjustes(Long cajaId, List<AjusteCajaRequestDTO> ajustes);

    /** Deshace un ajuste manual: borra la fila y recalcula el cierre. ADMIN-only. */
    CajaResponseDTO eliminarAjuste(Long cajaId, Long ajusteId);
}
