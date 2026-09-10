package org.example.laserranitaentradas.service.impl;

import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service("mercadoPagoService")
public class MercadoPagoServiceImpl implements PagoService {

    @Value("${mercadopago.accessToken}")
    private String accessToken;

    @Value("${mercadopago.notification-url}")
    private String notificationUrl;

    @Value("${mercadopago.frontend-url}")
    private String frontendUrl;

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

            // Lo que cobra Mercado Pago tiene que ser exactamente compra.getMontoTotal(): es el
            // total que el cliente vio en pantalla, el que se guarda como recaudación y el que
            // usa la verificación de respaldo para reconocer el pago (compara el monto). Los
            // items de arriba van a precio de lista, así que cuando hubo descuento por cupón no
            // coinciden. Mercado Pago no admite un descuento a nivel de orden ni un item en
            // negativo: la forma de que el total cierre es mandar una sola línea por el importe
            // final. Se pierde el desglose por tipo de entrada en el checkout, que es cosmético,
            // a cambio de que nadie pague de más.
            BigDecimal descuento = compra.getDescuentoAplicado();
            if (descuento != null && descuento.compareTo(BigDecimal.ZERO) > 0) {
                items.clear();
                items.add(PreferenceItemRequest.builder()
                        .id(compra.getCodigoReserva())
                        .title("Entradas " + compra.getCodigoReserva() + " (descuento aplicado)")
                        .quantity(1)
                        .unitPrice(compra.getMontoTotal())
                        .currencyId("ARS")
                        .build());
            }

            PreferenceRequest.PreferenceRequestBuilder requestBuilder = PreferenceRequest.builder()
                    .items(items)
                    .externalReference(String.valueOf(compra.getId())); // Impactamos el ID auto-incremental generado

            // El navegador del propio cliente vuelve acá al terminar de pagar. A diferencia
            // del webhook, esto no depende de que nuestro servidor sea alcanzable desde afuera
            // (ver Entradas/PagoExitoso: ahí se re-confirma directamente contra Mercado Pago).
            // Mercado Pago rechaza (o directamente ignora) back_urls con "localhost": en dev
            // se sigue dependiendo solo del webhook y de la verificación de respaldo del
            // polling, sin back_urls ni auto_return.
            if (frontendUrl != null && !frontendUrl.contains("localhost")) {
                requestBuilder.backUrls(PreferenceBackUrlsRequest.builder()
                                .success(frontendUrl + "/pago-exitoso")
                                .pending(frontendUrl + "/pago-exitoso")
                                .failure(frontendUrl + "/pago-fallido")
                                .build())
                        .autoReturn("approved");
            }

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
