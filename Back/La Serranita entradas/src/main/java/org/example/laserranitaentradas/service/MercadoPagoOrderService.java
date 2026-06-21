//package org.example.laserranitaentradas.service;
//
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.http.HttpHeaders;
//import org.springframework.http.MediaType;
//import org.springframework.stereotype.Service;
//import org.springframework.web.reactive.function.client.WebClient;
//import reactor.core.publisher.Mono;
//
//import java.util.Map;
//
//@Service
//public class MercadoPagoOrderService {
//
//    private final WebClient webClient;
//    private final String accessToken;
//
//    public MercadoPagoOrderService(@Value("${mercadopago.accessToken:}") String accessToken) {
//        this.webClient = WebClient.builder().baseUrl("https://api.mercadopago.com").build();
//        this.accessToken = accessToken;
//    }
//
//    /**
//     * Crea una orden en Mercado Pago usando la API REST directamente.
//     * @param idempotencyKey valor de X-Idempotency-Key
//     * @param body cuerpo de la petición (se enviará tal cual como JSON)
//     * @return la respuesta recibida desde Mercado Pago en forma de Map
//     */
//    public Mono<Map> createOrder(String idempotencyKey, Map<String, Object> body) {
//        WebClient.RequestBodySpec req = webClient.post()
//                .uri("/v1/orders")
//                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
//                .header("X-Idempotency-Key", idempotencyKey)
//                .contentType(MediaType.APPLICATION_JSON);
//
//        return req.bodyValue(body)
//                .retrieve()
//                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), clientResponse ->
//                        clientResponse.bodyToMono(Map.class).flatMap(error -> Mono.error(new RuntimeException("MercadoPago API error: " + error))))
//                .bodyToMono(Map.class);
//    }
//}
//
