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
    public ResponseEntity<Map<String, Object>> createPreference(@RequestBody Map<String, Object> body) throws Exception {
        // Validación mínima: debe contener 'items' como lista.
        // El resto de los errores los traduce ManejadorGlobalErrores.
        if (body.get("items") == null) {
            throw new IllegalArgumentException("'items' es obligatorio en el payload de la preferencia");
        }

        return ResponseEntity.ok(preferenceService.createPreference(body));
    }
}
