package org.example.laserranitaentradas.service.impl;

import org.example.laserranitaentradas.model.dto.ComprasSinCompletarDTO;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.CompraDetalle;
import org.example.laserranitaentradas.model.entity.EstadoCompra;
import org.example.laserranitaentradas.model.entity.EventoPago;
import org.example.laserranitaentradas.model.entity.FormaPago;
import org.example.laserranitaentradas.model.entity.MotivoCancelacion;
import org.example.laserranitaentradas.model.entity.Tipo;
import org.example.laserranitaentradas.repository.CompraRepository;
import org.example.laserranitaentradas.repository.EventoPagoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/** El embudo de las compras online: cuántas se empiezan, cuántas se pagan y por qué no se pagan las demás. */
@Service
public class ComprasSinCompletarService {

    /** Llegaron a pagarse (aunque después se hayan usado o reembolsado). */
    private static final Set<EstadoCompra> PAGADAS = EnumSet.of(EstadoCompra.APROBADO, EstadoCompra.USADO, EstadoCompra.REEMBOLSADA);
    /** Cuántos intentos se listan como máximo, los más recientes. */
    private static final int MAX_INTENTOS = 300;
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final String SIN_DATOS = "Sin datos (antes de que se registrara)";

    private final CompraRepository compraRepository;
    private final EventoPagoRepository eventoPagoRepository;

    public ComprasSinCompletarService(CompraRepository compraRepository, EventoPagoRepository eventoPagoRepository) {
        this.compraRepository = compraRepository;
        this.eventoPagoRepository = eventoPagoRepository;
    }

    @Transactional(readOnly = true)
    public ComprasSinCompletarDTO generar(LocalDate desde, LocalDate hasta) {
        LocalDateTime inicio = desde.atStartOfDay();
        LocalDateTime fin = hasta.plusDays(1).atStartOfDay();
        List<Compra> delRango = compraRepository.findOnlineCreadasEntre(FormaPago.MERCADO_PAGO, inicio, fin);
        // Para saber si quien no completó compró después (también pasado el rango).
        List<Compra> pagadasDespues = compraRepository.findOnlineCreadasEntre(FormaPago.MERCADO_PAGO, inicio, LocalDateTime.now().plusDays(1))
                .stream().filter(c -> PAGADAS.contains(c.getEstado())).toList();
        Map<Long, List<EventoPago>> eventos = new HashMap<>();
        List<Long> sinPagar = delRango.stream().filter(c -> !PAGADAS.contains(c.getEstado())).map(Compra::getId).toList();
        if (!sinPagar.isEmpty()) {
            for (EventoPago e : eventoPagoRepository.findByCompraIdInOrderByFechaAsc(sinPagar)) {
                eventos.computeIfAbsent(e.getCompra().getId(), k -> new ArrayList<>()).add(e);
            }
        }

        long pagadas = 0, abandonadas = 0, rechazadas = 0, noIniciadas = 0, pendientes = 0, otras = 0, noRecuperadas = 0;
        BigDecimal montoNoRecuperado = BigDecimal.ZERO;
        Map<String, Long> motivos = new LinkedHashMap<>();
        Map<String, Long> pasos = new LinkedHashMap<>();
        Map<LocalDate, long[]> porDia = new TreeMap<>();
        Map<String, long[]> porDispositivo = new TreeMap<>();
        Map<String, long[]> porOrigen = new LinkedHashMap<>();
        List<Compra> listaPagadas = new ArrayList<>();
        List<Compra> listaSinCompletar = new ArrayList<>();
        List<ComprasSinCompletarDTO.Intento> intentos = new ArrayList<>();

        for (Compra c : delRango) {
            boolean pagada = PAGADAS.contains(c.getEstado());
            sumar(porDia.computeIfAbsent(c.getFechaCreacion().toLocalDate(), d -> new long[2]), pagada);
            sumar(porDispositivo.computeIfAbsent(c.getDispositivo() == null ? SIN_DATOS : c.getDispositivo(), d -> new long[2]), pagada);
            sumar(porOrigen.computeIfAbsent(origen(c), d -> new long[2]), pagada);
            if (pagada) {
                pagadas++;
                listaPagadas.add(c);
                continue;
            }
            listaSinCompletar.add(c);
            List<EventoPago> deEsta = eventos.getOrDefault(c.getId(), List.of());
            if (c.getEstado() == EstadoCompra.PENDIENTE_PAGO) {
                pendientes++;
            } else if (MotivoCancelacion.CHECKOUT_ABANDONADO.name().equals(c.getMotivoCancelacion())) {
                abandonadas++;
                pasos.merge(hastaDondeLlego(c, deEsta), 1L, Long::sum);
            } else if (MotivoCancelacion.PAGO_RECHAZADO.name().equals(c.getMotivoCancelacion())) {
                rechazadas++;
                for (String parte : partesDetalle(c.getDetalleCancelacion())) motivos.merge(parte, 1L, Long::sum);
            } else if (MotivoCancelacion.PAGO_NO_INICIADO.name().equals(c.getMotivoCancelacion())) {
                noIniciadas++;
            } else {
                otras++;
            }
            Compra posterior = compraPosterior(c, pagadasDespues);
            if (posterior == null && c.getEstado() != EstadoCompra.PENDIENTE_PAGO) {
                noRecuperadas++;
                montoNoRecuperado = montoNoRecuperado.add(c.getMontoTotal());
            }
            intentos.add(new ComprasSinCompletarDTO.Intento(c.getId(), c.getCodigoReserva(), c.getFechaCreacion(),
                    c.getFechaVisita(), c.getMontoTotal(), entradas(c), c.getEstado(), c.getMotivoCancelacion(),
                    c.getDetalleCancelacion(), nombre(c), c.getContactEmail(), c.getContactPhone(),
                    c.getDispositivo(), origen(c),
                    deEsta.stream().map(e -> e.getFecha().format(HORA) + " " + SeguimientoPagoService.PASOS.get(e.getTipo())).toList(),
                    posterior == null ? null : posterior.getCodigoReserva()));
        }

        intentos.sort(Comparator.comparing(ComprasSinCompletarDTO.Intento::fechaCreacion).reversed());
        return new ComprasSinCompletarDTO(delRango.size(), pagadas, abandonadas, rechazadas, noIniciadas, pendientes,
                otras, noRecuperadas, montoNoRecuperado,
                ordenados(pasos), ordenados(motivos),
                grupos(porDispositivo), grupos(porOrigen),
                perfil(listaPagadas), perfil(listaSinCompletar),
                porDia.entrySet().stream()
                        .map(e -> new ComprasSinCompletarDTO.Dia(e.getKey(), e.getValue()[0], e.getValue()[1]))
                        .toList(),
                intentos.size() > MAX_INTENTOS ? intentos.subList(0, MAX_INTENTOS) : intentos);
    }

    /** El paso más avanzado al que llegó una compra abandonada, según lo que avisó la página. */
    static String hastaDondeLlego(Compra c, List<EventoPago> eventos) {
        Set<String> tipos = new java.util.HashSet<>();
        eventos.forEach(e -> tipos.add(e.getTipo()));
        if (tipos.isEmpty()) return c.getDispositivo() == null ? SIN_DATOS : "Sin pasos registrados";
        if (tipos.contains("VOLVIO_SIN_PAGAR")) return "Volvió de Mercado Pago sin pagar";
        if (tipos.contains("VENTANA_CERRADA")) return "Abrió Mercado Pago y lo cerró sin pagar";
        if (tipos.contains("VENTANA_ABIERTA") || tipos.contains("ABRIO_TRAS_BLOQUEO")) {
            return tipos.contains("VENTANA_BLOQUEADA") ? "Se bloqueó, abrió Mercado Pago y no pagó"
                    : "Abrió Mercado Pago y no hizo nada más";
        }
        if (tipos.contains("VENTANA_BLOQUEADA") || tipos.contains("SIGUE_BLOQUEADA")) {
            return "El navegador bloqueó Mercado Pago y no lo pudo abrir";
        }
        return "Volvió atrás sin pagar";
    }

    private static String origen(Compra c) {
        if (c.getEnIframe() == null) return SIN_DATOS;
        return c.getEnIframe() ? "Desde el sitio del parque" : "Directo en la página de compra";
    }

    private static void sumar(long[] grupo, boolean pagada) {
        grupo[0]++;
        if (pagada) grupo[1]++;
    }

    private static List<ComprasSinCompletarDTO.Motivo> ordenados(Map<String, Long> mapa) {
        return mapa.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new ComprasSinCompletarDTO.Motivo(e.getKey(), e.getValue()))
                .toList();
    }

    private static List<ComprasSinCompletarDTO.Grupo> grupos(Map<String, long[]> mapa) {
        return mapa.entrySet().stream()
                .map(e -> new ComprasSinCompletarDTO.Grupo(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .sorted(Comparator.comparingLong(ComprasSinCompletarDTO.Grupo::iniciadas).reversed())
                .toList();
    }

    static ComprasSinCompletarDTO.Perfil perfil(List<Compra> compras) {
        if (compras.isEmpty()) return new ComprasSinCompletarDTO.Perfil(0, 0, BigDecimal.ZERO, 0, 0, 0);
        int n = compras.size();
        Function<java.util.function.ToDoubleFunction<Compra>, Double> promedio =
                f -> compras.stream().mapToDouble(f).average().orElse(0);
        BigDecimal monto = compras.stream().map(Compra::getMontoTotal).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(n), 0, RoundingMode.HALF_UP);
        List<Compra> conFecha = compras.stream().filter(c -> c.getFechaVisita() != null).toList();
        double anticipacion = conFecha.stream()
                .mapToLong(c -> ChronoUnit.DAYS.between(c.getFechaCreacion().toLocalDate(), c.getFechaVisita()))
                .average().orElse(0);
        return new ComprasSinCompletarDTO.Perfil(n, promedio.apply(c -> entradas(c)), monto, anticipacion,
                100.0 * compras.stream().filter(c -> c.getCupon() != null).count() / n,
                100.0 * compras.stream().filter(c -> c.getFechaVisita() == null).count() / n);
    }

    /** "visa: rechazado, fondos insuficientes · master: rechazado, ..." → un motivo por intento. */
    static List<String> partesDetalle(String detalle) {
        if (detalle == null || detalle.isBlank()) return List.of("Sin detalle de Mercado Pago");
        return java.util.Arrays.stream(detalle.split(" · ")).map(String::trim).filter(p -> !p.isEmpty()).toList();
    }

    /** Una compra pagada de la misma persona (email o DNI) hecha después de este intento. */
    static Compra compraPosterior(Compra intento, List<Compra> pagadas) {
        String email = normalizar(intento.getContactEmail());
        String dni = intento.getCliente() == null ? null : normalizar(intento.getCliente().getDni());
        if (email == null && dni == null) return null;
        return pagadas.stream()
                .filter(p -> p.getFechaCreacion().isAfter(intento.getFechaCreacion()))
                .filter(p -> (email != null && email.equals(normalizar(p.getContactEmail())))
                        || (dni != null && p.getCliente() != null && dni.equals(normalizar(p.getCliente().getDni()))))
                .min(Comparator.comparing(Compra::getFechaCreacion))
                .orElse(null);
    }

    private static String normalizar(String s) {
        return s == null || s.isBlank() ? null : s.trim().toLowerCase();
    }

    private static long entradas(Compra c) {
        if (c.getDetalles() == null) return 0;
        return c.getDetalles().stream()
                .filter(d -> d.getTipoEntrada() != null && d.getTipoEntrada().getTipo() == Tipo.ENTRADA)
                .mapToLong(CompraDetalle::getCantidad)
                .sum();
    }

    private static String nombre(Compra c) {
        if (c.getCliente() == null) return null;
        String n = ((c.getCliente().getNombre() == null ? "" : c.getCliente().getNombre()) + " "
                + (c.getCliente().getApellido() == null ? "" : c.getCliente().getApellido())).trim();
        return n.isEmpty() ? null : n;
    }
}
