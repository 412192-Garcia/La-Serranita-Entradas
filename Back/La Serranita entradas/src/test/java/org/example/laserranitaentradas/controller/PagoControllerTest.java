package org.example.laserranitaentradas.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.laserranitaentradas.service.CompraService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cubre la validación de firma del webhook de Mercado Pago (PagoController.firmaValida):
 * es el único portón entre "cualquiera en internet" y confirmar un pago como aprobado, así
 * que un bug acá (aceptar una firma inválida, o el cálculo del HMAC mal armado) es directamente
 * plata regalada. No cubre las ramas "payment"/"merchant_order" en sí porque instancian el SDK
 * de Mercado Pago (new PaymentClient()/new MerchantOrderClient()) sin inyección, así que llamarlas
 * en test pegaría contra la red real — quedan fuera de alcance de un test unitario.
 */
@ExtendWith(MockitoExtension.class)
class PagoControllerTest {

    private static final String SECRETO = "secreto-de-test";

    @Mock private CompraService compraService;
    @Mock private HttpServletRequest request;

    private PagoController controller;

    @BeforeEach
    void setUp() {
        controller = new PagoController(compraService);
    }

    private void configurarSecreto(String secreto) {
        ReflectionTestUtils.setField(controller, "webhookSecret", secreto);
    }

    /** HMAC-SHA256("id:{dataId};request-id:{requestId};ts:{ts};", secreto), tal cual lo espera el controller. */
    private String firmarComoMercadoPago(String dataId, String requestId, String ts, String secreto) throws Exception {
        String manifest = "id:" + dataId.toLowerCase() + ";request-id:" + requestId + ";ts:" + ts + ";";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void sinSecretoConfigurado_aceptaSinValidarFirma() {
        configurarSecreto("");

        ResponseEntity<Void> respuesta = controller.recibirWebhook(
                null, null, null, null, null, null, null, request);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(compraService, never()).confirmarAprobado(anyLong());
    }

    @Test
    void conSecretoYSinHeaderDeFirma_rechaza401() {
        configurarSecreto(SECRETO);

        ResponseEntity<Void> respuesta = controller.recibirWebhook(
                "payment", null, "123456789", null, null, "req-1", null, request);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(compraService, never()).confirmarAprobado(anyLong());
    }

    @Test
    void conSecretoYFirmaConValorIncorrecto_rechaza401() {
        configurarSecreto(SECRETO);
        String xSignature = "ts=1700000000,v1=" + "0".repeat(64);

        ResponseEntity<Void> respuesta = controller.recibirWebhook(
                "payment", null, "123456789", null, xSignature, "req-1", null, request);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(compraService, never()).confirmarAprobado(anyLong());
    }

    @Test
    void conSecretoYFirmaCalculadaConOtroSecreto_rechaza401() throws Exception {
        configurarSecreto(SECRETO);
        String firmaConSecretoAjeno = firmarComoMercadoPago("123456789", "req-1", "1700000000", "secreto-atacante");
        String xSignature = "ts=1700000000,v1=" + firmaConSecretoAjeno;

        ResponseEntity<Void> respuesta = controller.recibirWebhook(
                "payment", null, "123456789", null, xSignature, "req-1", null, request);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(compraService, never()).confirmarAprobado(anyLong());
    }

    @Test
    void conFirmaValidaPeroSinDataIdNiEvento_aceptaSinLlamarACompraService() throws Exception {
        // Cubre la firma correcta sin arrastrar la rama "payment" (que pegaría contra la red
        // real de Mercado Pago): sin data.id ni type/topic reconocido, el controller no tiene
        // nada que procesar y devuelve 200 sin tocar compraService.
        configurarSecreto(SECRETO);
        String dataId = "123456789";
        String firmaValida = firmarComoMercadoPago(dataId, "req-1", "1700000000", SECRETO);
        String xSignature = "ts=1700000000,v1=" + firmaValida;

        ResponseEntity<Void> respuesta = controller.recibirWebhook(
                null, null, dataId, null, xSignature, "req-1", null, request);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(compraService, never()).confirmarAprobado(anyLong());
    }

    @Test
    void firmaValida_esInsensibleAMayusculasEnElDataId() throws Exception {
        // El controller normaliza el data.id a minúsculas antes de armar el manifest
        // (dataId.toLowerCase()): la firma tiene que calcularse igual sobre el id en
        // minúsculas para que un data.id con mayúsculas no rompa la validación.
        configurarSecreto(SECRETO);
        String dataIdOriginal = "ABC123";
        String firmaValida = firmarComoMercadoPago(dataIdOriginal.toLowerCase(), "req-1", "1700000000", SECRETO);
        String xSignature = "ts=1700000000,v1=" + firmaValida;

        ResponseEntity<Void> respuesta = controller.recibirWebhook(
                null, null, dataIdOriginal, null, xSignature, "req-1", null, request);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void conXSignatureMalformadoSinV1_rechaza401() {
        configurarSecreto(SECRETO);
        String xSignature = "ts=1700000000";

        ResponseEntity<Void> respuesta = controller.recibirWebhook(
                "payment", null, "123456789", null, xSignature, "req-1", null, request);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void conRequestIdDistintoAlFirmado_rechaza401() throws Exception {
        // El request-id es parte del manifest firmado: si alguien reusa una firma válida
        // de OTRA notificación pero con un x-request-id distinto, tiene que rechazarse.
        configurarSecreto(SECRETO);
        String firmaParaOtroRequestId = firmarComoMercadoPago("123456789", "req-original", "1700000000", SECRETO);
        String xSignature = "ts=1700000000,v1=" + firmaParaOtroRequestId;

        ResponseEntity<Void> respuesta = controller.recibirWebhook(
                "payment", null, "123456789", null, xSignature, "req-distinto", null, request);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(compraService, never()).confirmarAprobado(anyLong());
    }

    @Test
    void usaIdDelBodySiDataIdDeQueryFaltaYPayloadTraeData() {
        // Si Mercado Pago no manda data.id como query param pero sí un body con
        // {"data": {"id": ...}}, el controller tiene que resolverlo igual antes de validar
        // la firma (que en este test se salta al no haber secreto, para aislar sólo esa
        // resolución del id, no la validación de firma que ya está cubierta arriba).
        configurarSecreto("");
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("id", "999");
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("type", "algo-no-manejado");
        payload.put("data", data);

        ResponseEntity<Void> respuesta = controller.recibirWebhook(
                null, null, null, null, null, null, payload, request);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(compraService, never()).confirmarAprobado(anyLong());
    }
}
