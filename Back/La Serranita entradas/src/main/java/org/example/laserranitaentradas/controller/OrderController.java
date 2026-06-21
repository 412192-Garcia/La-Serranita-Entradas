//package org.example.laserranitaentradas.controller;
//
//import org.example.laserranitaentradas.service.MercadoPagoOrderService;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.util.StringUtils;
//import org.springframework.web.bind.annotation.*;
//import reactor.core.publisher.Mono;
//
//import java.util.Map;
//
//@RestController
//@RequestMapping
//public class OrderController {
//
//    private final MercadoPagoOrderService mpService;
//
//    public OrderController(MercadoPagoOrderService mpService) {
//        this.mpService = mpService;
//    }
//
//    @PostMapping(path = "/v1/orders", consumes = "application/json", produces = "application/json")
//    public Mono<ResponseEntity<?>> createOrder(
//            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
//            @RequestBody Map<String, Object> body
//    ) {
//        // Validaciones simples del header
//        if (!StringUtils.hasText(idempotencyKey)) {
//            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "empty_required_header", "message", "El header X-Idempotency-Key es requerido.")));
//        }
//        if (idempotencyKey.length() > 150) {
//            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "invalid_idempotency_key_length", "message", "El header X-Idempotency-Key excede la longitud permitida (150).")));
//        }
//
//        return mpService.createOrder(idempotencyKey, body)
//                .map(resp -> ResponseEntity.status(HttpStatus.CREATED).body(resp))
//                .onErrorResume(ex -> Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "internal_error", "message", ex.getMessage()))));
//    }
//}
//
