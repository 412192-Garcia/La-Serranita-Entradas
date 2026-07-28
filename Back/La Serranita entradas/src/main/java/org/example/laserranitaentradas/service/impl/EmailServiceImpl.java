package org.example.laserranitaentradas.service.impl;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.transaction.Transactional;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.CompraDetalle;
import org.example.laserranitaentradas.repository.CompraRepository;
import org.example.laserranitaentradas.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final JavaMailSender mailSender;
    private final CompraRepository compraRepository;

    @Value("${spring.mail.username}")
    private String remitente;

    public EmailServiceImpl(JavaMailSender mailSender, CompraRepository compraRepository) {
        this.mailSender = mailSender;
        this.compraRepository = compraRepository;
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

            helper.setFrom(remitente);
            helper.setTo(compra.getContactEmail());
            helper.setSubject("¡Compra confirmada! - La Serranita Parque Recreativo");

            String htmlBody = construirHtmlEmail(compra);
            helper.setText(htmlBody, true);

            mailSender.send(message);
            // Se loguea el id de compra, no el correo, para no volcar datos de contacto al log.
            log.info("Email de confirmación enviado para la compra ID {}", compraId);

        } catch (MessagingException e) {
            log.error("Error al enviar el email de confirmación de la compra ID {}", compraId, e);
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

        } catch (MessagingException e) {
            log.error("Error al enviar el email de aviso de regalo de la compra ID {}", compraId, e);
        }
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

                <p style="text-align: right; font-size: 1.1em;"><strong>Total pagado:</strong> $%s</p>
                
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
}