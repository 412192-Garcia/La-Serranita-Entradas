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
import java.util.concurrent.atomic.AtomicLong;

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

    /**
     * Sin noticias del agente en este tiempo = la PC de la entrada se apagó o perdió internet. No
     * alcanza con esperar a que falle un envío: entre el backend y el agente hay intermediarios
     * (nginx, Caddy, el proxy de Docker) que siguen aceptando los latidos aunque del otro lado ya
     * no haya nadie, y la tablet seguía mostrando la ticketera como disponible. El agente informa
     * el estado de sus ticketeras cada 15 s: eso es la señal de vida (35 s = dos avisos perdidos).
     */
    @Value("${impresion.segundos-sin-noticias:35}")
    private int segundosSinNoticias = 35;

    public ImpresionService(TrabajoImpresionRepository trabajoRepository, FacturaRepository facturaRepository,
                            ComprobanteFacturaService comprobanteService, TicketEscPosGenerator escPos,
                            PlatformTransactionManager transactionManager) {
        this.trabajoRepository = trabajoRepository;
        this.facturaRepository = facturaRepository;
        this.comprobanteService = comprobanteService;
        this.escPos = escPos;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /**
     * Una ticketera de un agente conectado. disponible = el agente la ve prendida (responde por
     * red, o Windows la da por lista); si no, detalle dice qué le pasa ("apagada o desconectada",
     * "sin papel"...). Hasta que el agente informa (o con un agente viejo que no informa) se la
     * da por disponible: mejor intentar imprimir que bloquear sin motivo.
     */
    public record ImpresoraConectada(String nombre, String agente, boolean disponible, String detalle) {}

    public record EstadoImpresora(boolean disponible, String detalle) {}

    private record AgenteConectado(String nombre, Set<String> impresoras, SseEmitter emitter,
                                   Map<String, EstadoImpresora> estados, AtomicLong ultimoContactoMs) {
        void tocar() {
            ultimoContactoMs.set(System.currentTimeMillis());
        }
    }

    private boolean vivo(AgenteConectado a) {
        return System.currentTimeMillis() - a.ultimoContactoMs().get() <= segundosSinNoticias * 1000L;
    }

    // ---------- Agentes ----------

    public SseEmitter conectarAgente(String nombre, Set<String> impresoras) {
        // Sin timeout: la conexión dura lo que dure el agente prendido. Un corte se detecta al
        // fallar el envío del heartbeat.
        SseEmitter emitter = new SseEmitter(0L);
        AgenteConectado agente = new AgenteConectado(nombre, Set.copyOf(impresoras), emitter, new ConcurrentHashMap<>(),
                new AtomicLong(System.currentTimeMillis()));
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
            if (!vivo(a)) continue;
            for (String impresora : a.impresoras()) {
                EstadoImpresora estado = a.estados().get(impresora);
                salida.add(new ImpresoraConectada(impresora, a.nombre(),
                        estado == null || estado.disponible(), estado == null ? null : estado.detalle()));
            }
        }
        salida.sort(Comparator.comparing(ImpresoraConectada::nombre));
        return salida;
    }

    /** Lo que informa el agente cada vez que cambia el estado de sus ticketeras. */
    public void actualizarEstados(String nombreAgente, Map<String, EstadoImpresora> estados) {
        AgenteConectado agente = agentes.get(nombreAgente);
        if (agente == null) return; // todavía no conectó (o ya se fue): lo reenvía al conectar
        agente.tocar();
        estados.forEach((impresora, estado) -> {
            if (!agente.impresoras().contains(impresora)) return;
            EstadoImpresora antes = agente.estados().put(impresora, estado);
            if (antes == null || antes.disponible() != estado.disponible()) {
                log.info("Ticketera '{}' del agente '{}': {}", impresora, nombreAgente,
                        estado.disponible() ? "lista" : estado.detalle());
            }
        });
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

    /**
     * La factura se anuló (venta cancelada o corregida): sus trabajos sin imprimir no tienen que
     * salir. Si el agente estaba apagado y reconecta después, el reenvío ya no los encuentra.
     * Corre en la transacción de la cancelación.
     */
    public void cancelarTrabajos(Long facturaId) {
        int n = trabajoRepository.cancelarDeFactura(facturaId, "Cancelado: la factura se anuló",
                LocalDateTime.now(), EstadoTrabajoImpresion.ERROR, SIN_CONFIRMAR);
        if (n > 0) log.info("Factura ID {} anulada: {} trabajo(s) de impresión cancelado(s)", facturaId, n);
    }

    public Optional<TrabajoImpresion> ultimoTrabajo(Long facturaId) {
        return trabajoRepository.findFirstByFacturaIdOrderByIdDesc(facturaId);
    }

    /** El último trabajo de cada factura, en una sola consulta. Facturas sin trabajo no aparecen. */
    public Map<Long, TrabajoImpresion> ultimosTrabajos(Collection<Long> facturaIds) {
        Map<Long, TrabajoImpresion> ultimos = new HashMap<>();
        if (facturaIds.isEmpty()) return ultimos;
        for (TrabajoImpresion t : trabajoRepository.findByFacturaIdIn(facturaIds)) {
            ultimos.merge(t.getFactura().getId(), t, (a, b) -> a.getId() > b.getId() ? a : b);
        }
        return ultimos;
    }

    public void registrarResultado(Long trabajoId, boolean ok, String error) {
        // Un IMPRESO no se pisa: puede llegar una confirmación repetida de un reenvío.
        trabajoRepository.registrarResultado(trabajoId,
                ok ? EstadoTrabajoImpresion.IMPRESO : EstadoTrabajoImpresion.ERROR,
                ok ? null : recortar(error), LocalDateTime.now(), EstadoTrabajoImpresion.IMPRESO);
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
        // Por si la anulación llegó entre la consulta y el envío (o el trabajo es anterior a ella).
        Factura factura = facturaRepository.findById(trabajo.getFactura().getId()).orElse(null);
        if (factura == null || Boolean.TRUE.equals(factura.getAnulacionPedida())
                || factura.getEstado() == org.example.laserranitaentradas.model.entity.EstadoFactura.ANULADA) {
            registrarResultado(trabajoId, false, "Cancelado: la factura se anuló");
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

        // Se marca ENVIADO ANTES de mandarlo: el agente puede confirmar en milisegundos, y si el
        // ENVIADO se escribiera después pisaría ese IMPRESO. 0 filas = ya tenía resultado.
        if (trabajoRepository.marcarEnviado(trabajoId, impresora, LocalDateTime.now(),
                EstadoTrabajoImpresion.ENVIADO, SIN_CONFIRMAR) == 0) {
            return;
        }
        // Formato del evento: "id;impresora;ticket en base64". Sin JSON a propósito: así el
        // agente no necesita ninguna librería. Los nombres de impresora no llevan ';' (se valida
        // al conectar).
        String datos = trabajoId + ";" + impresora + ";" + Base64.getEncoder().encodeToString(ticket);
        if (!enviar(agente, "trabajo", datos)) {
            trabajoRepository.volverAPendiente(trabajoId, EstadoTrabajoImpresion.PENDIENTE, EstadoTrabajoImpresion.ENVIADO);
        }
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
            if (!vivo(a)) continue;
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
            if (!vivo(a) && agentes.remove(a.nombre(), a)) {
                log.info("Agente de impresión '{}' sin noticias hace más de {} s: desconectado", a.nombre(), segundosSinNoticias);
                a.emitter().complete();
            }
        }
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
        trabajoRepository.vencer(LocalDateTime.now().minusMinutes(minutosVencimiento),
                "No se pudo imprimir a tiempo (impresora desconectada). Reimprimilo si hace falta.",
                LocalDateTime.now(), EstadoTrabajoImpresion.ERROR, SIN_CONFIRMAR);
    }

    private static String recortar(String texto) {
        if (texto == null) return null;
        return texto.length() > 500 ? texto.substring(0, 500) : texto;
    }
}
