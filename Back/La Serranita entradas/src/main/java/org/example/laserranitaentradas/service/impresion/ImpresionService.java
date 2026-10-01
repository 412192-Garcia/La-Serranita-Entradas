package org.example.laserranitaentradas.service.impresion;

import org.example.laserranitaentradas.model.entity.EstadoTrabajoImpresion;
import org.example.laserranitaentradas.model.entity.Factura;
import org.example.laserranitaentradas.model.entity.TrabajoImpresion;
import org.example.laserranitaentradas.repository.FacturaRepository;
import org.example.laserranitaentradas.repository.TrabajoImpresionRepository;
import org.example.laserranitaentradas.service.factura.ComprobanteFacturaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Impresión de tickets en la boletería a través del agente de la PC de la entrada.
 *
 * El parque está detrás de Starlink (CGNAT): desde el servidor no se puede entrar a su red. Por
 * eso es el agente el que se conecta: abre una conexión SSE hacia el backend al arrancar, se
 * anuncia con sus impresoras, y queda escuchando. Cuando hay que imprimir, el backend le empuja el
 * ticket ya armado en ESC/POS por esa conexión, y el agente lo manda a la impresora por la red
 * local (puerto 9100) y confirma.
 *
 * Los agentes conectados viven en memoria (una sola instancia de backend, igual que el lock de
 * emisión de facturas). Los trabajos, en la base: si la conexión se corta, al reconectar se le
 * reenvían los que no confirmó y el agente descarta los que ya había impreso.
 */
@Service
public class ImpresionService {

    private static final Logger log = LoggerFactory.getLogger(ImpresionService.class);

    /** Impresora "cualquiera": la tablet no eligió y al encolar no había ninguna conectada. Se
     * resuelve a la primera que aparezca. */
    static final String CUALQUIERA = "*";
    private static final List<EstadoTrabajoImpresion> SIN_CONFIRMAR =
            List.of(EstadoTrabajoImpresion.PENDIENTE, EstadoTrabajoImpresion.ENVIADO);

    private final TrabajoImpresionRepository trabajoRepository;
    private final FacturaRepository facturaRepository;
    private final ComprobanteFacturaService comprobanteService;
    private final TicketEscPosGenerator escPos;
    private final TransactionTemplate tx;

    private final Map<String, AgenteConectado> agentes = new ConcurrentHashMap<>();

    /** Un ticket que no se pudo imprimir en este tiempo ya no se imprime solo: el cliente se fue
     * y que salga de golpe horas después (al prenderse la PC) sólo confunde. Se reimprime a mano. */
    @Value("${impresion.minutos-vencimiento:15}")
    private int minutosVencimiento;

    public ImpresionService(TrabajoImpresionRepository trabajoRepository, FacturaRepository facturaRepository,
                            ComprobanteFacturaService comprobanteService, TicketEscPosGenerator escPos,
                            PlatformTransactionManager transactionManager) {
        this.trabajoRepository = trabajoRepository;
        this.facturaRepository = facturaRepository;
        this.comprobanteService = comprobanteService;
        this.escPos = escPos;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public record ImpresoraConectada(String nombre, String agente) {}

    private record AgenteConectado(String nombre, Set<String> impresoras, SseEmitter emitter) {}

    // ---------- Agentes ----------

    public SseEmitter conectarAgente(String nombre, Set<String> impresoras) {
        // Sin timeout: la conexión dura lo que dure el agente prendido. Un corte se detecta al
        // fallar el envío del heartbeat.
        SseEmitter emitter = new SseEmitter(0L);
        AgenteConectado agente = new AgenteConectado(nombre, Set.copyOf(impresoras), emitter);
        AgenteConectado anterior = agentes.put(nombre, agente);
        if (anterior != null) {
            // El mismo agente reconectó antes de que se detectara la caída de la conexión vieja.
            anterior.emitter().complete();
        }
        Runnable alCerrar = () -> {
            if (agentes.remove(nombre, agente)) {
                log.info("Agente de impresión '{}' desconectado", nombre);
            }
        };
        emitter.onCompletion(alCerrar);
        emitter.onTimeout(alCerrar);
        emitter.onError(e -> alCerrar.run());
        log.info("Agente de impresión '{}' conectado con impresoras {}", nombre, impresoras);

        enviar(agente, "conectado", "ok");
        reenviarSinConfirmar(agente);
        return emitter;
    }

    public List<ImpresoraConectada> impresorasConectadas() {
        List<ImpresoraConectada> salida = new ArrayList<>();
        for (AgenteConectado a : agentes.values()) {
            for (String impresora : a.impresoras()) {
                salida.add(new ImpresoraConectada(impresora, a.nombre()));
            }
        }
        salida.sort(Comparator.comparing(ImpresoraConectada::nombre));
        return salida;
    }

    // ---------- Trabajos ----------

    /** Encola el ticket de una factura emitida y lo despacha si su impresora está conectada. */
    public TrabajoImpresion imprimirFactura(Long facturaId, String impresoraPedida) {
        String impresora = resolverImpresora(impresoraPedida);
        TrabajoImpresion trabajo = tx.execute(s -> {
            Factura factura = facturaRepository.findById(facturaId)
                    .orElseThrow(() -> new IllegalArgumentException("Factura no encontrada ID: " + facturaId));
            return trabajoRepository.save(TrabajoImpresion.builder()
                    .factura(factura)
                    .impresora(impresora)
                    .build());
        });
        despachar(trabajo.getId());
        return trabajoRepository.findById(trabajo.getId()).orElse(trabajo);
    }

    public Optional<TrabajoImpresion> ultimoTrabajo(Long facturaId) {
        return trabajoRepository.findFirstByFacturaIdOrderByIdDesc(facturaId);
    }

    public void registrarResultado(Long trabajoId, boolean ok, String error) {
        tx.executeWithoutResult(s -> trabajoRepository.findById(trabajoId).ifPresent(t -> {
            // Un IMPRESO no se pisa: puede llegar una confirmación repetida de un reenvío.
            if (t.getEstado() == EstadoTrabajoImpresion.IMPRESO) return;
            t.setEstado(ok ? EstadoTrabajoImpresion.IMPRESO : EstadoTrabajoImpresion.ERROR);
            t.setError(ok ? null : recortar(error));
            t.setFechaResultado(LocalDateTime.now());
            trabajoRepository.save(t);
        }));
        if (!ok) {
            log.warn("El agente no pudo imprimir el trabajo ID {}: {}", trabajoId, error);
        }
    }

    private String resolverImpresora(String pedida) {
        if (pedida != null && !pedida.isBlank()) return pedida.trim();
        List<ImpresoraConectada> conectadas = impresorasConectadas();
        return conectadas.size() == 1 ? conectadas.get(0).nombre() : CUALQUIERA;
    }

    private void despachar(Long trabajoId) {
        TrabajoImpresion trabajo = trabajoRepository.findById(trabajoId).orElse(null);
        if (trabajo == null || !SIN_CONFIRMAR.contains(trabajo.getEstado())) return;
        AgenteConectado agente = agenteCon(trabajo.getImpresora());
        if (agente == null) {
            log.info("Trabajo de impresión ID {} en espera: la impresora '{}' no está conectada", trabajoId, trabajo.getImpresora());
            return;
        }
        String impresora = CUALQUIERA.equals(trabajo.getImpresora())
                ? agente.impresoras().iterator().next()
                : trabajo.getImpresora();

        byte[] ticket;
        try {
            ticket = escPos.generar(comprobanteService.armar(trabajo.getFactura().getId()));
        } catch (Exception e) {
            log.error("No se pudo armar el ticket del trabajo ID {}", trabajoId, e);
            registrarResultado(trabajoId, false, "No se pudo armar el ticket: " + e.getMessage());
            return;
        }

        // Formato del evento: "id;impresora;ticket en base64". Sin JSON a propósito: así el
        // agente no necesita ninguna librería. Los nombres de impresora no llevan ';' (se valida
        // al conectar).
        String datos = trabajoId + ";" + impresora + ";" + Base64.getEncoder().encodeToString(ticket);
        if (!enviar(agente, "trabajo", datos)) return;

        tx.executeWithoutResult(s -> trabajoRepository.findById(trabajoId).ifPresent(t -> {
            if (t.getEstado() == EstadoTrabajoImpresion.PENDIENTE) {
                t.setEstado(EstadoTrabajoImpresion.ENVIADO);
            }
            t.setImpresora(impresora);
            t.setFechaEnvio(LocalDateTime.now());
            trabajoRepository.save(t);
        }));
    }

    private void reenviarSinConfirmar(AgenteConectado agente) {
        Set<String> nombres = new HashSet<>(agente.impresoras());
        nombres.add(CUALQUIERA);
        LocalDateTime limite = LocalDateTime.now().minusMinutes(minutosVencimiento);
        for (TrabajoImpresion t : trabajoRepository.findByEstadoInAndImpresoraInOrderByIdAsc(SIN_CONFIRMAR, nombres)) {
            if (t.getFechaCreacion() != null && t.getFechaCreacion().isBefore(limite)) continue;
            despachar(t.getId());
        }
    }

    private AgenteConectado agenteCon(String impresora) {
        for (AgenteConectado a : agentes.values()) {
            if (a.impresoras().isEmpty()) continue;
            if (CUALQUIERA.equals(impresora) || a.impresoras().contains(impresora)) return a;
        }
        return null;
    }

    private boolean enviar(AgenteConectado agente, String evento, String datos) {
        try {
            synchronized (agente.emitter()) {
                agente.emitter().send(SseEmitter.event().name(evento).data(datos));
            }
            return true;
        } catch (IOException | IllegalStateException e) {
            log.info("No se pudo enviar al agente '{}' ({}): se da por desconectado", agente.nombre(), e.getMessage());
            agentes.remove(agente.nombre(), agente);
            agente.emitter().completeWithError(e);
            return false;
        }
    }

    /**
     * Cada 20 s: un comentario SSE a cada agente para que ningún proxy (nginx corta a los 60 s sin
     * datos) cierre la conexión y para detectar rápido los agentes caídos. De paso vence los
     * trabajos que quedaron demasiado tiempo sin imprimirse.
     */
    @Scheduled(fixedDelay = 20_000, initialDelay = 20_000)
    public void latido() {
        for (AgenteConectado a : List.copyOf(agentes.values())) {
            try {
                synchronized (a.emitter()) {
                    a.emitter().send(SseEmitter.event().comment("latido"));
                }
            } catch (IOException | IllegalStateException e) {
                agentes.remove(a.nombre(), a);
                a.emitter().completeWithError(e);
                log.info("Agente de impresión '{}' sin respuesta: desconectado", a.nombre());
            }
        }
        LocalDateTime limite = LocalDateTime.now().minusMinutes(minutosVencimiento);
        tx.executeWithoutResult(s -> {
            for (TrabajoImpresion t : trabajoRepository.findByEstadoInAndFechaCreacionBefore(SIN_CONFIRMAR, limite)) {
                t.setEstado(EstadoTrabajoImpresion.ERROR);
                t.setError("No se pudo imprimir a tiempo (impresora desconectada). Reimprimilo si hace falta.");
                t.setFechaResultado(LocalDateTime.now());
                trabajoRepository.save(t);
            }
        });
    }

    private static String recortar(String texto) {
        if (texto == null) return null;
        return texto.length() > 500 ? texto.substring(0, 500) : texto;
    }
}
