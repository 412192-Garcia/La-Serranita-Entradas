package org.example.laserranitaentradas.service.impl;

import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.EstadoCompra;
import org.example.laserranitaentradas.model.entity.EventoPago;
import org.example.laserranitaentradas.model.entity.FormaPago;
import org.example.laserranitaentradas.repository.CompraRepository;
import org.example.laserranitaentradas.repository.EventoPagoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/** Qué hizo el comprador entre que se creó la compra y el pago: para entender por qué no se completan. */
@Service
public class SeguimientoPagoService {

    /** Los pasos que manda la página de compra, con cómo se leen en Reportes. */
    public static final Map<String, String> PASOS = Map.of(
            "VENTANA_ABIERTA", "Se abrió Mercado Pago",
            "VENTANA_BLOQUEADA", "El navegador bloqueó la ventana de pago",
            "ABRIO_TRAS_BLOQUEO", "Tocó \"Abrir Mercado Pago\"",
            "SIGUE_BLOQUEADA", "Tocó \"Abrir Mercado Pago\" y siguió bloqueada",
            "DESISTIO", "Volvió atrás sin pagar",
            "VENTANA_CERRADA", "Cerró Mercado Pago sin pagar",
            "VOLVIO_SIN_PAGAR", "Volvió de Mercado Pago sin pagar");

    /** Tope de pasos por compra: el endpoint es público. */
    private static final int MAX_EVENTOS = 30;

    private final CompraRepository compraRepository;
    private final EventoPagoRepository eventoPagoRepository;

    public SeguimientoPagoService(CompraRepository compraRepository, EventoPagoRepository eventoPagoRepository) {
        this.compraRepository = compraRepository;
        this.eventoPagoRepository = eventoPagoRepository;
    }

    /** Anota un paso. Se ignora en silencio lo que no corresponde (otra compra, ya vieja, tipo raro). */
    @Transactional
    public void registrar(Long compraId, String codigoReserva, String tipo) {
        if (tipo == null || !PASOS.containsKey(tipo)) return;
        Compra compra = compraRepository.findById(compraId).orElse(null);
        if (compra == null || compra.getFormaPago() != FormaPago.MERCADO_PAGO
                || !compra.getCodigoReserva().equals(codigoReserva)
                || compra.getFechaCreacion().isBefore(LocalDateTime.now().minusDays(1))
                || eventoPagoRepository.countByCompraId(compraId) >= MAX_EVENTOS) {
            return;
        }
        eventoPagoRepository.save(EventoPago.builder().compra(compra).tipo(tipo).fecha(LocalDateTime.now()).build());
    }

    /** Desde qué dispositivo y si vino por el sitio (iframe). Se pisa si reintenta desde otro lado. */
    @Transactional
    public void registrarOrigen(Long compraId, String userAgent, Boolean enIframe) {
        compraRepository.findById(compraId).ifPresent(c -> {
            if (c.getEstado() != EstadoCompra.PENDIENTE_PAGO) return;
            c.setDispositivo(clasificar(userAgent));
            c.setEnIframe(enIframe);
        });
    }

    /** "Celular · Android · Instagram": lo que sirve para ver dónde se traba el pago. */
    static String clasificar(String ua) {
        if (ua == null || ua.isBlank()) return "Desconocido";
        String u = ua.toLowerCase();
        String tipo = u.contains("ipad") || (u.contains("android") && !u.contains("mobile")) ? "Tablet"
                : u.contains("mobi") || u.contains("iphone") || u.contains("android") ? "Celular" : "Computadora";
        String sistema = u.contains("iphone") || u.contains("ipad") || u.contains("ios") ? "iOS"
                : u.contains("android") ? "Android"
                : u.contains("windows") ? "Windows"
                : u.contains("mac os") ? "Mac"
                : u.contains("linux") ? "Linux" : "Otro";
        String navegador = u.contains("instagram") ? "Instagram"
                : u.contains("fban") || u.contains("fbav") || u.contains("fb_iab") ? "Facebook"
                : u.contains("tiktok") || u.contains("musical_ly") ? "TikTok"
                : u.contains("whatsapp") ? "WhatsApp"
                : u.contains("samsungbrowser") ? "Samsung Internet"
                : u.contains("edg/") ? "Edge"
                : u.contains("opr/") || u.contains("opera") ? "Opera"
                : u.contains("firefox") || u.contains("fxios") ? "Firefox"
                : u.contains("crios") || u.contains("chrome") ? "Chrome"
                : u.contains("safari") ? "Safari" : "Otro";
        return tipo + " · " + sistema + " · " + navegador;
    }
}
