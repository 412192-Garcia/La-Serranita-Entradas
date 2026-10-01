package org.example.laserranitaentradas.service.impl;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.EstadoCompra;
import org.example.laserranitaentradas.repository.CompraRepository;
import org.example.laserranitaentradas.service.RechazoOperacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * enviarComprobanteCompra/enviarAvisoRegalo corren @Async, sin ningún admin mirando la
 * pantalla en el momento (los dispara el webhook de Mercado Pago, la verificación directa del
 * comprador, o el "reenviar" de Boletería que tampoco espera la respuesta): si el envío falla,
 * lo único que puede avisarle a un admin es dejarlo registrado en la cola de rechazos.
 */
@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {

    @Mock private JavaMailSender mailSender;
    @Mock private CompraRepository compraRepository;
    @Mock private RechazoOperacionService rechazoService;
    @Mock private org.example.laserranitaentradas.repository.FacturaRepository facturaRepository;
    @Mock private org.example.laserranitaentradas.service.factura.ComprobanteFacturaService comprobanteFacturaService;
    @Mock private org.example.laserranitaentradas.service.factura.FacturaPdfGenerator facturaPdfGenerator;

    private EmailServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EmailServiceImpl(mailSender, compraRepository, rechazoService,
                facturaRepository, comprobanteFacturaService, facturaPdfGenerator);
        // @Value no se inyecta fuera de un contexto de Spring: sin esto, helper.setFrom(null)
        // explota con NPE antes de siquiera llegar al mailSender.send(...) que cada test stubea.
        ReflectionTestUtils.setField(service, "remitente", "no-reply@laserranita.com");
    }

    @Test
    void enviarComprobanteCompra_fallaElEnvio_registraRechazoParaElAdmin() {
        Compra compra = Compra.builder()
                .id(42L)
                .codigoReserva("260820-1")
                .contactEmail("cliente@mail.com")
                .montoTotal(BigDecimal.TEN)
                .estado(EstadoCompra.APROBADO)
                .fechaVisita(LocalDate.now())
                .build();
        when(compraRepository.findById(42L)).thenReturn(Optional.of(compra));
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
        doThrow(new MailSendException("SMTP caído")).when(mailSender).send(any(MimeMessage.class));

        service.enviarComprobanteCompra(42L);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<String> motivoCaptor = ArgumentCaptor.forClass(String.class);
        verify(rechazoService).registrar(eq("COMPROBANTE_EMAIL"), payloadCaptor.capture(), motivoCaptor.capture(), isNull());

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) payloadCaptor.getValue();
        assertThat(payload).containsEntry("compraId", 42L);
        assertThat(payload).containsEntry("codigoReserva", "260820-1");
        assertThat(payload).containsEntry("email", "cliente@mail.com");
        assertThat(payload).containsEntry("tipoEmail", "comprobante de compra");
        assertThat(motivoCaptor.getValue()).contains("comprobante de compra");
    }

    @Test
    void enviarComprobanteCompra_envioExitoso_noRegistraNadaParaElAdmin() {
        Compra compra = Compra.builder()
                .id(42L)
                .codigoReserva("260820-1")
                .contactEmail("cliente@mail.com")
                .montoTotal(BigDecimal.TEN)
                .estado(EstadoCompra.APROBADO)
                .fechaVisita(LocalDate.now())
                .build();
        when(compraRepository.findById(42L)).thenReturn(Optional.of(compra));
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));

        service.enviarComprobanteCompra(42L);

        verifyNoInteractions(rechazoService);
    }

    @Test
    void enviarAvisoRegalo_fallaElEnvio_registraRechazoConElDestinatarioDelRegalo() {
        Compra compra = Compra.builder()
                .id(7L)
                .codigoReserva("REGALO-3")
                .receptorEmail("regalo@mail.com")
                .receptorNombre("Ana")
                .receptorDni("11111111")
                .montoTotal(BigDecimal.TEN)
                .estado(EstadoCompra.APROBADO)
                .fechaVisita(null)
                .build();
        when(compraRepository.findById(7L)).thenReturn(Optional.of(compra));
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
        doThrow(new MailSendException("SMTP caído")).when(mailSender).send(any(MimeMessage.class));

        service.enviarAvisoRegalo(7L);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(rechazoService).registrar(eq("COMPROBANTE_EMAIL"), payloadCaptor.capture(), any(), isNull());

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) payloadCaptor.getValue();
        assertThat(payload).containsEntry("email", "regalo@mail.com");
        assertThat(payload).containsEntry("tipoEmail", "aviso de regalo");
    }

    @Test
    void enviarComprobanteCompra_sinEmailDeContacto_noIntentaEnviarNiRegistrarNada() {
        Compra compra = Compra.builder()
                .id(1L)
                .codigoReserva("REGALO-1")
                .contactEmail(null)
                .montoTotal(BigDecimal.TEN)
                .estado(EstadoCompra.APROBADO)
                .build();
        when(compraRepository.findById(1L)).thenReturn(Optional.of(compra));

        service.enviarComprobanteCompra(1L);

        verifyNoInteractions(rechazoService);
        verifyNoInteractions(mailSender);
    }

    // ---------- Factura ----------

    private org.example.laserranitaentradas.service.factura.ComprobanteFactura comprobante() {
        return new org.example.laserranitaentradas.service.factura.ComprobanteFactura(
                "PARQUE", "RUTA 5", "20409378472", "1", "30/11/2020", 37, 21L, java.time.LocalDate.now(),
                "260930-6", java.util.List.of(), new java.math.BigDecimal("2500"), new java.math.BigDecimal("433.88"),
                "86390938715788", java.time.LocalDate.now().plusDays(10), "https://www.afip.gob.ar/fe/qr/?p=x", 6, null);
    }

    private org.example.laserranitaentradas.model.entity.Factura facturaMail() {
        return org.example.laserranitaentradas.model.entity.Factura.builder()
                .id(5L).email("cliente@mail.com")
                .compra(Compra.builder().id(9L).build())
                .build();
    }

    @Test
    void enviarFactura_mandaElPdfAdjuntoYMarcaElEnvio() throws Exception {
        var factura = facturaMail();
        when(facturaRepository.findById(5L)).thenReturn(Optional.of(factura));
        when(comprobanteFacturaService.armar(5L)).thenReturn(comprobante());
        when(facturaPdfGenerator.generar(any())).thenReturn("%PDF-1.4".getBytes());
        when(mailSender.createMimeMessage()).thenReturn(new jakarta.mail.internet.MimeMessage((jakarta.mail.Session) null));

        service.enviarFactura(5L);

        org.mockito.ArgumentCaptor<jakarta.mail.internet.MimeMessage> captor =
                org.mockito.ArgumentCaptor.forClass(jakarta.mail.internet.MimeMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getSubject()).contains("0037-00000021");
        assertThat(factura.getMailEnviadoEn()).isNotNull();
        verify(rechazoService, never()).registrar(any(), any(), any(), any());
    }

    @Test
    void enviarFactura_fallaElEnvio_registraRechazoFacturaEmail() {
        when(facturaRepository.findById(5L)).thenReturn(Optional.of(facturaMail()));
        when(comprobanteFacturaService.armar(5L)).thenReturn(comprobante());
        when(facturaPdfGenerator.generar(any())).thenReturn("%PDF-1.4".getBytes());
        when(mailSender.createMimeMessage()).thenReturn(new jakarta.mail.internet.MimeMessage((jakarta.mail.Session) null));
        doThrow(new org.springframework.mail.MailSendException("SMTP caído")).when(mailSender).send(any(jakarta.mail.internet.MimeMessage.class));

        service.enviarFactura(5L);

        verify(rechazoService).registrar(eq("FACTURA_EMAIL"), any(), any(), any());
    }
}
