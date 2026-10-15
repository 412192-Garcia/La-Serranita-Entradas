package org.example.laserranitaentradas.service.factura;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.laserranitaentradas.model.entity.CompraDetalle;
import org.example.laserranitaentradas.model.entity.EstadoCompra;
import org.example.laserranitaentradas.model.entity.Compra;
import org.example.laserranitaentradas.model.entity.EstadoFactura;
import org.example.laserranitaentradas.model.entity.Factura;
import org.example.laserranitaentradas.model.entity.Usuario;
import org.example.laserranitaentradas.repository.FacturaRepository;
import org.example.laserranitaentradas.service.afip.ArcaClient;
import org.example.laserranitaentradas.service.afip.WsfeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Arma el {@link ComprobanteFactura} de una factura emitida, con los datos del emisor y el QR. */
@Service
public class ComprobanteFacturaService {

    private static final Logger log = LoggerFactory.getLogger(ComprobanteFacturaService.class);
    private static final String URL_QR_ARCA = "https://www.afip.gob.ar/fe/qr/?p=";
    private static final BigDecimal DIVISOR_IVA_21 = new BigDecimal("1.21");

    private final FacturaRepository facturaRepository;
    private final ArcaClient afipClient;
    private final ObjectMapper objectMapper;

    @Value("${afip.emisor.razon-social:PARQUE RECREATIVO LA SERRANITA S.A.S.}")
    private String razonSocial;

    @Value("${afip.emisor.domicilio:}")
    private String domicilio;

    @Value("${afip.emisor.ingresos-brutos:}")
    private String ingresosBrutos;

    @Value("${afip.emisor.inicio-actividades:}")
    private String inicioActividades;

    public ComprobanteFacturaService(FacturaRepository facturaRepository, ArcaClient afipClient, ObjectMapper objectMapper) {
        this.facturaRepository = facturaRepository;
        this.afipClient = afipClient;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ComprobanteFactura armar(Long facturaId) {
        Factura f = facturaRepository.findById(facturaId)
                .orElseThrow(() -> new IllegalArgumentException("Factura no encontrada ID: " + facturaId));
        if (f.getEstado() != EstadoFactura.EMITIDA) {
            throw new IllegalStateException("La factura todavía no está emitida");
        }

        List<ComprobanteFactura.Item> items = new ArrayList<>();
        if (f.getDetalle() != null && !f.getDetalle().isBlank()) {
            // Lo que se facturó, aunque la venta se haya editado después.
            for (String linea : f.getDetalle().split("\n")) {
                // "cantidad<TAB>descripción[<TAB>subtotal]": las facturas viejas no tienen subtotal.
                String[] partes = linea.split("\t", 3);
                if (partes.length < 2) continue;
                BigDecimal subtotal = partes.length == 3 && !partes[2].isBlank() ? new BigDecimal(partes[2]) : null;
                items.add(new ComprobanteFactura.Item(Integer.parseInt(partes[0]), partes[1], subtotal));
            }
        } else if (f.getCompra() != null && f.getCompra().getDetalles() != null) {
            for (CompraDetalle d : f.getCompra().getDetalles()) {
                items.add(new ComprobanteFactura.Item(d.getCantidad(), descripcion(d)));
            }
        }

        String asociado = null;
        if (f.getComprobanteAsociado() != null) {
            Factura a = f.getComprobanteAsociado();
            asociado = String.format("%s %04d-%08d", WsfeService.nombre(a.getTipoComprobante()), a.getPuntoVenta(), a.getNumero());
        }

        ComprobanteFactura.Receptor receptor = null;
        if (f.getReceptorCuit() != null) {
            receptor = new ComprobanteFactura.Receptor(f.getReceptorRazonSocial(), f.getReceptorCuit(), f.getReceptorDomicilio(),
                    WsfeService.CONDICIONES_IVA_FACTURA_A.getOrDefault(f.getReceptorCondicionIva(), ""));
            items = itemsSinIva(items, f.getImporteNeto());
        }

        return new ComprobanteFactura(
                razonSocial, domicilio, afipClient.getCuit(), ingresosBrutos, inicioActividades,
                f.getPuntoVenta(), f.getNumero(), f.getFechaEmision(), f.getCompra() != null ? f.getCompra().getCodigoReserva() : null,
                items, f.getImporteTotal(), f.getImporteIva(), f.getCae(), f.getCaeVencimiento(),
                qrUrl(f), f.getTipoComprobante(), asociado,
                f.getEmitidaEn() != null ? f.getEmitidaEn().toLocalTime() : null,
                f.getCompra() != null && esVentaDePuerta(f.getCompra()) ? "Venta" : "Reserva",
                f.getCompra() != null ? boletero(f.getCompra()) : null,
                receptor);
    }

    /**
     * En una Factura A el IVA va discriminado: cada renglón se muestra sin IVA. El redondeo de cada
     * renglón puede no sumar justo el neto de la factura; la diferencia (centavos) va al renglón más
     * grande, para que los renglones cierren con el "Subtotal neto gravado".
     */
    private static List<ComprobanteFactura.Item> itemsSinIva(List<ComprobanteFactura.Item> items, BigDecimal neto) {
        if (items.isEmpty() || items.stream().anyMatch(i -> i.subtotal() == null)) return items;
        List<ComprobanteFactura.Item> salida = new ArrayList<>();
        BigDecimal suma = BigDecimal.ZERO;
        int mayor = 0;
        for (int i = 0; i < items.size(); i++) {
            ComprobanteFactura.Item item = items.get(i);
            BigDecimal sinIva = item.subtotal().divide(DIVISOR_IVA_21, 2, RoundingMode.HALF_UP);
            suma = suma.add(sinIva);
            if (sinIva.compareTo(salida.isEmpty() ? BigDecimal.ZERO : salida.get(mayor).subtotal()) > 0) mayor = i;
            salida.add(new ComprobanteFactura.Item(item.cantidad(), item.descripcion(), sinIva));
        }
        BigDecimal diferencia = neto.subtract(suma);
        if (diferencia.signum() != 0) {
            ComprobanteFactura.Item m = salida.get(mayor);
            salida.set(mayor, new ComprobanteFactura.Item(m.cantidad(), m.descripcion(), m.subtotal().add(diferencia)));
        }
        return salida;
    }

    /** "Nombre Apellido" del dueño de la caja en la que se cobró; null si no pasó por una caja. */
    private static String boletero(Compra c) {
        if (c.getCaja() == null || c.getCaja().getUsuario() == null) return null;
        Usuario u = c.getCaja().getUsuario();
        String nombre = ((u.getNombre() != null ? u.getNombre() : "") + " "
                + (u.getApellido() != null ? u.getApellido() : "")).trim();
        return nombre.isEmpty() ? u.getUsername() : nombre;
    }

    /**
     * Venta hecha directo en la boletería (nunca fue una reserva): queda VENDIDO_EN_PUERTA y no
     * tiene titular. Lo del titular cubre también la nota de crédito de una venta de puerta ya
     * cancelada (deja de estar VENDIDO_EN_PUERTA). Una reserva cobrada en puerta tiene titular.
     */
    private static boolean esVentaDePuerta(Compra c) {
        return c.getEstado() == EstadoCompra.VENDIDO_EN_PUERTA || (c.getCliente() == null && c.getCaja() != null);
    }

    private static String descripcion(CompraDetalle d) {
        if (d.getTipoEntrada() != null) return d.getTipoEntrada().getNombre();
        if (d.getArticuloVario() != null) return d.getArticuloVario().getNombre();
        return d.getDescripcionLibre() != null ? d.getDescripcionLibre() : "Artículo";
    }

    /**
     * QR obligatorio de los comprobantes electrónicos (RG 4892): la URL de ARCA con los datos del
     * comprobante en JSON y base64. Escanearlo lleva a la consulta de validez del comprobante.
     */
    public String qrUrl(Factura f) {
        // Sin CUIT = la facturación ya no está configurada (ej. una base de prueba sin el certificado).
        if (f.getEstado() != EstadoFactura.EMITIDA || f.getCae() == null || afipClient.getCuit() == null) return null;
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("ver", 1);
        datos.put("fecha", f.getFechaEmision().toString());
        datos.put("cuit", Long.parseLong(afipClient.getCuit()));
        datos.put("ptoVta", f.getPuntoVenta());
        datos.put("tipoCmp", f.getTipoComprobante());
        datos.put("nroCmp", f.getNumero());
        // Sin ceros decimales de más ("34300" y no "34300.00"): cada carácter agranda el QR.
        datos.put("importe", new java.math.BigDecimal(f.getImporteTotal().stripTrailingZeros().toPlainString()));
        datos.put("moneda", "PES");
        datos.put("ctz", 1);
        // tipoDocRec/nroDocRec son opcionales en la especificación de ARCA y no se informan para un
        // consumidor final sin identificar (99/0). Omitirlos achica el QR de 61x61 a 57x57
        // cuadraditos: el contenido es lo que lo hace tan denso y difícil de leer en papel térmico.
        // En una Factura A el receptor está identificado: ahí sí se informa su CUIT.
        if (f.getReceptorCuit() != null) {
            datos.put("tipoDocRec", 80);
            datos.put("nroDocRec", Long.parseLong(f.getReceptorCuit()));
        }
        datos.put("tipoCodAut", "E");
        datos.put("codAut", Long.parseLong(f.getCae()));
        try {
            String json = objectMapper.writeValueAsString(datos);
            return URL_QR_ARCA + Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("No se pudo armar el QR de la factura ID {}", f.getId(), e);
            return null;
        }
    }
}
