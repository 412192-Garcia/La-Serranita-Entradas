package org.example.laserranitaentradas.service;

import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Service
public class PreferenceService {

    private final PreferenceClient client;

    public PreferenceService() {
        // PreferenceClient utiliza la configuración global establecida en MercadoPagoConfig (setAccessToken)
        this.client = new PreferenceClient();
    }

    /**
     * Crea una preferencia usando el SDK oficial.
     * Acepta un payload que puede contener 'items' (lista), 'back_urls' (map) y 'auto_return'.
     * Retorna un mapa con id y el objeto resource devuelto por el SDK.
     */
    public Map<String, Object> createPreference(Map<String, Object> preferencePayload) {
        List<PreferenceItemRequest> itemRequests = new ArrayList<>();

        Object itemsObj = preferencePayload.get("items");
        if (itemsObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) itemsObj;
            for (Map<String, Object> it : items) {
                String title = (String) it.getOrDefault("title", "item");
                Number qty = (Number) it.getOrDefault("quantity", 1);
                Number unitPrice = (Number) it.getOrDefault("unit_price", 0);

                PreferenceItemRequest itemReq = PreferenceItemRequest.builder()
                        .title(title)
                        .quantity(qty.intValue())
                        .unitPrice(new BigDecimal(unitPrice.toString()))
                        .build();
                itemRequests.add(itemReq);
            }
        }


        var builder = PreferenceRequest.builder();
        if (!itemRequests.isEmpty()) {
            builder.items(itemRequests);
        }

        // back_urls
        Object backUrlsObj = preferencePayload.get("back_urls");
        if (backUrlsObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, String> backUrlsMap = (Map<String, String>) backUrlsObj;
            PreferenceBackUrlsRequest backUrls = PreferenceBackUrlsRequest.builder()
                    .success(backUrlsMap.get("success"))
                    .pending(backUrlsMap.get("pending"))
                    .failure(backUrlsMap.get("failure"))
                    .build();
            builder.backUrls(backUrls);
        }

        Object autoReturn = preferencePayload.get("auto_return");
        if (autoReturn instanceof String) {
            builder.autoReturn((String) autoReturn);
        }

        PreferenceRequest request = builder.build();

        Object response;
        try {
            response = client.create(request);
        } catch (MPApiException | MPException e) {
            // envolver en runtime exception para no propagar excepciones de la librería en la API
            throw new RuntimeException("Error creando preferencia en MercadoPago", e);
        }

        String id = null;
        try {
            Method m = response.getClass().getMethod("getId");
            Object idObj = m.invoke(response);
            if (idObj != null) id = idObj.toString();
        } catch (Exception e) {
            // Si no se puede extraer el id por reflexión, lo dejamos como null pero devolvemos el resource
        }

        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        result.put("resource", response);
        return result;
    }
}
