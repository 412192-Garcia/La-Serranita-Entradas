package org.example.laserranitaentradas.monitoreo;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.example.laserranitaentradas.model.dto.EstadoSistemaDTO.Tarjeta;
import org.example.laserranitaentradas.model.entity.EstadoCompra;
import org.example.laserranitaentradas.model.entity.FormaPago;
import org.example.laserranitaentradas.model.entity.TerminalPos;
import org.example.laserranitaentradas.repository.CompraRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Métricas propias de la app para Grafana (además de las que Spring ya publica: requests por
 * endpoint, JVM, pool de conexiones). Se recalculan una vez por minuto y Prometheus lee el último
 * valor, así cada lectura no le pega a la base.
 *
 * - serranita_tarjeta_alerta{tarjeta="..."}: 1 si esa tarjeta de Sistema > Estado está en rojo.
 *   Sirve para armar alertas en Grafana con el historial de cuándo pasó y cuánto duró.
 * - serranita_ventas_ultima_hora{canal="online|puerta"}, serranita_validaciones_ultima_hora.
 * - serranita_incidentes_pendientes, serranita_terminales_operaciones_pendientes.
 */
@Component
public class MetricasNegocio {

    private static final Logger log = LoggerFactory.getLogger(MetricasNegocio.class);

    private final MeterRegistry registry;
    private final EstadoSistemaService estadoSistemaService;
    private final IncidenteService incidenteService;
    private final TerminalesService terminalesService;
    private final CompraRepository compraRepository;

    private final Map<String, AtomicLong> tarjetas = new ConcurrentHashMap<>();
    private final AtomicLong ventasOnline = new AtomicLong();
    private final AtomicLong ventasPuerta = new AtomicLong();
    private final AtomicLong validaciones = new AtomicLong();
    private final AtomicLong incidentes = new AtomicLong();
    private final AtomicLong operacionesPendientes = new AtomicLong();

    public MetricasNegocio(MeterRegistry registry, EstadoSistemaService estadoSistemaService, IncidenteService incidenteService,
                           TerminalesService terminalesService, CompraRepository compraRepository) {
        this.registry = registry;
        this.estadoSistemaService = estadoSistemaService;
        this.incidenteService = incidenteService;
        this.terminalesService = terminalesService;
        this.compraRepository = compraRepository;
        Gauge.builder("serranita.ventas.ultima.hora", ventasOnline, AtomicLong::get).tag("canal", "online")
                .description("Compras online pagadas en la última hora").register(registry);
        Gauge.builder("serranita.ventas.ultima.hora", ventasPuerta, AtomicLong::get).tag("canal", "puerta")
                .description("Ventas en la boletería en la última hora").register(registry);
        Gauge.builder("serranita.validaciones.ultima.hora", validaciones, AtomicLong::get)
                .description("Anticipadas validadas en la puerta en la última hora").register(registry);
        Gauge.builder("serranita.incidentes.pendientes", incidentes, AtomicLong::get)
                .description("Errores sin revisar en Sistema > Errores").register(registry);
        Gauge.builder("serranita.terminales.operaciones.pendientes", operacionesPendientes, AtomicLong::get)
                .description("Operaciones del POS guardadas en las terminales sin subir al servidor").register(registry);
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void actualizar() {
        try {
            for (Tarjeta t : estadoSistemaService.estado().tarjetas()) {
                tarjetas.computeIfAbsent(t.clave(), clave -> {
                    AtomicLong valor = new AtomicLong();
                    Gauge.builder("serranita.tarjeta.alerta", valor, AtomicLong::get).tag("tarjeta", clave)
                            .description("1 si la tarjeta de Sistema > Estado está en rojo").register(registry);
                    return valor;
                }).set("ALERTA".equals(t.estado()) ? 1 : 0);
            }
            LocalDateTime ahora = LocalDateTime.now(AuditoriaService.ZONA);
            LocalDateTime haceUnaHora = ahora.minusHours(1);
            ventasOnline.set(compraRepository.countByFormaPagoAndEstadoInAndFechaCreacionBetween(
                    FormaPago.MERCADO_PAGO, AnomaliasVentas.PAGADAS, haceUnaHora, ahora));
            ventasPuerta.set(compraRepository.countByEstadoAndFechaCreacionBetween(EstadoCompra.VENDIDO_EN_PUERTA, haceUnaHora, ahora));
            validaciones.set(compraRepository.countByEstadoAndFechaValidacionBetween(EstadoCompra.USADO, haceUnaHora, ahora));
            incidentes.set(incidenteService.pendientes());
            operacionesPendientes.set(terminalesService.recientes().stream().mapToLong(TerminalPos::getPendientes).sum());
        } catch (RuntimeException e) {
            // Sin base las métricas quedan con el último valor; el chequeo de salud ya avisa de eso.
            log.warn("No se pudieron actualizar las métricas de negocio: {}", e.getMessage());
        }
    }
}
