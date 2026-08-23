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

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final JavaMailSender mailSender;
    private final CompraRepository compraRepository;
    private final RechazoOperacionService rechazoService;

    @Value("${spring.mail.username}")
    private String remitente;

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

            helper.setFrom(remitente);
            helper.setTo(compra.getContactEmail());
            helper.setSubject(pendienteDePago
                    ? "¡Reserva confirmada! Pagás en la entrada - La Serranita Parque Recreativo"
                    : "¡Compra confirmada! - La Serranita Parque Recreativo");

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

    private String construirHtmlAvisoRegalo(Compra compra) {
        String nombreComprador = compra.getCliente() != null
                ? compra.getCliente().getNombre() + " " + compra.getCliente().getApellido()
                : "Alguien";

        return """
            <div style="font-family: Arial, sans-serif; max-width: 600px; margin: auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 8px;">
                <h2 style="color: #2e7d32; text-align: center;">¡Recibiste un regalo! 🎁</h2>
                <p>Hola <strong>%s</strong>,</p>
                <p><strong>%s</strong> te regaló una entrada para <strong>La Serranita Parque Recreativo</strong>. ¡Felicitaciones!</p>

                <div style="background-color: #e8f5e9; padding: 15px; border-radius: 6px; margin: 20px 0;">
                    <h3 style="margin-top: 0; color: #1b5e20;">🪪 Cómo usarla</h3>
                    <p style="margin-bottom: 0;">Es válida por 90 días desde la fecha de compra y no tiene un día fijo asignado. Al llegar al parque, presentá tu DNI <strong>%s</strong> en la boletería para ingresar.</p>
                </div>

                <p>Código de referencia: <strong>#%s</strong></p>

                <hr style="border: none; border-top: 1px solid #eee; margin: 20px 0;">
                <p style="font-size: 0.85em; color: #777; text-align: center;">La Serranita Parque Recreativo - Te esperamos de 11:00 a 18:30 hs.</p>
            </div>
            """.formatted(compra.getReceptorNombre(), nombreComprador, compra.getReceptorDni(), compra.getCodigoReserva());
    }

    private String construirHtmlEmail(Compra compra) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String fechaVisitaStr = compra.getFechaVisita() != null ? compra.getFechaVisita().format(formatter) : "Fecha a confirmar";

        String nombreCliente = compra.getCliente() != null
                ? compra.getCliente().getNombre() + " " + compra.getCliente().getApellido()
                : "Visitante";

        String dniCliente = compra.getCliente() != null ? compra.getCliente().getDni() : "-";

        StringBuilder detallesHtml = new StringBuilder();
        if (compra.getDetalles() != null) {
            for (CompraDetalle detalle : compra.getDetalles()) {
                detallesHtml.append("<tr>")
                        .append("<td style='padding: 8px; border-bottom: 1px solid #eee;'>").append(detalle.getTipoEntrada().getNombre()).append("</td>")
                        .append("<td style='padding: 8px; border-bottom: 1px solid #eee; text-align: center;'>").append(detalle.getCantidad()).append("</td>")
                        .append("</tr>");
            }
        }

        // Una reserva en efectivo todavía no cobró nada: el mail no puede decir "pago confirmado"
        // ni "NO necesitas imprimir nada" como si ya hubiese pagado, porque le falta abonar en la
        // boletería al llegar. El pagado online sí entra directo con el DNI, sin nada pendiente.
        boolean pendienteDePago = compra.getEstado() == EstadoCompra.RESERVADO_EFECTIVO;

        if (pendienteDePago) {
            return """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 8px;">
                    <h2 style="color: #b8860b; text-align: center;">¡Reserva confirmada! 🎉</h2>
                    <p>Hola <strong>%s</strong>,</p>
                    <p>Tu reserva en <strong>La Serranita Parque Recreativo</strong> quedó registrada.</p>

                    <div style="background-color: #fff8e1; padding: 15px; border-radius: 6px; margin: 20px 0;">
                        <h3 style="margin-top: 0; color: #8a6d1a;">🪪 Modalidad de Ingreso</h3>
                        <p style="margin-bottom: 0;"><strong>Todavía no abonaste.</strong> Al llegar al parque, el titular debe presentar el DNI <strong>%s</strong> en la boletería y pagar el total en efectivo antes de ingresar con todo el grupo.</p>
                    </div>

                    <h3>Resumen de la Reserva (#%s)</h3>
                    <p><strong>Fecha de visita:</strong> %s</p>

                    <table style="width: 100%%; border-collapse: collapse; margin-bottom: 20px;">
                        <thead>
                            <tr style="background-color: #f5f5f5;">
                                <th style="padding: 8px; text-align: left;">Entrada</th>
                                <th style="padding: 8px; text-align: center;">Cantidad</th>
                            </tr>
                        </thead>
                        <tbody>
                            %s
                        </tbody>
                    </table>

                    <p style="text-align: right; font-size: 1.1em;"><strong>Total a abonar en la entrada:</strong> $%s</p>

                    <hr style="border: none; border-top: 1px solid #eee; margin: 20px 0;">
                    <p style="font-size: 0.85em; color: #777; text-align: center;">La Serranita Parque Recreativo - Te esperamos de 11:00 a 18:30 hs.</p>
                </div>
                """.formatted(
                    nombreCliente,
                    dniCliente,
                    compra.getCodigoReserva(),
                    fechaVisitaStr,
                    detallesHtml.toString(),
                    compra.getMontoTotal()
            );
        }

        // Una reserva RESERVA_ADMIN (invitados, o ventas por agencia donde el cobro real se
        // hizo por fuera) no tiene un "monto pagado acá" honesto para mostrar: o es gratis, o
        // lo cobró la agencia con su propio margen — mostrarle nuestro precio de lista al
        // comprador filtraría esa comisión sin que venga al caso. Por eso esta línea se omite
        // sólo para ese caso (ver ReservaAdminServiceImpl).
        String filaTotalPagado = compra.getFormaPago() == FormaPago.RESERVA_ADMIN
                ? ""
                : "<p style=\"text-align: right; font-size: 1.1em;\"><strong>Total pagado:</strong> $%s</p>".formatted(compra.getMontoTotal());

        return """
            <div style="font-family: Arial, sans-serif; max-width: 600px; margin: auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 8px;">
                <h2 style="color: #2e7d32; text-align: center;">¡Pago Confirmado! 🎉</h2>
                <p>Hola <strong>%s</strong>,</p>
                <p>Tu compra en <strong>La Serranita Parque Recreativo</strong> fue procesada exitosamente.</p>

                <div style="background-color: #e8f5e9; padding: 15px; border-radius: 6px; margin: 20px 0;">
                    <h3 style="margin-top: 0; color: #1b5e20;">🪪 Modalidad de Ingreso</h3>
                    <p style="margin-bottom: 0;"><strong>NO necesitas imprimir nada.</strong> Al llegar al parque, el titular debe presentar el DNI <strong>%s</strong> en la boletería para ingresar con todo el grupo.</p>
                </div>

                <h3>Resumen de la Compra (#%s)</h3>
                <p><strong>Fecha de visita:</strong> %s</p>

                <table style="width: 100%%; border-collapse: collapse; margin-bottom: 20px;">
                    <thead>
                        <tr style="background-color: #f5f5f5;">
                            <th style="padding: 8px; text-align: left;">Entrada</th>
                            <th style="padding: 8px; text-align: center;">Cantidad</th>
                        </tr>
                    </thead>
                    <tbody>
                        %s
                    </tbody>
                </table>

                %s

                <hr style="border: none; border-top: 1px solid #eee; margin: 20px 0;">
                <p style="font-size: 0.85em; color: #777; text-align: center;">La Serranita Parque Recreativo - Te esperamos de 11:00 a 18:30 hs.</p>
            </div>
            """.formatted(
                nombreCliente,
                dniCliente,
                compra.getCodigoReserva(),
                fechaVisitaStr,
                detallesHtml.toString(),
                filaTotalPagado
        );
    }
}