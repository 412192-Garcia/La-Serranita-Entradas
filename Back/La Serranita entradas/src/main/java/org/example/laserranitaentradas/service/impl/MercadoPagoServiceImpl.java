package org.example.laserranitaentradas.service.impl;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.common.IdentificationRequest;
import com.mercadopago.client.common.PhoneRequest;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceCategoryDescriptorRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferencePayerRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.resources.preference.Preference;
import org.example.laserranitaentradas.model.entity.Cliente;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.CompraDetalle;
import org.example.laserranitaentradas.model.entity.EstadoCompra;
import org.example.laserranitaentradas.model.entity.FormaPago;
import org.example.laserranitaentradas.repository.CompraRepository;
import org.example.laserranitaentradas.service.PagoService;
import org.example.laserranitaentradas.model.dto.PagoResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Service("mercadoPagoService")
public class MercadoPagoServiceImpl implements PagoService {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoServiceImpl.class);

    private static final String CATEGORIA_ITEM = "tickets";
    private static final Pattern DNI_NUMERICO = Pattern.compile("\\d{7,8}");
    private static final PhoneNumberUtil TELEFONOS = PhoneNumberUtil.getInstance();

    @Value("${mercadopago.accessToken}")
    private String accessToken;

    @Value("${mercadopago.notification-url}")
    private String notificationUrl;

    @Value("${mercadopago.frontend-url}")
    private String frontendUrl;

    /** Lo que ve el cliente en el resumen de la tarjeta: si no lo reconoce, desconoce el cargo. */
    @Value("${mercadopago.statement-descriptor:LA SERRANITA}")
    private String statementDescriptor;

    @Value("${compras.checkout-abandonado.horas:3}")
    private int horasCheckoutAbandonado;

    private final CompraRepository compraRepository;

    public MercadoPagoServiceImpl(CompraRepository compraRepository) {
        this.compraRepository = compraRepository;
    }

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
                    items.add(item(compra)
                            .id(String.valueOf(detalle.getTipoEntrada().getId()))
                            .title(detalle.getTipoEntrada().getNombre())
                            .description(detalle.getTipoEntrada().getDescripcion())
                            .quantity(detalle.getCantidad())
                            .unitPrice(detalle.getTipoEntrada().getPrecio())
                            .build());
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
                items.add(item(compra)
                        .id(compra.getCodigoReserva())
                        .title("Entradas " + compra.getCodigoReserva() + " (descuento aplicado)")
                        .quantity(1)
                        .unitPrice(compra.getMontoTotal())
                        .build());
            }

            PreferenceRequest.PreferenceRequestBuilder requestBuilder = PreferenceRequest.builder()
                    .items(items)
                    // El código visible (yyMMdd-N) y no el id: así en el panel de MP aparece lo mismo
                    // que ve el cliente en el mail y lo que busca boletería.
                    .externalReference(compra.getCodigoReserva())
                    .payer(armarPayer(compra))
                    .statementDescriptor(statementDescriptor)
                    // El link vence junto con el barrido de checkouts abandonados: pasado ese plazo
                    // la compra se cancela y libera el cupo, así que no tiene que poder pagarse.
                    .expires(true)
                    .expirationDateTo(OffsetDateTime.now().plusHours(horasCheckoutAbandonado));

            // El navegador del propio cliente vuelve acá al terminar de pagar. A diferencia
            // del webhook, esto no depende de que nuestro servidor sea alcanzable desde afuera
            // (ver Entradas/PagoExitoso: ahí se re-confirma directamente contra Mercado Pago).
            // Mercado Pago rechaza (o directamente ignora) back_urls con "localhost": en dev
            // se sigue dependiendo solo del webhook y de la verificación de respaldo del
            // polling, sin back_urls ni auto_return.
            if (frontendPublico()) {
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
                log.warn("mercadopago.notification-url no está configurada (MP_NOTIFICATION_URL): Mercado Pago no podrá "
                        + "notificar el pago vía webhook y la compra ID {} dependerá de la verificación directa.", compra.getId());
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
            log.error("Mercado Pago rechazó la preferencia de la compra ID {} (HTTP {}): {}", compra.getId(),
                    e.getStatusCode(), e.getApiResponse() != null ? e.getApiResponse().getContent() : "sin detalle");
            throw e;
        }
    }

    /**
     * El teléfono se carga como texto libre ("351 5123456", "0351 15-512-3456", "+54 9 351...")
     * y Mercado Pago lo pide partido en código de área y número. libphonenumber conoce los
     * códigos de área argentinos y el "15"/"9" de los celulares, así que lo parte bien sin
     * obligar al comprador a escribirlo de una forma puntual. Si no es un número argentino
     * válido (sin código de área, del exterior, mal tipeado) no se manda, antes que mandarlo mal.
     */
    static Optional<PhoneRequest> telefonoParaMercadoPago(String telefono) {
        if (telefono == null || telefono.isBlank()) {
            return Optional.empty();
        }
        try {
            Phonenumber.PhoneNumber numero = TELEFONOS.parse(telefono, "AR");
            if (numero.getCountryCode() != 54 || !TELEFONOS.isValidNumber(numero)) {
                return Optional.empty();
            }
            String nacional = TELEFONOS.getNationalSignificantNumber(numero);
            int largoArea = TELEFONOS.getLengthOfNationalDestinationCode(numero);
            if (largoArea <= 0 || largoArea >= nacional.length()) {
                return Optional.empty();
            }
            String area = nacional.substring(0, largoArea);
            // En los celulares la librería incluye el 9 de "celular" adelante del código de área
            // (+54 9 351 ...): MP espera el código de área solo.
            if (TELEFONOS.getNumberType(numero) == PhoneNumberUtil.PhoneNumberType.MOBILE && area.startsWith("9")) {
                area = area.substring(1);
            }
            return Optional.of(PhoneRequest.builder().areaCode(area).number(nacional.substring(largoArea)).build());
        } catch (NumberParseException e) {
            return Optional.empty();
        }
    }

    private boolean frontendPublico() {
        return frontendUrl != null && !frontendUrl.contains("localhost");
    }

    /** Lo común a toda línea: categoría y el día de la visita como fecha del evento. */
    private PreferenceItemRequest.PreferenceItemRequestBuilder item(Compra compra) {
        PreferenceItemRequest.PreferenceItemRequestBuilder item = PreferenceItemRequest.builder()
                .categoryId(CATEGORIA_ITEM)
                .currencyId("ARS");
        if (compra.getFechaVisita() != null) {
            item.categoryDescriptor(PreferenceCategoryDescriptorRequest.builder()
                    .eventDate(compra.getFechaVisita().atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime())
                    .build());
        }
        return item;
    }

    /**
     * Datos del comprador para el motor antifraude de Mercado Pago: una preferencia sin payer
     * le llega anónima y es mucho más propensa a rechazos "por seguridad".
     */
    private PreferencePayerRequest armarPayer(Compra compra) {
        PreferencePayerRequest.PreferencePayerRequestBuilder payer = PreferencePayerRequest.builder()
                .email(compra.getContactEmail());
        telefonoParaMercadoPago(compra.getContactPhone()).ifPresent(payer::phone);
        Cliente cliente = compra.getCliente();
        if (cliente != null) {
            payer.name(cliente.getNombre()).surname(cliente.getApellido());
            // El formulario también acepta pasaportes extranjeros (con letras): sólo se declara
            // como DNI lo que tiene forma de DNI, para no mandarle a MP un documento mal tipado.
            if (cliente.getDni() != null && DNI_NUMERICO.matcher(cliente.getDni()).matches()) {
                payer.identification(IdentificationRequest.builder().type("DNI").number(cliente.getDni()).build());
            }
            // Historial del comprador. is_first_purchase_online es un boolean primitivo en el
            // SDK: sin setearlo se manda false siempre, o sea "ya nos compró antes" para todos.
            if (cliente.getFechaCreacion() != null) {
                payer.registrationDate(cliente.getFechaCreacion().atZone(ZoneId.systemDefault()).toOffsetDateTime());
            }
            Optional<Compra> compraOnlineAnterior = compraRepository
                    .findFirstByClienteIdAndIdNotAndFormaPagoAndEstadoInOrderByFechaCreacionDesc(
                            cliente.getId(), compra.getId(), FormaPago.MERCADO_PAGO,
                            List.of(EstadoCompra.APROBADO, EstadoCompra.USADO));
            payer.isFirstPurchaseOnline(compraOnlineAnterior.isEmpty());
            compraOnlineAnterior.ifPresent(anterior ->
                    payer.lastPurchase(anterior.getFechaCreacion().atZone(ZoneId.systemDefault()).toOffsetDateTime()));
        }
        return payer.build();
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
