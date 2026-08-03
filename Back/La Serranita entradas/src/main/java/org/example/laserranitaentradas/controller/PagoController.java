package org.example.laserranitaentradas.controller;

import com.mercadopago.client.merchantorder.MerchantOrderClient;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.resources.merchantorder.MerchantOrder;
import com.mercadopago.resources.payment.Payment;
import jakarta.servlet.http.HttpServletRequest;
import org.example.laserranitaentradas.service.CompraService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

@RestController
@RequestMapping("/api/pagos")
public class PagoController {

    private static final Logger log = LoggerFactory.getLogger(PagoController.class);

    private final CompraService compraService;

    // Sin valor por defecto: si no está seteada, se salta la validación de firma (para
    // no romper el flujo de dev con ngrok, donde nunca se configuró) pero avisando fuerte
    // por log. En producción es obligatoria — sin ella, cualquiera podría pegarle a este
    // endpoint y forzar la revisión de un pago ajeno.
    @Value("${mercadopago.webhook-secret:}")
    private String webhookSecret;

    public PagoController(CompraService compraService) {
        this.compraService = compraService;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> recibirWebhook(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "topic", required = false) String topic,
            @RequestParam(value = "data.id", required = false) String dataId,
            @RequestParam(value = "id", required = false) String id,
            @RequestHeader(value = "x-signature", required = false) String xSignature,
            @RequestHeader(value = "x-request-id", required = false) String xRequestId,
            @RequestBody(required = false) Map<String, Object> payload,
            HttpServletRequest request) {

        String idParaFirma = dataId != null ? dataId : id;
        if (!firmaValida(xSignature, xRequestId, idParaFirma)) {
            log.warn("Notificación de Mercado Pago con firma inválida o faltante, IP {}", request.getRemoteAddr());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            String evento = type != null ? type : topic;
            if (evento == null && payload != null) {
                Object t = payload.get("type") != null ? payload.get("type") : payload.get("topic");
                evento = t != null ? String.valueOf(t) : null;
            }

            if ("payment".equals(evento)) {
                String paymentId = dataId != null ? dataId : id;
                if (paymentId == null && payload != null && payload.containsKey("data")) {
                    Map<String, Object> data = (Map<String, Object>) payload.get("data");
                    paymentId = String.valueOf(data.get("id"));
                }

                if (paymentId != null) {
                    Payment payment = new PaymentClient().get(Long.parseLong(paymentId));
                    procesarSiAprobado(payment.getStatus(), payment.getExternalReference());
                }

            } else if ("merchant_order".equals(evento)) {
                // Mercado Pago no siempre notifica el topic "payment": el merchant_order
                // agrega todos los pagos asociados a la preferencia y es la vía recomendada
                // para no depender de que llegue el webhook de "payment".
                String merchantOrderId = id != null ? id : dataId;
                if (merchantOrderId != null) {
                    MerchantOrder order = new MerchantOrderClient().get(Long.parseLong(merchantOrderId));
                    boolean tieneAlgunPagoAprobado = order.getPayments() != null && order.getPayments().stream()
                            .anyMatch(p -> "approved".equals(p.getStatus()));
                    if (tieneAlgunPagoAprobado) {
                        procesarSiAprobado("approved", order.getExternalReference());
                    }
                }
            }

            return ResponseEntity.ok().build();

        } catch (Exception e) {
            // Se responde 200 igual: Mercado Pago reintenta el webhook si recibe un error,
            // y el reintento volvería a fallar por la misma causa.
            log.error("Error procesando la notificación de Mercado Pago", e);
            return ResponseEntity.ok().build();
        }
    }

    /**
     * Valida el header x-signature según el esquema documentado por Mercado Pago:
     * HMAC-SHA256("id:{data.id};request-id:{x-request-id};ts:{timestamp};", secreto)
     * tiene que coincidir con el "v1" que viene en el header. Sin mercadopago.webhook-secret
     * configurado no hay forma de validar nada, así que se deja pasar (con warning) para
     * no romper el dev local donde nunca se configuró un secreto de firma.
     */
    private boolean firmaValida(String xSignature, String xRequestId, String dataId) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.warn("mercadopago.webhook-secret no configurado: se acepta la notificación SIN validar la firma. " +
                    "Configurá MP_WEBHOOK_SECRET antes de ir a producción.");
            return true;
        }
        if (xSignature == null || xRequestId == null || dataId == null) {
            return false;
        }

        Map<String, String> partes = new HashMap<>();
        for (String parte : xSignature.split(",")) {
            String[] kv = parte.split("=", 2);
            if (kv.length == 2) {
                partes.put(kv[0].trim(), kv[1].trim());
            }
        }
        String ts = partes.get("ts");
        String v1 = partes.get("v1");
        if (ts == null || v1 == null) {
            return false;
        }

        String manifest = "id:" + dataId.toLowerCase() + ";request-id:" + xRequestId + ";ts:" + ts + ";";

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8));
            String calculado = HexFormat.of().formatHex(hash);
            return MessageDigest.isEqual(
                    calculado.getBytes(StandardCharsets.UTF_8),
                    v1.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Error calculando la firma esperada del webhook de Mercado Pago", e);
            return false;
        }
    }

    private void procesarSiAprobado(String estadoPago, String compraIdStr) {
        if (!"approved".equals(estadoPago) || compraIdStr == null) return;

        Long idDeCompra = Long.parseLong(compraIdStr);
        // La idempotencia (Mercado Pago puede reenviar la misma notificación varias
        // veces) y el envío del comprobante viven en el service: es la misma lógica
        // que usa la verificación directa en /api/compras/{id}/verificar-pago.
        if (compraService.confirmarAprobado(idDeCompra)) {
            log.info("Pago APROBADO confirmado en BD para la compra ID {}", idDeCompra);
        }
    }
}
