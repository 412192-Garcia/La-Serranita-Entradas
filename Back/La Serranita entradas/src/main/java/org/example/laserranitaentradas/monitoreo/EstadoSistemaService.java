package org.example.laserranitaentradas.monitoreo;

import org.example.laserranitaentradas.model.dto.ControlFacturacionDTO;
import org.example.laserranitaentradas.model.dto.EstadoSistemaDTO;
import org.example.laserranitaentradas.model.dto.EstadoSistemaDTO.Tarjeta;
import org.example.laserranitaentradas.model.entity.Caja;
import org.example.laserranitaentradas.model.entity.EstadoCompra;
import org.example.laserranitaentradas.model.entity.EstadoTrabajoImpresion;
import org.example.laserranitaentradas.model.entity.FormaPago;
import org.example.laserranitaentradas.model.entity.Incidente;
import org.example.laserranitaentradas.model.entity.TerminalPos;
import org.example.laserranitaentradas.repository.CajaRepository;
import org.example.laserranitaentradas.repository.CompraRepository;
import org.example.laserranitaentradas.repository.FacturaRepository;
import org.example.laserranitaentradas.repository.TrabajoImpresionRepository;
import org.example.laserranitaentradas.service.CajaService;
import org.example.laserranitaentradas.service.FacturaService;
import org.example.laserranitaentradas.service.impresion.ImpresionService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Sistema > Estado: arma una tarjeta por área con lo que puede estar fallando en silencio. Cada
 * tarjeta en ALERTA prende la campanita (ver idsAlertas). Ninguna habla con servicios externos
 * (ARCA, Mercado Pago): se puede consultar seguido sin costo.
 */
@Service
public class EstadoSistemaService {

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final List<EstadoCompra> PAGADAS = List.of(EstadoCompra.APROBADO, EstadoCompra.USADO);
    /** Resumen de una tarjeta que no se pudo armar (AlertasService no la toma ni como alerta ni como resuelta). */
    static final String NO_SE_PUDO_CONSULTAR = "No se pudo consultar";

    private final FacturaService facturaService;
    private final FacturaRepository facturaRepository;
    private final CompraRepository compraRepository;
    private final CajaRepository cajaRepository;
    private final CajaService cajaService;
    private final TrabajoImpresionRepository trabajoRepository;
    private final ImpresionService impresionService;
    private final IncidenteService incidenteService;
    private final AuditoriaService auditoriaService;
    private final EstadoMails estadoMails;
    private final JdbcTemplate jdbc;
    private final ObjectProvider<BuildProperties> build;
    private final EstadoTareas estadoTareas;
    private final TerminalesService terminalesService;
    private final MetricasRequests metricasRequests;
    private final AnomaliasVentas anomaliasVentas;
    private final PedidoBackup pedidoBackup;

    @Value("${app.backups.dir:/backups}")
    private String carpetaBackups;
    @Value("${app.monitoreo.dias-sin-backup:8}")
    private int diasSinBackup;
    @Value("${app.backups.drive-remoto:}")
    private String driveRemoto;
    @Value("${app.monitoreo.diferencia-caja:10000}")
    private BigDecimal umbralDiferenciaCaja;
    @Value("${compras.checkout-abandonado.horas:3}")
    private int horasCheckoutAbandonado;
    @Value("${app.monitoreo.logins-fallidos:5}")
    private int loginsFallidosPorUsuario;
    @Value("${app.monitoreo.lentas-por-hora:20}")
    private long lentasPorHora;
    @Value("${app.alertas.mail:}")
    private String alertasMail;
    @Value("${app.alertas.latido-url:}")
    private String latidoUrl;

    public EstadoSistemaService(FacturaService facturaService, FacturaRepository facturaRepository, CompraRepository compraRepository,
                                CajaRepository cajaRepository, CajaService cajaService, TrabajoImpresionRepository trabajoRepository,
                                ImpresionService impresionService, IncidenteService incidenteService,
                                AuditoriaService auditoriaService, EstadoMails estadoMails, JdbcTemplate jdbc,
                                ObjectProvider<BuildProperties> build, EstadoTareas estadoTareas,
                                TerminalesService terminalesService, MetricasRequests metricasRequests,
                                AnomaliasVentas anomaliasVentas, PedidoBackup pedidoBackup) {
        this.facturaService = facturaService;
        this.facturaRepository = facturaRepository;
        this.compraRepository = compraRepository;
        this.cajaRepository = cajaRepository;
        this.cajaService = cajaService;
        this.trabajoRepository = trabajoRepository;
        this.impresionService = impresionService;
        this.incidenteService = incidenteService;
        this.auditoriaService = auditoriaService;
        this.estadoMails = estadoMails;
        this.jdbc = jdbc;
        this.build = build;
        this.estadoTareas = estadoTareas;
        this.terminalesService = terminalesService;
        this.metricasRequests = metricasRequests;
        this.anomaliasVentas = anomaliasVentas;
        this.pedidoBackup = pedidoBackup;
    }

    @Transactional(readOnly = true)
    public EstadoSistemaDTO estado() {
        List<Tarjeta> tarjetas = new ArrayList<>();
        tarjetas.add(seguro("facturacion", "Facturación", this::facturacion));
        tarjetas.add(seguro("pagos", "Pagos online", this::pagos));
        tarjetas.add(seguro("ventas", "Ventas", anomaliasVentas::tarjeta));
        tarjetas.add(seguro("backups", "Backups", this::backups));
        tarjetas.add(seguro("mails", "Mails", this::mails));
        tarjetas.add(seguro("impresion", "Impresión", this::impresion));
        tarjetas.add(seguro("cajas", "Cajas", this::cajas));
        tarjetas.add(seguro("seguridad", "Seguridad", this::seguridad));
        tarjetas.add(seguro("errores", "Errores del sistema", this::errores));
        tarjetas.add(seguro("terminales", "Terminales del POS", this::terminales));
        tarjetas.add(seguro("tareas", "Tareas programadas", this::tareas));
        tarjetas.add(seguro("rendimiento", "Rendimiento", this::rendimiento));
        tarjetas.add(seguro("servidor", "Servidor", this::servidor));
        return new EstadoSistemaDTO(LocalDateTime.now(AuditoriaService.ZONA).withNano(0), tarjetas);
    }

    /** Para la campanita: una entrada por tarjeta en ALERTA. */
    public List<Long> idsAlertas() {
        List<Tarjeta> tarjetas = estado().tarjetas();
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < tarjetas.size(); i++) {
            if ("ALERTA".equals(tarjetas.get(i).estado())) ids.add((long) i + 1);
        }
        return ids;
    }

    /** Una tarjeta que no se puede armar (lo que sea que falle) no tumba las demás. */
    private Tarjeta seguro(String clave, String titulo, java.util.function.Supplier<Tarjeta> armar) {
        try {
            return armar.get();
        } catch (RuntimeException e) {
            return new Tarjeta(clave, titulo, "INFO", NO_SE_PUDO_CONSULTAR, List.of(String.valueOf(e.getMessage())), null);
        }
    }

    // ---------- tarjetas ----------

    private Tarjeta facturacion() {
        if (!facturaService.estaHabilitada()) {
            return new Tarjeta("facturacion", "Facturación", "INFO", "Apagada", List.of("Sin configurar en el servidor: se vende sin factura."), null);
        }
        ControlFacturacionDTO c = facturaService.controlFacturacion();
        List<String> detalles = new ArrayList<>();
        boolean alerta = false;
        if (!c.problemas().isEmpty()) {
            alerta = true;
            detalles.add(c.problemas().size() + " factura(s) con problema");
        }
        if (c.certificadoVence() != null) {
            boolean porVencer = c.diasParaVencer() != null && c.diasParaVencer() <= 30;
            alerta |= porVencer;
            detalles.add((porVencer ? "Certificado por vencer: " : "Certificado vigente hasta el ") + c.certificadoVence().format(FECHA)
                    + " (" + c.diasParaVencer() + " días)");
        } else {
            detalles.add("Homologación (pruebas), sin certificado");
        }
        if (c.numeracionControlada()) {
            if (!c.desfases().isEmpty()) {
                alerta = true;
                detalles.add("La numeración no coincide con ARCA");
            } else if (c.numeracionControladaEn() != null) {
                detalles.add("Numeración controlada el " + c.numeracionControladaEn().format(FECHA_HORA) + ": coincide");
            }
        }
        LocalDate hoy = LocalDate.now(AuditoriaService.ZONA);
        detalles.add("Comprobantes emitidos este mes: " + facturaRepository.countByNumeroIsNotNullAndFechaEmisionBetween(hoy.withDayOfMonth(1), hoy));
        return new Tarjeta("facturacion", "Facturación", alerta ? "ALERTA" : "OK",
                alerta ? "Hay algo para revisar" : "Funcionando", detalles, "/acciones");
    }

    private Tarjeta pagos() {
        List<String> detalles = new ArrayList<>();
        long incidentes = incidenteService.pendientes("PAGOS");
        for (Incidente i : incidenteService.ultimosPendientes("PAGOS")) detalles.add(i.getMensaje());
        LocalDateTime limite = LocalDateTime.now(AuditoriaService.ZONA).minusHours(horasCheckoutAbandonado + 1L);
        long trabadas = compraRepository.countByEstadoAndFechaCreacionBefore(EstadoCompra.PENDIENTE_PAGO, limite);
        if (trabadas > 0) {
            detalles.add(trabadas + " compra(s) siguen \"pago pendiente\" hace más de " + (horasCheckoutAbandonado + 1)
                    + " h (el barrido de checkouts abandonados no está corriendo o el webhook no llega)");
        }
        LocalDateTime ultima = compraRepository.ultimaCompraPagada(FormaPago.MERCADO_PAGO, PAGADAS);
        detalles.add(ultima != null ? "Último pago online: " + ultima.format(FECHA_HORA) : "Todavía no hay pagos online");
        boolean alerta = incidentes > 0 || trabadas > 0;
        return new Tarjeta("pagos", "Pagos online", alerta ? "ALERTA" : "OK",
                incidentes > 0 ? incidentes + " aviso(s) de Mercado Pago para revisar" : trabadas > 0 ? "Pagos pendientes trabados" : "Sin problemas",
                detalles, incidentes > 0 ? "/sistema?tab=errores&area=PAGOS" : null);
    }

    private Tarjeta backups() {
        File carpeta = new File(carpetaBackups);
        if (!carpeta.isDirectory()) {
            return new Tarjeta("backups", "Backups", "INFO", "No disponible",
                    List.of("El backend no ve la carpeta de backups (" + carpetaBackups + "): falta montarla en docker-compose."), null);
        }
        File[] archivos = carpeta.listFiles((dir, nombre) -> nombre.startsWith("serranita-") && nombre.endsWith(".dump"));
        if (archivos == null || archivos.length == 0) {
            List<String> detalles = new ArrayList<>(List.of("El servicio de backup no generó ningún archivo. Revisar el contenedor \"backup\"."));
            return new Tarjeta("backups", "Backups", "ALERTA", "Ningún backup todavía", detalles, null, accionBackup(detalles));
        }
        File ultimo = Arrays.stream(archivos).max(Comparator.comparingLong(File::lastModified)).orElseThrow();
        LocalDateTime cuando = LocalDateTime.ofInstant(Instant.ofEpochMilli(ultimo.lastModified()), AuditoriaService.ZONA);
        long dias = Duration.between(cuando, LocalDateTime.now(AuditoriaService.ZONA)).toDays();
        long total = Arrays.stream(archivos).mapToLong(File::length).sum();
        boolean viejo = dias > diasSinBackup;
        List<String> detalles = new ArrayList<>(List.of("Último: " + cuando.format(FECHA_HORA) + " (" + tamanio(ultimo.length()) + ")",
                archivos.length + " guardado(s), " + tamanio(total) + " en total"));
        String problemaDrive = copiaDrive(carpeta, detalles);
        String accion = accionBackup(detalles);
        String resumen = viejo ? "El último backup tiene " + dias + " días" : problemaDrive != null ? problemaDrive : "Al día";
        return new Tarjeta("backups", "Backups", viejo || problemaDrive != null ? "ALERTA" : "OK", resumen, detalles, null, accion);
    }

    /**
     * Estado del "hacer backup ahora" (ver PedidoBackup) en los detalles, y el botón si se puede pedir
     * uno: no se ofrece mientras hay otro en marcha ni si el servidor no tiene la carpeta de pedidos.
     */
    private String accionBackup(List<String> detalles) {
        PedidoBackup.Estado p = pedidoBackup.estado();
        switch (p.situacion()) {
            case PEDIDO -> detalles.add(0, "Backup manual pedido" + (p.pedidoEn() != null ? " a las " + p.pedidoEn().format(HORA) : "")
                    + ": arranca en menos de un minuto");
            case EN_CURSO -> detalles.add(0, "Backup manual en curso...");
            default -> { }
        }
        if (p.ultimoResultado() != null && p.ultimoEn() != null) {
            detalles.add("Último backup manual: " + p.ultimoEn().format(FECHA_HORA)
                    + ("OK".equals(p.ultimoResultado()) ? " (bien)" : " (FALLÓ" + (p.ultimoDetalle() != null ? ": " + p.ultimoDetalle() : "") + ")"));
        }
        return p.situacion() == PedidoBackup.Situacion.LIBRE ? "forzar-backup" : null;
    }

    /**
     * La copia en Google Drive la hace el contenedor "backup" (rclone) y deja el resultado en
     * .drive-estado: "OK <fecha> <archivo>" o "ERROR <fecha> <motivo>". Devuelve el problema, o null.
     */
    private String copiaDrive(File carpeta, List<String> detalles) {
        if (driveRemoto.isBlank()) {
            detalles.add("Sin copia fuera del servidor (falta BACKUP_DRIVE_REMOTO): si se pierde el servidor, se pierden los backups");
            return null;
        }
        File estado = new File(carpeta, ".drive-estado");
        if (!estado.isFile()) {
            detalles.add("Google Drive: todavía no se subió ningún backup");
            return "Sin copia en Google Drive";
        }
        try {
            String[] partes = java.nio.file.Files.readString(estado.toPath()).trim().split(" ", 3);
            LocalDateTime cuando = LocalDateTime.parse(partes[1]);
            long dias = Duration.between(cuando, LocalDateTime.now(AuditoriaService.ZONA)).toDays();
            if ("OK".equals(partes[0])) {
                detalles.add("Copia en Google Drive (" + driveRemoto + "): " + cuando.format(FECHA_HORA));
                return dias > diasSinBackup ? "La copia en Google Drive tiene " + dias + " días" : null;
            }
            detalles.add("Google Drive, falló el " + cuando.format(FECHA_HORA) + ": " + (partes.length > 2 ? partes[2] : "sin detalle"));
            return "Falló la copia a Google Drive";
        } catch (Exception e) {
            detalles.add("No se pudo leer el estado de la copia en Google Drive: " + e.getMessage());
            return "Copia en Google Drive desconocida";
        }
    }

    private Tarjeta mails() {
        List<String> detalles = new ArrayList<>();
        LocalDateTime ok = estadoMails.ultimoOk();
        EstadoMails.Fallo fallo = estadoMails.ultimoFallo();
        detalles.add(ok != null ? "Último mail enviado: " + ok.format(FECHA_HORA) : "No se envió ningún mail desde que arrancó el servidor");
        boolean falloReciente = fallo != null && (ok == null || fallo.momento().isAfter(ok));
        if (fallo != null) detalles.add("Último fallo: " + fallo.momento().format(FECHA_HORA) + " (" + fallo.motivo() + ")");
        long incidentes = incidenteService.pendientes("MAILS");
        if (incidentes > 0) detalles.add(incidentes + " error(es) de mails sin revisar");
        boolean alerta = falloReciente || incidentes > 0;
        return new Tarjeta("mails", "Mails", alerta ? "ALERTA" : "OK",
                falloReciente ? "El último envío falló" : incidentes > 0 ? "Hubo errores al mandar mails" : "Funcionando",
                detalles, alerta ? "/acciones" : null);
    }

    private Tarjeta impresion() {
        List<ImpresionService.ImpresoraConectada> conectadas = impresionService.impresorasConectadas();
        List<String> detalles = new ArrayList<>();
        if (conectadas.isEmpty()) {
            detalles.add("Ninguna PC con agente conectada (normal con el parque cerrado)");
        } else {
            for (ImpresionService.ImpresoraConectada i : conectadas) {
                detalles.add(i.nombre() + " (" + i.agente() + "): " + (i.disponible() ? "lista" : (i.detalle() != null ? i.detalle() : "no disponible")));
            }
        }
        LocalDateTime inicioDia = LocalDate.now(AuditoriaService.ZONA).atStartOfDay();
        long fallidos = trabajoRepository.countByEstadoAndFechaCreacionAfter(EstadoTrabajoImpresion.ERROR, inicioDia);
        if (fallidos > 0) detalles.add(fallidos + " ticket(s) que no se pudieron imprimir hoy");
        String resumen = fallidos > 0 ? fallidos + " ticket(s) sin imprimir hoy"
                : conectadas.isEmpty() ? "Sin ticketeras conectadas" : conectadas.size() + " ticketera(s) conectada(s)";
        return new Tarjeta("impresion", "Impresión", fallidos > 0 ? "ALERTA" : (conectadas.isEmpty() ? "INFO" : "OK"), resumen, detalles, null);
    }

    private Tarjeta cajas() {
        List<String> detalles = new ArrayList<>();
        List<Caja> conDiferencia = cajaRepository.cerradasConDiferencia(LocalDateTime.now(AuditoriaService.ZONA).minusDays(7), umbralDiferenciaCaja);
        for (Caja c : conDiferencia) {
            BigDecimal d = c.getDiferencia();
            detalles.add("Caja de " + c.getUsuario().getNombre() + " del " + c.getFechaApertura().format(FECHA) + ": "
                    + (d.signum() < 0 ? "faltan " : "sobran ") + pesos(d.abs()));
        }
        int atrasadas = cajaService.getIdsCajasAtrasadas().size();
        if (atrasadas > 0) detalles.add(atrasadas + " caja(s) de días anteriores sin cerrar");
        boolean alerta = !conDiferencia.isEmpty() || atrasadas > 0;
        if (!alerta) detalles.add("Sin cierres con diferencias de " + pesos(umbralDiferenciaCaja) + " o más en la última semana");
        return new Tarjeta("cajas", "Cajas", alerta ? "ALERTA" : "OK",
                !conDiferencia.isEmpty() ? conDiferencia.size() + " cierre(s) con diferencia grande" : atrasadas > 0 ? "Cajas sin cerrar" : "Sin novedades",
                detalles, "/cajas");
    }

    private Tarjeta seguridad() {
        List<Object[]> fallidos = auditoriaService.loginsFallidos(24);
        List<String> detalles = new ArrayList<>();
        long total = 0;
        boolean alerta = false;
        for (Object[] f : fallidos) {
            long cantidad = ((Number) f[1]).longValue();
            total += cantidad;
            if (cantidad >= loginsFallidosPorUsuario) {
                alerta = true;
                detalles.add(cantidad + " intentos fallidos con el usuario \"" + f[0] + "\"");
            }
        }
        if (!alerta) detalles.add(total == 0 ? "Ningún intento de ingreso fallido en las últimas 24 h" : total + " intento(s) fallido(s) en las últimas 24 h");
        return new Tarjeta("seguridad", "Seguridad", alerta ? "ALERTA" : "OK",
                alerta ? "Intentos de ingreso repetidos" : "Sin novedades", detalles, "/sistema?tab=historial&accion=LOGIN_FALLIDO");
    }

    private Tarjeta errores() {
        long pendientes = incidenteService.pendientes();
        List<String> detalles = new ArrayList<>();
        for (Incidente i : incidenteService.ultimosPendientes(null)) {
            detalles.add(i.getUltimaVez().format(FECHA_HORA) + " · " + i.getMensaje());
        }
        if (pendientes == 0) detalles.add("Ningún error sin revisar");
        return new Tarjeta("errores", "Errores del sistema", pendientes > 0 ? "ALERTA" : "OK",
                pendientes > 0 ? pendientes + " error(es) sin revisar" : "Sin errores", detalles, "/sistema?tab=errores");
    }

    private Tarjeta terminales() {
        List<TerminalPos> terminales = terminalesService.recientes();
        List<String> detalles = new ArrayList<>();
        int problemas = 0;
        LocalDateTime ahora = LocalDateTime.now(AuditoriaService.ZONA);
        for (TerminalPos t : terminales) {
            TerminalesService.Problema p = terminalesService.problema(t);
            if (p != null) problemas++;
            String estado = p != null ? p.descripcion()
                    : "última señal hace " + TerminalesService.duracion(Duration.between(t.getUltimaVez(), ahora))
                    + (t.getPendientes() > 0 ? ", " + t.getPendientes() + " sincronizando" : ", todo subido");
            detalles.add(TerminalesService.nombre(t) + ": " + estado
                    + (t.getConError() > 0 ? " · " + t.getConError() + " rechazada(s) por revisar" : ""));
        }
        if (terminales.isEmpty()) detalles.add("Ninguna terminal usó el POS en las últimas 24 h");
        return new Tarjeta("terminales", "Terminales del POS", problemas > 0 ? "ALERTA" : (terminales.isEmpty() ? "INFO" : "OK"),
                problemas > 0 ? problemas + " terminal(es) con ventas sin subir" : terminales.isEmpty() ? "Sin actividad" : "Todo sincronizado",
                detalles, null);
    }

    private Tarjeta tareas() {
        List<EstadoTareas.Tarea> tareas = estadoTareas.tareas();
        List<String> detalles = new ArrayList<>();
        long problemas = 0;
        for (EstadoTareas.Tarea t : tareas) {
            if (!t.enProblemas()) continue;
            problemas++;
            String que = switch (t.situacion()) {
                case TRABADA -> "trabada: lleva corriendo desde las " + t.ultima().format(FECHA_HORA);
                case ATRASADA -> "no arrancó: le tocaba a las " + t.proxima().format(FECHA_HORA);
                default -> "la última pasada falló" + (t.error() != null ? " (" + t.error() + ")" : "");
            };
            detalles.add(t.nombre() + ": " + que);
        }
        for (EstadoTareas.Tarea t : tareas) {
            if (t.enProblemas()) continue;
            detalles.add(t.nombre() + ": " + (t.ultima() != null ? "última " + t.ultima().format(FECHA_HORA) : "todavía no corrió")
                    + (t.proxima() != null ? " · próxima " + t.proxima().format(FECHA_HORA) : ""));
        }
        return new Tarjeta("tareas", "Tareas programadas", problemas > 0 ? "ALERTA" : "OK",
                problemas > 0 ? problemas + " tarea(s) con problemas" : tareas.size() + " tareas funcionando", detalles, null);
    }

    private Tarjeta rendimiento() {
        MetricasRequests.Resumen r = metricasRequests.resumen();
        long lentasHora = metricasRequests.lentasUltimaHora();
        List<String> detalles = new ArrayList<>();
        String umbral = r.umbralMs() % 1000 == 0 ? r.umbralMs() / 1000 + " s" : r.umbralMs() + " ms";
        detalles.add(lentasHora + " request(s) de " + umbral + " o más en la última hora");
        long total = r.endpoints().stream().mapToLong(MetricasRequests.Endpoint::cantidad).sum();
        detalles.add(total + " requests desde el " + r.desde().format(FECHA_HORA));
        // Sólo los que de verdad tardan: listar "el más lento" cuando tarda 2 ms confunde más de lo que ayuda.
        r.endpoints().stream().filter(e -> e.cantidad() >= 5 && e.promedioMs() * 2 >= r.umbralMs()).limit(3)
                .forEach(e -> detalles.add("Lento en promedio: " + e.endpoint() + " (" + e.promedioMs() + " ms)"));
        boolean alerta = lentasHora >= lentasPorHora;
        return new Tarjeta("rendimiento", "Rendimiento", alerta ? "ALERTA" : "OK",
                alerta ? "La app está respondiendo lento" : "Respondiendo bien", detalles, "/sistema?tab=rendimiento");
    }

    private Tarjeta servidor() {
        List<String> detalles = new ArrayList<>();
        File disco = new File(new File(carpetaBackups).isDirectory() ? carpetaBackups : "/");
        long libre = disco.getUsableSpace();
        long total = disco.getTotalSpace();
        boolean poco = total > 0 && (libre < 2L * 1024 * 1024 * 1024 || libre * 10 < total);
        if (total > 0) detalles.add("Disco: " + tamanio(libre) + " libres de " + tamanio(total));
        try {
            Long base = jdbc.queryForObject("SELECT pg_database_size(current_database())", Long.class);
            if (base != null) detalles.add("Base de datos: " + tamanio(base));
        } catch (RuntimeException e) {
            // Otra base (tests con H2): el dato no aplica.
        }
        Runtime rt = Runtime.getRuntime();
        detalles.add("Memoria: " + tamanio(rt.totalMemory() - rt.freeMemory()) + " en uso de " + tamanio(rt.maxMemory()));
        Duration prendido = Duration.ofMillis(ManagementFactory.getRuntimeMXBean().getUptime());
        detalles.add("Prendido hace " + (prendido.toDays() > 0 ? prendido.toDays() + " d " : "") + prendido.toHoursPart() + " h " + prendido.toMinutesPart() + " min");
        detalles.add(alertasMail.isBlank() ? "Alertas por mail: apagadas (falta ALERTAS_MAIL)" : "Alertas por mail a " + alertasMail);
        detalles.add(latidoUrl.isBlank() ? "Monitor externo: sin latido (falta ALERTAS_LATIDO_URL)" : "Monitor externo: latido cada 5 min");
        BuildProperties b = build.getIfAvailable();
        if (b != null && b.getTime() != null) {
            detalles.add("Versión: " + b.getVersion() + " (compilada el " + LocalDateTime.ofInstant(b.getTime(), AuditoriaService.ZONA).format(FECHA_HORA) + ")");
        }
        return new Tarjeta("servidor", "Servidor", poco ? "ALERTA" : "OK", poco ? "Queda poco espacio en disco" : "Funcionando", detalles, null);
    }

    private static String tamanio(long bytes) {
        if (bytes >= 1024L * 1024 * 1024) return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
        if (bytes >= 1024L * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%d KB", Math.max(1, bytes / 1024));
    }

    private static String pesos(BigDecimal monto) {
        return "$" + String.format("%,.0f", monto).replace(',', '.');
    }
}
