package org.example.laserranitaentradas.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.laserranitaentradas.model.dto.IngresoEntradasRequestDTO;
import org.example.laserranitaentradas.model.dto.OperacionRechazadaResponseDTO;
import org.example.laserranitaentradas.model.dto.RetiroCajaRequestDTO;
import org.example.laserranitaentradas.model.dto.VentaPosRequestDTO;
import org.example.laserranitaentradas.model.entity.Caja;
import org.example.laserranitaentradas.model.entity.OperacionRechazada;
import org.example.laserranitaentradas.repository.OperacionRechazadaRepository;
import org.example.laserranitaentradas.service.CajaService;
import org.example.laserranitaentradas.service.CompraService;
import org.example.laserranitaentradas.service.RechazoOperacionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class RechazoOperacionServiceImpl implements RechazoOperacionService {

    private static final Logger log = LoggerFactory.getLogger(RechazoOperacionServiceImpl.class);
    private static final String SIN_CAJA_GUARDADA =
            "Este rechazo no tiene guardado de qué caja se trata (es de antes de esta función): hay que resolverlo a mano.";

    private final OperacionRechazadaRepository repository;
    private final ObjectMapper objectMapper;
    private final CajaService cajaService;
    private final CompraService compraService;

    public RechazoOperacionServiceImpl(OperacionRechazadaRepository repository, ObjectMapper objectMapper,
                                        CajaService cajaService, CompraService compraService) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.cajaService = cajaService;
        this.compraService = compraService;
    }

    @Override
    public void registrar(String tipoOperacion, Object payload, String motivo, String idempotencyKey) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            // No debería pasar (son DTOs simples), pero registrar el rechazo importa más que el
            // payload exacto: mejor un placeholder que perder el registro entero.
            log.warn("No se pudo serializar el payload de una operación rechazada", e);
            payloadJson = "{}";
        }

        OperacionRechazada rechazo = OperacionRechazada.builder()
                .tipoOperacion(tipoOperacion)
                .payload(payloadJson)
                .motivo(motivo)
                .idempotencyKey(idempotencyKey)
                .resuelto(false)
                .build();
        repository.save(rechazo);
    }

    @Override
    public List<OperacionRechazadaResponseDTO> listar(Boolean resuelto) {
        List<OperacionRechazada> rechazos = resuelto == null
                ? repository.findAllByOrderByFechaCreacionDesc()
                : repository.findAllByResueltoOrderByFechaCreacionDesc(resuelto);
        return rechazos.stream().map(this::toDto).toList();
    }

    @Transactional
    @Override
    public OperacionRechazadaResponseDTO resolver(Long id, String nota) {
        OperacionRechazada rechazo = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el rechazo indicado"));
        rechazo.setResuelto(true);
        rechazo.setNotaResolucion(nota != null && !nota.isBlank() ? nota.trim() : null);
        return toDto(repository.save(rechazo));
    }

    @Transactional
    @Override
    public OperacionRechazadaResponseDTO reabrirYReintentar(Long id) {
        return reabrirYReintentarLote(List.of(id)).get(0);
    }

    /** Un rechazo ya parseado a su DTO real, listo para volver a mandarse tal cual — reusar el
     * DTO original (con su mismo idempotencyKey) es lo que hace que cada operación se revalide
     * en vivo (cupo, promo vigente, tipo de entrada, etc.) en vez de forzarse a ciegas, y que un
     * reintento accidental doble no duplique nada. */
    private record OperacionAAplicar(Object datos) {}

    @Transactional
    @Override
    public List<OperacionRechazadaResponseDTO> reabrirYReintentarLote(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("No se indicó ningún rechazo para reintentar");
        }
        List<OperacionRechazada> rechazos = new ArrayList<>(repository.findAllById(ids));
        if (rechazos.size() != ids.size()) {
            throw new IllegalArgumentException("Algún rechazo indicado no existe");
        }
        if (rechazos.stream().anyMatch(OperacionRechazada::isResuelto)) {
            throw new IllegalStateException("Alguno de estos rechazos ya está resuelto");
        }
        // Se aplican en el mismo orden en que se rechazaron (= el orden en que el POS los
        // intentó, ver OperacionesPendientesService.sincronizar), para no invertir operaciones
        // que dependen entre sí (ej. un retiro grande seguido de un aporte que lo compensa, o
        // una venta que agota el cupo antes que otra).
        rechazos.sort(Comparator.comparing(OperacionRechazada::getFechaCreacion));

        Long cajaId = null;
        List<OperacionAAplicar> aAplicar = new ArrayList<>();
        try {
            for (OperacionRechazada rechazo : rechazos) {
                Long cajaIdDeEste;
                Object datos;
                switch (rechazo.getTipoOperacion()) {
                    case "RETIRO_APORTE" -> {
                        RetiroCajaRequestDTO d = objectMapper.readValue(rechazo.getPayload(), RetiroCajaRequestDTO.class);
                        cajaIdDeEste = d.getCajaId();
                        datos = d;
                    }
                    case "INGRESO_ENTRADAS" -> {
                        IngresoEntradasRequestDTO d = objectMapper.readValue(rechazo.getPayload(), IngresoEntradasRequestDTO.class);
                        cajaIdDeEste = d.getCajaId();
                        datos = d;
                    }
                    case "VENTA" -> {
                        VentaPosRequestDTO d = objectMapper.readValue(rechazo.getPayload(), VentaPosRequestDTO.class);
                        cajaIdDeEste = d.getCajaId();
                        datos = d;
                    }
                    default -> throw new IllegalArgumentException("Este tipo de rechazo no se puede reabrir y reintentar así");
                }
                if (cajaIdDeEste == null) throw new IllegalStateException(SIN_CAJA_GUARDADA);
                if (cajaId == null) {
                    cajaId = cajaIdDeEste;
                } else if (!Objects.equals(cajaId, cajaIdDeEste)) {
                    throw new IllegalArgumentException("Los rechazos indicados no son todos de la misma caja");
                }
                aAplicar.add(new OperacionAAplicar(datos));
            }
        } catch (JsonProcessingException e) {
            log.error("No se pudo leer el payload guardado de un rechazo del lote {}", ids, e);
            throw new IllegalStateException("No se pudieron leer los datos guardados de alguno de estos rechazos");
        }

        // El usuario sale de la caja recién reabierta, no de cada rechazo (que no lo guarda): es
        // la misma caja para todos en el lote, así que su dueño es el usuario correcto para
        // volver a mandar cada operación por el camino real de siempre.
        Caja caja = cajaService.reabrir(cajaId);
        Long usuarioId = caja.getUsuario().getId();
        for (OperacionAAplicar op : aAplicar) {
            if (op.datos() instanceof RetiroCajaRequestDTO d) {
                cajaService.registrarRetiro(usuarioId, d.getMonto(), d.getMotivo(), d.getTipo(), d.getIdempotencyKey(), d.getFechaOriginal());
            } else if (op.datos() instanceof IngresoEntradasRequestDTO d) {
                cajaService.registrarIngresoEntradas(usuarioId, d.getCantidad(), d.getMotivo(), d.getTipo(), d.getIdempotencyKey(), d.getFechaOriginal());
            } else if (op.datos() instanceof VentaPosRequestDTO d) {
                compraService.registrarVentaPos(d, usuarioId);
            }
        }
        cajaService.recerrarConElUltimoConteo(cajaId);

        for (OperacionRechazada rechazo : rechazos) {
            rechazo.setResuelto(true);
            rechazo.setNotaResolucion(rechazos.size() > 1
                    ? "Reabierto y reintentado automáticamente junto con otras " + (rechazos.size() - 1) + " operación(es) de la misma caja"
                    : "Reabierto y reintentado automáticamente");
        }
        return repository.saveAll(rechazos).stream().map(this::toDto).toList();
    }

    private OperacionRechazadaResponseDTO toDto(OperacionRechazada r) {
        return OperacionRechazadaResponseDTO.builder()
                .id(r.getId())
                .tipoOperacion(r.getTipoOperacion())
                .payload(r.getPayload())
                .motivo(r.getMotivo())
                .usuario(r.getUsuarioCreacion())
                .fecha(r.getFechaCreacion())
                .resuelto(r.isResuelto())
                .notaResolucion(r.getNotaResolucion())
                .resueltoPor(r.isResuelto() ? r.getUsuarioModificacion() : null)
                .fechaResolucion(r.isResuelto() ? r.getFechaModificacion() : null)
                .build();
    }
}
