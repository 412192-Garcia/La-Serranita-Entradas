package org.example.laserranitaentradas.controller;

import org.example.laserranitaentradas.service.PreferenceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/mercadopago")
public class MercadoPagoController {

    private final PreferenceService preferenceService;

    public MercadoPagoController(PreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @PostMapping("/preferences")
    public ResponseEntity<?> createPreference(@RequestBody Map<String, Object> body) {
        try {
            // Validación mínima: debe contener 'items' como lista
            Object itemsObj = body.get("items");
            if (itemsObj == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "'items' es obligatorio en el payload de la preferencia"));
            }

            Map<String, Object> preference = preferenceService.createPreference(body);
            return ResponseEntity.ok(preference);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Error al crear preferencia", "detail", e.getMessage()));
        }
    }
}
