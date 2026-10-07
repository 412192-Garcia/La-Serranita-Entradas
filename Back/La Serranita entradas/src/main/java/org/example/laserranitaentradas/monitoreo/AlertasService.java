package org.example.laserranitaentradas.monitoreo;

import jakarta.mail.internet.MimeMessage;
import org.example.laserranitaentradas.model.dto.EstadoSistemaDTO.Tarjeta;
import org.example.laserranitaentradas.model.entity.EstadoAlerta;
import org.example.laserranitaentradas.repository.EstadoAlertaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Avisos que llegan sin entrar a la app:
 *
 * - Mail a ALERTAS_MAIL cuando una tarjeta de Sistema > Estado se pone en rojo, cuando cambia lo que
 *   dice (ej. de 1 a 3 errores, como mucho una vez por hora), cuando vuelve a estar bien, y un
 *   recordatorio diario mientras siga en rojo. También cuando el servidor arranca (un reinicio que
 *   nadie hizo es una caída).
 * - Latido a ALERTAS_LATIDO_URL (healthchecks.io o similar) cada 5 minutos si el backend y la base
 *   andan: el servicio externo avisa cuando el latido deja de llegar. Es lo único que se entera
 *   si se cae el servidor entero, porque entonces esta app no puede mandar nada.
 *
 * Sin ALERTAS_MAIL / ALERTAS_LATIDO_URL cada parte queda apagada.
 */
@Service
public class AlertasService {

    private static final Logger log = LoggerFactory.getLogger(AlertasService.class);
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final EstadoSistemaService estadoSistemaService;
    private final EstadoAlertaRepository repository;
    private final JavaMailSender mailSender;
    private final EstadoMails estadoMails;
    /** Con timeout corto propio: el chequeo de salud no puede quedarse colgado esperando a la base. */
    private final JdbcTemplate jdbc;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Value("${app.alertas.mail:}")
    private String destinatarios;
    @Value("${app.alertas.latido-url:}")
    private String latidoUrl;
    @Value("${app.alertas.recordatorio-horas:24}")
    private long horasRecordatorio;
    @Value("${app.alertas.avisar-arranque:true}")
    private boolean avisarArranque;
    @Value("${spring.mail.username:}")
    private String remitente;
    @Value("${mercadopago.frontend-url:}")
    private String urlApp;

    public AlertasService(EstadoSistemaService estadoSistemaService, EstadoAlertaRepository repository,
                          JavaMailSender mailSender, EstadoMails estadoMails, DataSource dataSource) {
        this.estadoSistemaService = estadoSistemaService;
        this.repository = repository;
        this.mailSender = mailSender;
        this.estadoMails = estadoMails;
        this.jdbc = new JdbcTemplate(dataSource);
        this.jdbc.setQueryTimeout(5);
    }

    public boolean baseResponde() {
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    // ---------- latido externo ----------

    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    public void latidoExterno() {
        if (latidoUrl.isBlank()) return;
        // Sin base la app no sirve: mejor que el monitor externo lo note y avise.
        if (!baseResponde()) {
            log.error("La base de datos no responde: no se manda el latido al monitor externo");
            return;
        }
        try {
            HttpResponse<Void> r = http.send(HttpRequest.newBuilder(URI.create(latidoUrl)).timeout(Duration.ofSeconds(10)).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
            if (r.statusCode() >= 400) log.warn("El monitor externo respondió {} al latido", r.statusCode());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // Sin internet o el servicio caído: el monitor externo igual va a avisar por la falta de latido.
            log.warn("No se pudo mandar el latido al monitor externo: {}", e.getMessage());
        }
    }

    // ---------- mails ----------

    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        if (!avisarArranque || destinatarios.isBlank()) return;
        try {
            enviar("Servidor reiniciado", "El servidor de La Serranita arrancó el " + ahora().format(FECHA_HORA) + ".\n\n"
                    + "Si nadie desplegó una versión nueva ni lo reinició a mano, se cayó y Docker lo volvió a levantar: "
                    + "revisar Sistema > Errores y el log del contenedor (docker compose logs backend).\n" + pie());
        } catch (Exception e) {
            log.error("No se pudo mandar el mail de aviso de arranque a {}", destinatarios, e);
        }
    }

    @Scheduled(fixedDelay = 300_000, initialDelay = 120_000)
    public void revisar() {
        if (destinatarios.isBlank()) return;
        List<Tarjeta> tarjetas = estadoSistemaService.estado().tarjetas();
        Map<String, EstadoAlerta> anteriores = repository.findAll().stream()
                .collect(Collectors.toMap(EstadoAlerta::getClave, Function.identity()));
        LocalDateTime ahora = ahora();

        List<Tarjeta> nuevas = new ArrayList<>();
        List<Tarjeta> cambiaron = new ArrayList<>();
        List<Tarjeta> siguen = new ArrayList<>();
        List<Tarjeta> resueltas = new ArrayList<>();
        for (Tarjeta t : tarjetas) {
            // Si la tarjeta no se pudo armar no se sabe cómo está: ni alerta nueva ni "resuelta".
            if (EstadoSistemaService.NO_SE_PUDO_CONSULTAR.equals(t.resumen())) continue;
            EstadoAlerta antes = anteriores.get(t.clave());
            boolean antesEnAlerta = antes != null && antes.isEnAlerta();
            if ("ALERTA".equals(t.estado())) {
                if (!antesEnAlerta) nuevas.add(t);
                else if (!Objects.equals(antes.getResumen(), recortar(t.resumen())) && hace(antes.getUltimoAviso(), ahora, Duration.ofHours(1))) cambiaron.add(t);
                else if (hace(antes.getUltimoAviso(), ahora, Duration.ofHours(horasRecordatorio))) siguen.add(t);
            } else if (antesEnAlerta) {
                resueltas.add(t);
            }
        }
        if (nuevas.isEmpty() && cambiaron.isEmpty() && siguen.isEmpty() && resueltas.isEmpty()) return;

        try {
            enviar(asunto(nuevas, cambiaron, siguen, resueltas), cuerpo(nuevas, cambiaron, siguen, resueltas));
        } catch (Exception e) {
            // No se guarda el estado: en la próxima vuelta se vuelve a intentar con lo mismo.
            log.error("No se pudo mandar el mail de alertas del sistema a {}", destinatarios, e);
            return;
        }
        for (Tarjeta t : nuevas) guardar(anteriores.get(t.clave()), t, true, ahora, true);
        for (Tarjeta t : cambiaron) guardar(anteriores.get(t.clave()), t, true, ahora, false);
        for (Tarjeta t : siguen) guardar(anteriores.get(t.clave()), t, true, ahora, false);
        for (Tarjeta t : resueltas) guardar(anteriores.get(t.clave()), t, false, ahora, false);
    }

    private void guardar(EstadoAlerta e, Tarjeta t, boolean enAlerta, LocalDateTime ahora, boolean nueva) {
        if (e == null) {
            e = new EstadoAlerta();
            e.setClave(t.clave());
        }
        e.setEnAlerta(enAlerta);
        e.setResumen(recortar(t.resumen()));
        e.setUltimoAviso(ahora);
        if (nueva) e.setDesde(ahora);
        if (!enAlerta) e.setDesde(null);
        repository.save(e);
    }

    static String asunto(List<Tarjeta> nuevas, List<Tarjeta> cambiaron, List<Tarjeta> siguen, List<Tarjeta> resueltas) {
        List<Tarjeta> alertas = new ArrayList<>(nuevas);
        alertas.addAll(cambiaron);
        if (!alertas.isEmpty()) return "Alerta: " + titulos(alertas);
        if (!siguen.isEmpty()) return "Sigue en alerta: " + titulos(siguen);
        return "Resuelto: " + titulos(resueltas);
    }

    String cuerpo(List<Tarjeta> nuevas, List<Tarjeta> cambiaron, List<Tarjeta> siguen, List<Tarjeta> resueltas) {
        StringBuilder sb = new StringBuilder("Estado del sistema de La Serranita al ").append(ahora().format(FECHA_HORA)).append(".\n");
        seccion(sb, "NUEVAS ALERTAS", nuevas, true);
        seccion(sb, "CAMBIARON", cambiaron, true);
        seccion(sb, "SIGUEN EN ALERTA (recordatorio)", siguen, true);
        seccion(sb, "RESUELTAS", resueltas, false);
        return sb.append(pie()).toString();
    }

    private static void seccion(StringBuilder sb, String titulo, List<Tarjeta> tarjetas, boolean conDetalles) {
        if (tarjetas.isEmpty()) return;
        sb.append("\n").append(titulo).append("\n");
        for (Tarjeta t : tarjetas) {
            sb.append("- ").append(t.titulo()).append(": ").append(t.resumen()).append("\n");
            if (conDetalles && t.detalles() != null) {
                for (String d : t.detalles()) sb.append("    · ").append(d).append("\n");
            }
        }
    }

    private String pie() {
        return urlApp.isBlank() ? "" : "\nVer en la app: " + urlApp.replaceAll("/+$", "") + "/sistema\n";
    }

    private void enviar(String asunto, String texto) throws Exception {
        MimeMessage mensaje = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mensaje, false, "UTF-8");
        helper.setFrom(remitente, "La Serranita - Sistema");
        helper.setTo(destinatarios.split("\\s*,\\s*"));
        helper.setSubject("[La Serranita] " + asunto);
        helper.setText(texto, false);
        try {
            mailSender.send(mensaje);
            estadoMails.ok();
        } catch (RuntimeException e) {
            estadoMails.fallo(e.getMessage());
            throw e;
        }
    }

    private static String titulos(List<Tarjeta> tarjetas) {
        return tarjetas.stream().map(Tarjeta::titulo).collect(Collectors.joining(", "));
    }

    private static boolean hace(LocalDateTime momento, LocalDateTime ahora, Duration tiempo) {
        return momento == null || !momento.plus(tiempo).isAfter(ahora);
    }

    private static String recortar(String texto) {
        return texto != null && texto.length() > 300 ? texto.substring(0, 300) : texto;
    }

    private static LocalDateTime ahora() {
        return LocalDateTime.now(AuditoriaService.ZONA).withNano(0);
    }
}
