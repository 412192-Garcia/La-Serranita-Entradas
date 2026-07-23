package org.example.laserranitaentradas.controller;

import com.mercadopago.client.merchantorder.MerchantOrderClient;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.resources.merchantorder.MerchantOrder;
import com.mercadopago.resources.payment.Payment;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.EstadoCompra;
import org.example.laserranitaentradas.service.CompraService;
import org.example.laserranitaentradas.service.EmailService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/pagos")
public class PagoController {

    private final CompraService compraService;
    private final EmailService emailService;

    public PagoController(CompraService compraService, EmailService emailService) {
        this.compraService = compraService;
        this.emailService = emailService;
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
            e.printStackTrace();
            return ResponseEntity.ok().build();
        }
    }

    private void procesarSiAprobado(String estadoPago, String compraIdStr) {
        if (!"approved".equals(estadoPago) || compraIdStr == null) return;

        Long idDeCompra = Long.parseLong(compraIdStr);
        Optional<Compra> compraActual = compraService.findById(idDeCompra);

        // Idempotencia: Mercado Pago puede reenviar la misma notificación varias veces.
        if (compraActual.isEmpty() || compraActual.get().getEstado() == EstadoCompra.APROBADO) {
            return;
        }

        compraService.actualizarEstado(idDeCompra, EstadoCompra.APROBADO);
        emailService.enviarComprobanteCompra(idDeCompra);
        System.out.println("✅ Pago APROBADO confirmado en BD para la compra ID: " + idDeCompra);
    }
}
