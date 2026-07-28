package org.example.laserranitaentradas.controller;

import com.mercadopago.client.merchantorder.MerchantOrderClient;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.resources.merchantorder.MerchantOrder;
import com.mercadopago.resources.payment.Payment;
import org.example.laserranitaentradas.service.CompraService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/pagos")
public class PagoController {

    private static final Logger log = LoggerFactory.getLogger(PagoController.class);

    private final CompraService compraService;

    public PagoController(CompraService compraService) {
        this.compraService = compraService;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> recibirWebhook(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "topic", required = false) String topic,
            @RequestParam(value = "data.id", required = false) String dataId,
            @RequestParam(value = "id", required = false) String id,
            @RequestBody(required = false) Map<String, Object> payload) {

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
