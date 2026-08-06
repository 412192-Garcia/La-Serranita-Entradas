package org.example.laserranitaentradas.service.impl;

import org.example.laserranitaentradas.model.dto.CrearPromocionRequest;
import org.example.laserranitaentradas.model.entity.Promocion;
import org.example.laserranitaentradas.repository.PromocionRepository;
import org.example.laserranitaentradas.service.PromocionService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class PromocionServiceImpl implements PromocionService {

    private final PromocionRepository promocionRepository;

    public PromocionServiceImpl(PromocionRepository promocionRepository) {
        this.promocionRepository = promocionRepository;
    }

    @Override
    public List<Promocion> getAll() {
        return promocionRepository.findAll();
    }

    @Override
    public Optional<Promocion> findById(Long id) {
        return promocionRepository.findById(id);
    }

    @Override
    public Promocion create(CrearPromocionRequest request) {
        validar(request);
        Promocion promocion = Promocion.builder()
                .nombre(request.getNombre().trim())
                .porcentajeDescuento(request.getPorcentajeDescuento())
                .montoDescuento(request.getMontoDescuento())
                .activo(true)
                .build();
        return promocionRepository.save(promocion);
    }

    @Override
    public Promocion update(Long id, CrearPromocionRequest request) {
        validar(request);
        Promocion promocion = promocionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Promoción no encontrada para id: " + id));
        promocion.setNombre(request.getNombre().trim());
        promocion.setPorcentajeDescuento(request.getPorcentajeDescuento());
        promocion.setMontoDescuento(request.getMontoDescuento());
        if (request.getActivo() != null) {
            promocion.setActivo(request.getActivo());
        }
        return promocionRepository.save(promocion);
    }

    @Override
    public void delete(Long id) {
        promocionRepository.findById(id).ifPresent(promocion -> {
            promocion.setActivo(false);
            promocionRepository.save(promocion);
        });
    }

    private void validar(CrearPromocionRequest request) {
        if (request.getNombre() == null || request.getNombre().isBlank()) {
            throw new IllegalArgumentException("Indicá el nombre de la promoción");
        }
        boolean tienePorcentaje = request.getPorcentajeDescuento() != null
                && request.getPorcentajeDescuento().compareTo(BigDecimal.ZERO) > 0;
        boolean tieneMonto = request.getMontoDescuento() != null
                && request.getMontoDescuento().compareTo(BigDecimal.ZERO) > 0;
        if (tienePorcentaje == tieneMonto) {
            throw new IllegalArgumentException("Indicá un porcentaje de descuento o un monto fijo (no ambos, no ninguno)");
        }
    }
}
