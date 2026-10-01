package org.example.laserranitaentradas.service.factura;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.laserranitaentradas.model.entity.CompraDetalle;
import org.example.laserranitaentradas.model.entity.EstadoFactura;
import org.example.laserranitaentradas.model.entity.Factura;
import org.example.laserranitaentradas.repository.FacturaRepository;
import org.example.laserranitaentradas.service.afip.AfipSdkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final FacturaRepository facturaRepository;
    private final AfipSdkClient afipClient;
    private final ObjectMapper objectMapper;

    @Value("${afip.emisor.razon-social:PARQUE RECREATIVO LA SERRANITA S.A.S.}")
    private String razonSocial;

    @Value("${afip.emisor.domicilio:}")
    private String domicilio;

    @Value("${afip.emisor.ingresos-brutos:}")
    private String ingresosBrutos;

    @Value("${afip.emisor.inicio-actividades:}")
    private String inicioActividades;

    public ComprobanteFacturaService(FacturaRepository facturaRepository, AfipSdkClient afipClient, ObjectMapper objectMapper) {
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
        List<CompraDetalle> detalles = f.getCompra().getDetalles();
        if (detalles != null) {
            for (CompraDetalle d : detalles) {
                items.add(new ComprobanteFactura.Item(d.getCantidad(), descripcion(d)));
            }
        }

        return new ComprobanteFactura(
                razonSocial, domicilio, afipClient.getCuit(), ingresosBrutos, inicioActividades,
                f.getPuntoVenta(), f.getNumero(), f.getFechaEmision(), f.getCompra().getCodigoReserva(),
                items, f.getImporteTotal(), f.getImporteIva(), f.getCae(), f.getCaeVencimiento(),
                qrUrl(f));
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
        if (f.getEstado() != EstadoFactura.EMITIDA || f.getCae() == null) return null;
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("ver", 1);
        datos.put("fecha", f.getFechaEmision().toString());
        datos.put("cuit", Long.parseLong(afipClient.getCuit()));
        datos.put("ptoVta", f.getPuntoVenta());
        datos.put("tipoCmp", f.getTipoComprobante());
        datos.put("nroCmp", f.getNumero());
        datos.put("importe", f.getImporteTotal());
        datos.put("moneda", "PES");
        datos.put("ctz", 1);
        datos.put("tipoDocRec", 99);
        datos.put("nroDocRec", 0);
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
