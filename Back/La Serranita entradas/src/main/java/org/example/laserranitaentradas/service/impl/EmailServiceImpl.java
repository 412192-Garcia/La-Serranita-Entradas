package org.example.laserranitaentradas.service.impl;

import jakarta.mail.internet.MimeMessage;
import jakarta.transaction.Transactional;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.CompraDetalle;
import org.example.laserranitaentradas.model.entity.EstadoCompra;
import org.example.laserranitaentradas.model.entity.FormaPago;
import org.example.laserranitaentradas.repository.CompraRepository;
import org.example.laserranitaentradas.service.EmailService;
import org.example.laserranitaentradas.service.RechazoOperacionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

    // ---------- Paleta del mail (calcada de la marca: ver Front/src/styles.css --color-primary
    // y el fondo oscuro de app.css) para que el comprobante se vea como parte de la misma app,
    // no como una plantilla genérica. ----------
    private static final String COLOR_FONDO = "#f0f2ef";
    private static final String COLOR_HEADER = "#39a935";
    private static final String COLOR_HEADER_TEXTO_MUTED = "#eafbe7";
    private static final String COLOR_PRIMARIO = "#39a935";
    private static final String COLOR_PRIMARIO_OSCURO = "#2c7f2a";
    private static final String COLOR_PRIMARIO_CLARO = "#e8f6e7";
    private static final String COLOR_AMBAR = "#b8860b";
    private static final String COLOR_AMBAR_OSCURO = "#8a6d1a";
    private static final String COLOR_AMBAR_CLARO = "#fff8e1";
    private static final String COLOR_BORDE = "#e5e7eb";
    private static final String COLOR_TEXTO = "#111827";
    private static final String COLOR_TEXTO_SECUNDARIO = "#374151";
    private static final String COLOR_TEXTO_MUTED = "#6b7280";

    private static final NumberFormat FORMATO_MONEDA = NumberFormat.getCurrencyInstance(new Locale("es", "AR"));

    private final JavaMailSender mailSender;
    private final CompraRepository compraRepository;
    private final RechazoOperacionService rechazoService;

    @Value("${spring.mail.username}")
    private String remitente;

    // Cada mail que sale de la app va también, en copia oculta, a esta casilla del parque: es el
    // mismo mensaje (un solo envío con BCC), así el destinatario no ve la copia. Viene de la env
    // MAIL_COPIA; si está vacía no se manda copia.
    @Value("${app.mail.copia:}")
    private String copia;

    // Mismo dominio público que usa Mercado Pago para el back_url: es donde vive el logo
    // (Front/public/img/logo-la-serranita.png, servido en la raíz por Angular) que el header
    // del mail carga como <img> con URL absoluta — un mail no puede resolver rutas relativas.
    @Value("${mercadopago.frontend-url}")
    private String frontendUrl;

    // Teléfono/mail de contacto del parque, mostrados en el mail para que el cliente pueda
    // consultar o pedir un cambio sin tener que responder al remitente (una casilla no-reply).
    // Con default porque son datos públicos del negocio, no un secreto — a diferencia de
    // MAIL_USERNAME/MAIL_PASSWORD no hace falta configurarlos para que la app arranque.
    @Value("${app.contacto.telefono:+5493547642649}")
    private String contactoTelefono;

    @Value("${app.contacto.email:info@laserranita.com.ar}")
    private String contactoEmail;

    // @Lazy en rechazoService: RechazoOperacionServiceImpl ahora también depende de
    // CompraService (para reintentar una VENTA rechazada), que a su vez depende de
    // EmailService — sin el @Lazy acá se cierra un ciclo real en la construcción de los beans
    // (EmailService → RechazoOperacionService → CompraService → EmailService). Con @Lazy, este
    // constructor recibe un proxy que recién resuelve el bean real la primera vez que
    // registrarRechazoEnvio() lo usa, momento en el que el contexto ya terminó de armarse.
    public EmailServiceImpl(JavaMailSender mailSender, CompraRepository compraRepository,
                             @Lazy RechazoOperacionService rechazoService) {
        this.mailSender = mailSender;
        this.compraRepository = compraRepository;
        this.rechazoService = rechazoService;
    }

    @Async
    @Transactional
    @Override
    public void enviarComprobanteCompra(Long compraId) {
        Compra compra = compraRepository.findById(compraId).orElse(null);

        if (compra == null || compra.getContactEmail() == null || compra.getContactEmail().isBlank()) {
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            boolean pendienteDePago = compra.getEstado() == EstadoCompra.RESERVADO_EFECTIVO;
            boolean esRegalo = compra.getFechaVisita() == null;

            helper.setFrom(remitente);
            helper.setTo(compra.getContactEmail());
            agregarCopia(helper);
            String asunto;
            if (esRegalo) {
                asunto = pendienteDePago
                        ? "¡Regalo reservado! - La Serranita Parque Recreativo"
                        : "¡Gracias por tu regalo! - La Serranita Parque Recreativo";
            } else {
                asunto = pendienteDePago
                        ? "¡Reserva confirmada! Pagás en la entrada - La Serranita Parque Recreativo"
                        : "¡Compra confirmada! - La Serranita Parque Recreativo";
            }
            helper.setSubject(asunto);

            String htmlBody = construirHtmlEmail(compra);
            helper.setText(htmlBody, true);

            mailSender.send(message);
            // Se loguea el id de compra, no el correo, para no volcar datos de contacto al log.
            log.info("Email de confirmación enviado para la compra ID {}", compraId);

        } catch (Exception e) {
            // mailSender.send(...) tira MailException (no checked) si falla el SMTP en sí, y
            // MimeMessageHelper tira MessagingException al armar el mensaje: como esto corre
            // @Async sin nadie mirando la pantalla, atrapar ambas acá es lo único que evita que
            // el fallo se pierda en un log que nadie lee — por eso también se avisa al admin.
            log.error("Error al enviar el email de confirmación de la compra ID {}", compraId, e);
            registrarRechazoEnvio(compra, "comprobante de compra", e);
        }
    }

    @Async
    @Transactional
    @Override
    public void enviarAvisoRegalo(Long compraId) {
        Compra compra = compraRepository.findById(compraId).orElse(null);

        if (compra == null || compra.getReceptorEmail() == null || compra.getReceptorEmail().isBlank()) {
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(remitente);
            helper.setTo(compra.getReceptorEmail());
            agregarCopia(helper);
            helper.setSubject("¡Recibiste un regalo! - La Serranita Parque Recreativo");

            String htmlBody = construirHtmlAvisoRegalo(compra);
            helper.setText(htmlBody, true);

            mailSender.send(message);
            log.info("Email de aviso de regalo enviado para la compra ID {}", compraId);

        } catch (Exception e) {
            log.error("Error al enviar el email de aviso de regalo de la compra ID {}", compraId, e);
            registrarRechazoEnvio(compra, "aviso de regalo", e);
        }
    }

    private void agregarCopia(MimeMessageHelper helper) throws jakarta.mail.MessagingException {
        if (copia != null && !copia.isBlank()) {
            helper.setBcc(copia);
        }
    }

    /** Ningún llamador de estos métodos (webhook, verificación de pago, "reenviar comprobante"
     * desde Boletería) espera el resultado: al ser @Async, el fallo no vuelve a nadie que lo esté
     * mirando, así que es esto o que se pierda para siempre en un log del servidor. */
    private void registrarRechazoEnvio(Compra compra, String tipoEmail, Exception causa) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("compraId", compra.getId());
        payload.put("codigoReserva", compra.getCodigoReserva());
        payload.put("email", tipoEmail.equals("aviso de regalo") ? compra.getReceptorEmail() : compra.getContactEmail());
        payload.put("tipoEmail", tipoEmail);
        payload.put("detalleTecnico", causa.getMessage());
        rechazoService.registrar("COMPROBANTE_EMAIL", payload,
                "No se pudo enviar el email de " + tipoEmail + ". Reenvialo manualmente desde Boletería una vez resuelto.",
                null);
    }

    // ---------- Layout compartido ----------

    /** Franja oscura de arriba con el logo (en un chip blanco: el isologo de La Serranita tiene
     * texto verde/marrón que se pierde contra un fondo oscuro sin ese respaldo), el título/bajada
     * del mail y una píldora de estado. */
    private String construirHeader(String titulo, String bajada, String estadoTexto, String estadoColorTexto, String estadoColorFondo) {
        return """
            <tr>
              <td style="background-color:%s; padding:28px 28px 24px; border-radius:14px 14px 0 0;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                  <tr>
                    <td style="vertical-align:top;">
                      <p style="margin:0; font-family:Arial,Helvetica,sans-serif; font-size:21px; font-weight:700; color:#ffffff; line-height:1.3;">%s</p>
                      <p style="margin:6px 0 0; font-family:Arial,Helvetica,sans-serif; font-size:13.5px; color:%s; line-height:1.4;">%s</p>
                    </td>
                    <td style="vertical-align:top; text-align:right; width:112px;">
                      <table role="presentation" cellpadding="0" cellspacing="0" style="background-color:#ffffff; border-radius:10px; display:inline-block;">
                        <tr><td style="padding:8px 10px;">
                          <img src="%s/img/logo-la-serranita.png" width="96" alt="La Serranita" style="display:block; border:0; max-width:96px; height:auto;" />
                        </td></tr>
                      </table>
                    </td>
                  </tr>
                </table>
                <table role="presentation" cellpadding="0" cellspacing="0" style="margin-top:16px;">
                  <tr><td style="background-color:%s; border-radius:999px; padding:6px 14px;">
                    <span style="font-family:Arial,Helvetica,sans-serif; font-size:12px; font-weight:700; color:%s; letter-spacing:0.2px;">%s</span>
                  </td></tr>
                </table>
              </td>
            </tr>
            """.formatted(COLOR_HEADER, titulo, COLOR_HEADER_TEXTO_MUTED, bajada, frontendUrl,
                estadoColorFondo, estadoColorTexto, estadoTexto);
    }

    private String construirFooter() {
        return """
            <tr>
              <td style="background-color:%s; padding:22px 28px; border-radius:0 0 14px 14px; text-align:center;">
                <p style="margin:0; font-family:Arial,Helvetica,sans-serif; font-size:13px; font-weight:700; color:#ffffff;">La Serranita &middot; Parque Recreativo</p>
                <p style="margin:6px 0 0; font-family:Arial,Helvetica,sans-serif; font-size:12px; color:%s;">Te esperamos de 11:00 a 18:30 hs.</p>
                <p style="margin:10px 0 0; font-family:Arial,Helvetica,sans-serif; font-size:12px; color:%s;">
                  %s &nbsp;&middot;&nbsp; <a href="mailto:%s" style="color:%s; text-decoration:none;">%s</a>
                </p>
              </td>
            </tr>
            """.formatted(COLOR_HEADER, COLOR_HEADER_TEXTO_MUTED, COLOR_HEADER_TEXTO_MUTED,
                contactoTelefono, contactoEmail, COLOR_HEADER_TEXTO_MUTED, contactoEmail);
    }

    /** Franja gris con los medios de contacto: no reemplaza al remitente (una casilla no-reply
     * que nadie lee si el cliente le responde), así que el mail necesita decir explícitamente
     * a dónde escribir para pedir un cambio o hacer una consulta. */
    private String construirTarjetaAyuda() {
        return """
            <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background-color:#f7f8f6; border-radius:10px; margin-bottom:16px;">
              <tr><td style="padding:14px 16px;">
                <p style="margin:0; font-family:Arial,Helvetica,sans-serif; font-size:13px; color:%s; line-height:1.5;">
                  &iquest;Necesit&aacute;s cambiar algo de tu reserva o ten&eacute;s una consulta?<br>
                  Escribinos al <strong style="color:%s;">%s</strong> o a <a href="mailto:%s" style="color:%s; text-decoration:none; font-weight:700;">%s</a>.
                </p>
              </td></tr>
            </table>
            """.formatted(COLOR_TEXTO_SECUNDARIO, COLOR_TEXTO, contactoTelefono, contactoEmail, COLOR_PRIMARIO_OSCURO, contactoEmail);
    }

    private String construirTarjetaTitulo(String icono, String titulo) {
        return """
            <table role="presentation" cellpadding="0" cellspacing="0" style="margin-bottom:10px;">
              <tr>
                <td style="vertical-align:middle; padding-right:8px;">%s</td>
                <td style="vertical-align:middle; font-family:Arial,Helvetica,sans-serif; font-size:14px; font-weight:700; color:%s;">%s</td>
              </tr>
            </table>
            """.formatted(icono, COLOR_TEXTO, titulo);
    }

    private String envolverEnLayout(String header, String cuerpo, String footer) {
        return """
            <!DOCTYPE html>
            <html lang="es">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <title>La Serranita</title>
            </head>
            <body style="margin:0; padding:0; background-color:%s;">
              <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background-color:%s;">
                <tr>
                  <td align="center" style="padding:24px 16px;">
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:600px; background-color:#ffffff; border-radius:14px; overflow:hidden;">
                      %s
                      <tr>
                        <td style="padding:24px 24px 8px;">
                          %s
                        </td>
                      </tr>
                      %s
                    </table>
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """.formatted(COLOR_FONDO, COLOR_FONDO, header, cuerpo, footer);
    }

    // ---------- Íconos (trazos al estilo Lucide, inline: nada de emojis en la UI de la app,
    // ver memoria del proyecto — acá aplica igual) ----------

    private String icono(String path, String color) {
        return """
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="%s" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="display:block;">%s</svg>
            """.formatted(color, path).strip();
    }

    private static final String PATH_TICKET = "<path d=\"M2 9a3 3 0 1 0 0 6v2a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-2a3 3 0 1 1 0-6V7a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2Z\"/><path d=\"M13 5v2\"/><path d=\"M13 17v2\"/><path d=\"M13 11v2\"/>";
    private static final String PATH_CALENDARIO = "<rect width=\"18\" height=\"18\" x=\"3\" y=\"4\" rx=\"2\" ry=\"2\"/><line x1=\"16\" x2=\"16\" y1=\"2\" y2=\"6\"/><line x1=\"8\" x2=\"8\" y1=\"2\" y2=\"6\"/><line x1=\"3\" x2=\"21\" y1=\"10\" y2=\"10\"/>";
    private static final String PATH_CHECK = "<path d=\"M3.85 8.62a4 4 0 0 1 4.78-4.77 4 4 0 0 1 6.74 0 4 4 0 0 1 4.78 4.78 4 4 0 0 1 0 6.74 4 4 0 0 1-4.77 4.78 4 4 0 0 1-6.75 0 4 4 0 0 1-4.78-4.77 4 4 0 0 1 0-6.76Z\"/><path d=\"m9 12 2 2 4-4\"/>";
    private static final String PATH_GIFT = "<rect x=\"3\" y=\"8\" width=\"18\" height=\"4\" rx=\"1\"/><path d=\"M12 8v13\"/><path d=\"M19 12v7a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-7\"/><path d=\"M7.5 8a2.5 2.5 0 0 1 0-5C11 3 12 8 12 8\"/><path d=\"M16.5 8a2.5 2.5 0 0 0 0-5C13 3 12 8 12 8\"/>";

    private String formatearMonto(BigDecimal monto) {
        return FORMATO_MONEDA.format(monto == null ? BigDecimal.ZERO : monto);
    }

    /** Filas de la tabla de entradas: nombre a la izquierda, cantidad a la derecha. Sin precio
     * por línea a propósito: CompraDetalle.precioUnitario sólo lo completan los artículos del
     * POS (ver CompraServiceImpl#construirLineasArticulos) — en una entrada online ese campo
     * queda null, y el precio real puede variar por cupón/promo/forma de pago, así que mostrar
     * "cantidad x tipoEntrada.getPrecio()" podría no coincidir con lo que de verdad se cobró. */
    private String construirFilasDetalle(Compra compra) {
        StringBuilder filas = new StringBuilder();
        if (compra.getDetalles() != null) {
            for (CompraDetalle detalle : compra.getDetalles()) {
                filas.append("""
                    <tr>
                      <td style="padding:9px 0; border-bottom:1px solid %s; font-family:Arial,Helvetica,sans-serif; font-size:13.5px; color:%s;">%s</td>
                      <td style="padding:9px 0; border-bottom:1px solid %s; font-family:Arial,Helvetica,sans-serif; font-size:13.5px; color:%s; text-align:right; white-space:nowrap;">&times;&nbsp;%d</td>
                    </tr>
                    """.formatted(COLOR_BORDE, COLOR_TEXTO_SECUNDARIO, detalle.getTipoEntrada().getNombre(),
                        COLOR_BORDE, COLOR_TEXTO, detalle.getCantidad()));
            }
        }
        return filas.toString();
    }

    private String construirTarjetaDetalle(Compra compra, String etiquetaTotal, String colorTotal, boolean mostrarTotal) {
        String filaTotal = mostrarTotal
                ? """
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="margin-top:4px;">
                      <tr>
                        <td style="padding-top:10px; font-family:Arial,Helvetica,sans-serif; font-size:14px; font-weight:700; color:%s;">%s</td>
                        <td style="padding-top:10px; font-family:Arial,Helvetica,sans-serif; font-size:16px; font-weight:700; color:%s; text-align:right;">%s</td>
                      </tr>
                    </table>
                    """.formatted(COLOR_TEXTO, etiquetaTotal, colorTotal, formatearMonto(compra.getMontoTotal()))
                : "";

        return """
            <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="border:1px solid %s; border-radius:10px; margin-bottom:16px;">
              <tr><td style="padding:14px 16px;">
                %s
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                  %s
                </table>
                %s
              </td></tr>
            </table>
            """.formatted(COLOR_BORDE, construirTarjetaTitulo(icono(PATH_TICKET, COLOR_PRIMARIO), "Detalle de la compra"),
                construirFilasDetalle(compra), filaTotal);
    }

    /** etiquetaFecha/valorFecha y etiquetaPersona son parametrizables porque una compra-regalo
     * (ver esRegalo en construirHtmlEmail) le muestra al comprador "Vigencia" en vez de "Fecha
     * de visita" (no hay una: la elige quien recibe el regalo) y "Se lo regalás a" en vez de
     * "Titular" (el titular de esta tarjeta no es el comprador, es el destinatario). */
    private String construirTarjetaReserva(Compra compra, String etiquetaFecha, String valorFecha,
                                            String etiquetaPersona, String nombrePersona, String dniPersona) {
        return """
            <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="border:1px solid %s; border-radius:10px; margin-bottom:16px;">
              <tr><td style="padding:14px 16px;">
                %s
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                  <tr>
                    <td style="padding:5px 8px 5px 0; font-family:Arial,Helvetica,sans-serif; font-size:13.5px; color:%s; white-space:nowrap;">N&deg; de reserva</td>
                    <td style="padding:5px 0; font-family:Arial,Helvetica,sans-serif; font-size:13.5px; color:%s; text-align:right; font-weight:700;">#%s</td>
                  </tr>
                  <tr>
                    <td style="padding:5px 8px 5px 0; font-family:Arial,Helvetica,sans-serif; font-size:13.5px; color:%s; white-space:nowrap;">%s</td>
                    <td style="padding:5px 0; font-family:Arial,Helvetica,sans-serif; font-size:13.5px; color:%s; text-align:right;">%s</td>
                  </tr>
                  <tr>
                    <td style="padding:5px 8px 5px 0; font-family:Arial,Helvetica,sans-serif; font-size:13.5px; color:%s; white-space:nowrap; vertical-align:top;">%s</td>
                    <td style="padding:5px 0; font-family:Arial,Helvetica,sans-serif; font-size:13.5px; color:%s; text-align:right;">%s &middot; DNI %s</td>
                  </tr>
                </table>
              </td></tr>
            </table>
            """.formatted(COLOR_BORDE, construirTarjetaTitulo(icono(PATH_CALENDARIO, COLOR_PRIMARIO), "Datos de la reserva"),
                COLOR_TEXTO_MUTED, COLOR_TEXTO, compra.getCodigoReserva(),
                COLOR_TEXTO_MUTED, etiquetaFecha, COLOR_TEXTO_SECUNDARIO, valorFecha,
                COLOR_TEXTO_MUTED, etiquetaPersona, COLOR_TEXTO_SECUNDARIO, nombrePersona, dniPersona);
    }

    private String construirTarjetaModalidad(String titulo, String texto, String colorFondo, String colorTitulo, String colorTexto) {
        return """
            <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background-color:%s; border-radius:10px; margin-bottom:16px;">
              <tr><td style="padding:14px 16px;">
                <table role="presentation" cellpadding="0" cellspacing="0" style="margin-bottom:6px;">
                  <tr>
                    <td style="vertical-align:middle; padding-right:8px;">%s</td>
                    <td style="vertical-align:middle; font-family:Arial,Helvetica,sans-serif; font-size:14px; font-weight:700; color:%s;">%s</td>
                  </tr>
                </table>
                <p style="margin:0; font-family:Arial,Helvetica,sans-serif; font-size:13.5px; color:%s; line-height:1.5;">%s</p>
              </td></tr>
            </table>
            """.formatted(colorFondo, icono(PATH_CHECK, colorTitulo), colorTitulo, titulo, colorTexto, texto);
    }

    // ---------- Comprobante de compra/reserva ----------

    private String construirHtmlEmail(Compra compra) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String fechaVisitaStr = compra.getFechaVisita() != null ? compra.getFechaVisita().format(formatter) : "Fecha a confirmar";

        String nombreCliente = compra.getCliente() != null
                ? compra.getCliente().getNombre() + " " + compra.getCliente().getApellido()
                : "Visitante";

        String dniCliente = compra.getCliente() != null ? compra.getCliente().getDni() : "-";

        // Sin fecha de visita la compra es un regalo (ver el Javadoc de Compra.fechaVisita):
        // este comprobante es del COMPRADOR, no de quien va a entrar al parque, así que no
        // puede decirle "presentá tu DNI en la boletería" con el DNI del propio comprador — el
        // que entra (y paga, si quedó pendiente) es el destinatario del regalo.
        boolean esRegalo = compra.getFechaVisita() == null;
        String nombreReceptor = compra.getReceptorNombre() != null ? compra.getReceptorNombre() : "la persona que elijas";
        String dniReceptor = compra.getReceptorDni() != null ? compra.getReceptorDni() : "-";

        // Una reserva en efectivo todavía no cobró nada: el mail no puede decir "pago confirmado"
        // ni "NO necesitas imprimir nada" como si ya hubiese pagado, porque le falta abonar en la
        // boletería al llegar. El pagado online sí entra directo con el DNI, sin nada pendiente.
        boolean pendienteDePago = compra.getEstado() == EstadoCompra.RESERVADO_EFECTIVO;

        String header;
        String tarjetaModalidad;
        String etiquetaTotal;
        String colorTotal;

        if (pendienteDePago) {
            header = esRegalo
                    ? construirHeader("¡Regalo reservado!", "Le reservaste una entrada a " + nombreReceptor + " para La Serranita.",
                            "PAGO PENDIENTE", COLOR_AMBAR_OSCURO, COLOR_AMBAR_CLARO)
                    : construirHeader("¡Reserva confirmada!", "Tu reserva en La Serranita quedó registrada.",
                            "PAGO PENDIENTE", COLOR_AMBAR_OSCURO, COLOR_AMBAR_CLARO);
            String textoModalidad = esRegalo
                    ? "<strong>Todavía no se abonó.</strong> " + nombreReceptor + " tiene que presentar su DNI <strong>" + dniReceptor
                            + "</strong> en la boletería y pagar el total en efectivo antes de ingresar."
                    : "<strong>Todavía no abonaste.</strong> Al llegar al parque, el titular debe presentar el DNI <strong>"
                            + dniCliente + "</strong> en la boletería y pagar el total en efectivo antes de ingresar con todo el grupo.";
            tarjetaModalidad = construirTarjetaModalidad("Modalidad de ingreso", textoModalidad,
                    COLOR_AMBAR_CLARO, COLOR_AMBAR_OSCURO, COLOR_AMBAR_OSCURO);
            etiquetaTotal = "Total a abonar en la entrada";
            colorTotal = COLOR_AMBAR_OSCURO;
        } else {
            header = esRegalo
                    ? construirHeader("¡Gracias por tu compra!", "Le regalaste una entrada a " + nombreReceptor + " para La Serranita.",
                            "REGALO ENVIADO", COLOR_PRIMARIO_OSCURO, COLOR_PRIMARIO_CLARO)
                    : construirHeader("¡Pago confirmado!", "Tu compra en La Serranita fue procesada con éxito.",
                            "CONFIRMADO", COLOR_PRIMARIO_OSCURO, COLOR_PRIMARIO_CLARO);
            String textoModalidad = esRegalo
                    ? "Le avisamos por mail a " + nombreReceptor + " con las instrucciones para usarla. No hace falta que vayas vos: al llegar al parque, "
                            + nombreReceptor + " presenta su DNI <strong>" + dniReceptor + "</strong> en la boletería para ingresar."
                    : "<strong>No necesitás imprimir nada.</strong> Al llegar al parque, el titular debe presentar el DNI <strong>"
                            + dniCliente + "</strong> en la boletería para ingresar con todo el grupo.";
            tarjetaModalidad = construirTarjetaModalidad(esRegalo ? "Cómo la va a usar" : "Modalidad de ingreso", textoModalidad,
                    COLOR_PRIMARIO_CLARO, COLOR_PRIMARIO_OSCURO, COLOR_PRIMARIO_OSCURO);
            etiquetaTotal = "Total pagado";
            colorTotal = COLOR_PRIMARIO_OSCURO;
        }

        // Una reserva RESERVA_ADMIN (invitados, o ventas por agencia donde el cobro real se
        // hizo por fuera) no tiene un "monto pagado acá" honesto para mostrar: o es gratis, o
        // lo cobró la agencia con su propio margen — mostrarle nuestro precio de lista al
        // comprador filtraría esa comisión sin que venga al caso. Por eso el total se omite
        // sólo para ese caso (ver ReservaAdminServiceImpl).
        boolean mostrarTotal = compra.getFormaPago() != FormaPago.RESERVA_ADMIN;

        String tarjetaReserva = esRegalo
                ? construirTarjetaReserva(compra, "Vigencia", "90 días desde la fecha de compra",
                        "Se lo regalás a", nombreReceptor, dniReceptor)
                : construirTarjetaReserva(compra, "Fecha de visita", fechaVisitaStr, "Titular", nombreCliente, dniCliente);

        String cuerpo = """
            <p style="margin:0 0 16px; font-family:Arial,Helvetica,sans-serif; font-size:14px; color:%s;">Hola <strong style="color:%s;">%s</strong>,</p>
            %s
            %s
            %s
            %s
            """.formatted(COLOR_TEXTO_SECUNDARIO, COLOR_TEXTO, nombreCliente,
                construirTarjetaAyuda(),
                tarjetaReserva,
                tarjetaModalidad,
                construirTarjetaDetalle(compra, etiquetaTotal, colorTotal, mostrarTotal));

        return envolverEnLayout(header, cuerpo, construirFooter());
    }

    // ---------- Aviso de regalo ----------

    private String construirHtmlAvisoRegalo(Compra compra) {
        String nombreComprador = compra.getCliente() != null
                ? compra.getCliente().getNombre() + " " + compra.getCliente().getApellido()
                : "Alguien";

        String header = construirHeader("¡Recibiste un regalo!",
                nombreComprador + " te regaló una entrada para La Serranita.",
                "REGALO", COLOR_PRIMARIO_OSCURO, COLOR_PRIMARIO_CLARO);

        String tarjetaComoUsarla = construirTarjetaModalidad("Cómo usarla",
                "Es válida por 90 días desde la fecha de compra y no tiene un día fijo asignado. Al llegar al parque, presentá tu DNI <strong>"
                        + compra.getReceptorDni() + "</strong> en la boletería para ingresar.",
                COLOR_PRIMARIO_CLARO, COLOR_PRIMARIO_OSCURO, COLOR_PRIMARIO_OSCURO);

        String tarjetaCodigo = """
            <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="border:1px solid %s; border-radius:10px; margin-bottom:16px;">
              <tr><td style="padding:14px 16px;">
                %s
                <p style="margin:0; font-family:Arial,Helvetica,sans-serif; font-size:13.5px; color:%s;">C&oacute;digo de referencia: <strong style="color:%s;">#%s</strong></p>
              </td></tr>
            </table>
            """.formatted(COLOR_BORDE, construirTarjetaTitulo(icono(PATH_GIFT, COLOR_PRIMARIO), "Datos del regalo"),
                COLOR_TEXTO_SECUNDARIO, COLOR_TEXTO, compra.getCodigoReserva());

        String cuerpo = """
            <p style="margin:0 0 16px; font-family:Arial,Helvetica,sans-serif; font-size:14px; color:%s;">Hola <strong style="color:%s;">%s</strong>,</p>
            <p style="margin:0 0 16px; font-family:Arial,Helvetica,sans-serif; font-size:14px; color:%s;"><strong style="color:%s;">%s</strong> te regaló una entrada para <strong>La Serranita Parque Recreativo</strong>. &iexcl;Felicitaciones!</p>
            %s
            %s
            """.formatted(COLOR_TEXTO_SECUNDARIO, COLOR_TEXTO, compra.getReceptorNombre(),
                COLOR_TEXTO_SECUNDARIO, COLOR_TEXTO, nombreComprador,
                tarjetaComoUsarla, tarjetaCodigo);

        return envolverEnLayout(header, cuerpo, construirFooter());
    }
}
