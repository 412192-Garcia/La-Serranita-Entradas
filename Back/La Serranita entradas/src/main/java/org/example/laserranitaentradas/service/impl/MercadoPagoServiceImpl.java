package org.example.laserranitaentradas.service.impl;

import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.resources.preference.Preference;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.CompraDetalle;
import org.example.laserranitaentradas.model.entity.EstadoCompra;
import org.example.laserranitaentradas.model.entity.FormaPago;
import org.example.laserranitaentradas.service.PagoService;
import org.example.laserranitaentradas.model.dto.PagoResponseDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

@Service("mercadoPagoService")
public class MercadoPagoServiceImpl implements PagoService {

    @Value("${mercadopago.accessToken}")
    private String accessToken;

    @Value("${mercadopago.notification-url}")
    private String notificationUrl;

    @PostConstruct
    public void init() {
        MercadoPagoConfig.setAccessToken(accessToken);
    }

    @Override
    public PagoResponseDTO procesarPago(Compra compra) throws Exception {
        try {
            List<PreferenceItemRequest> items = new ArrayList<>();

            if (compra.getDetalles() != null) {
                for (CompraDetalle detalle : compra.getDetalles()) {
                    PreferenceItemRequest itemRequest = PreferenceItemRequest.builder()
                            .id(String.valueOf(detalle.getTipoEntrada().getId()))
                            .title(detalle.getTipoEntrada().getNombre())
                            .quantity(detalle.getCantidad())
                            .unitPrice(detalle.getTipoEntrada().getPrecio())
                            .currencyId("ARS")
                            .build();
                    items.add(itemRequest);
                }
            }

            PreferenceRequest.PreferenceRequestBuilder requestBuilder = PreferenceRequest.builder()
                    .items(items)
                    .externalReference(String.valueOf(compra.getId())); // Impactamos el ID auto-incremental generado

            if (notificationUrl != null && !notificationUrl.isBlank()) {
                requestBuilder.notificationUrl(notificationUrl + "/api/pagos/webhook");
            } else {
                System.err.println("ADVERTENCIA: mercadopago.notification-url no está configurada (MP_NOTIFICATION_URL). " +
                        "Mercado Pago no podrá notificar el pago vía webhook y la compra ID " + compra.getId() + " quedará en PENDIENTE_PAGO.");
            }

            PreferenceRequest preferenceRequest = requestBuilder.build();

            PreferenceClient client = new PreferenceClient();
            Preference preference = client.create(preferenceRequest);

            return PagoResponseDTO.builder()
                    .exitoso(true)
                    .compraId(compra.getId())
                    .preferenceId(preference.getId())
                    .initPoint(preference.getInitPoint())
                    .mensaje("Preferencia de Mercado Pago generada correctamente.")
                    .build();}
        catch (MPApiException e) {
            System.err.println("=== ERROR DE MERCADO PAGO API ===");
            System.err.println("Status code: " + e.getStatusCode());
            System.err.println("Response API: " + e.getApiResponse().getContent());
            throw e;
        }
    }

    @Override
    public FormaPago getFormaPago() {
        return FormaPago.MERCADO_PAGO;
    }

    @Override
    public EstadoCompra getEstadoInicial() {
        return EstadoCompra.PENDIENTE_PAGO;
    }
}
