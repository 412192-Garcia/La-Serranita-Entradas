package org.example.laserranitaentradas.service.impl;

import org.example.laserranitaentradas.model.dto.CrearFamiliaCuponRequest;
import org.example.laserranitaentradas.model.entity.AplicacionDescuento;
import org.example.laserranitaentradas.model.entity.Cupon;
import org.example.laserranitaentradas.model.entity.FamiliaCupon;
import org.example.laserranitaentradas.repository.CuponRepository;
import org.example.laserranitaentradas.repository.FamiliaCuponRepository;
import org.example.laserranitaentradas.service.FamiliaCuponService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

@Service
public class FamiliaCuponServiceImpl implements FamiliaCuponService {

    private final FamiliaCuponRepository familiaCuponRepository;
    private final CuponRepository cuponRepository;
    private final SecureRandom random = new SecureRandom();
    private static final String ALPHANUM = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    public FamiliaCuponServiceImpl(FamiliaCuponRepository familiaCuponRepository, CuponRepository cuponRepository) {
        this.familiaCuponRepository = familiaCuponRepository;
        this.cuponRepository = cuponRepository;
    }

    @Override
    public Optional<FamiliaCupon> getById(Long id) {
        return familiaCuponRepository.findById(id);
    }

    @Override
    public List<FamiliaCupon> getAll() {
        return familiaCuponRepository.findAll();
    }

    @Override
    public FamiliaCupon create(CrearFamiliaCuponRequest request) {
        CuponDescuentoCalculator.validarReglas(request.getPorcentajeDescuento(), request.getMontoDescuento(),
                request.getAplicaPor(), request.getMinEntradas(), request.getMaxEntradasAfectadas(),
                request.getTopeDescuento(), request.getFechaDesde(), request.getFechaExpiracion());

        int cantidad = (request.getCantidad() == null || request.getCantidad() < 1) ? 1 : request.getCantidad();
        if (request.getUsosMaximos() != null && request.getUsosMaximos() < 1) {
            throw new IllegalArgumentException("Los usos máximos tienen que ser 1 o más (o dejarlo vacío para no limitarlos).");
        }
        // Opcionales: vacío es sin límite de usos / sin vencimiento.
        Integer usos = request.getUsosMaximos();
        LocalDate fechaExp = request.getFechaExpiracion();

        FamiliaCupon familia = FamiliaCupon.builder()
                .nombre(request.getNombre())
                .prefijo(request.getPrefijo())
                .descripcion(request.getDescripcion())
                .build();

        List<Cupon> cupones = new ArrayList<>();
        String pref = (request.getPrefijo() == null) ? "" : request.getPrefijo();
        for (int i = 0; i < cantidad; i++) {
            String codigo = generarCodigoConPrefijo(pref);
            while (cuponRepository.findByCodigo(codigo).isPresent()) {
                codigo = generarCodigoConPrefijo(pref);
            }
            Cupon cupon = Cupon.builder()
                    .codigo(codigo)
                    .fechaExpiracion(fechaExp)
                    .usosMaximos(usos)
                    .porcentajeDescuento(request.getPorcentajeDescuento())
                    .montoDescuento(request.getMontoDescuento())
                    .activo(true)
                    .usosActuales(0)
                    .fechaDesde(request.getFechaDesde())
                    .aplicaPor(request.getAplicaPor() == null ? AplicacionDescuento.COMPRA : request.getAplicaPor())
                    .tiposEntradaIds(request.getTiposEntradaIds() == null ? new HashSet<>() : new HashSet<>(request.getTiposEntradaIds()))
                    .minEntradas(request.getMinEntradas())
                    .maxEntradasAfectadas(request.getMaxEntradasAfectadas())
                    .topeDescuento(request.getTopeDescuento())
                    .build();
            cupon.setFamiliaCupon(familia);
            cupones.add(cupon);
        }
        familia.setCupones(cupones);
        return familiaCuponRepository.save(familia);
    }

    @Override
    @Transactional
    public FamiliaCupon cambiarActivo(Long id, boolean activo) {
        FamiliaCupon familia = familiaCuponRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Lote de cupones no encontrado ID: " + id));
        if (activo) {
            cuponRepository.activarFamilia(familia.getId(), LocalDate.now());
        } else {
            cuponRepository.desactivarFamilia(familia.getId());
        }
        return familiaCuponRepository.findById(id).orElseThrow();
    }

    @Override
    @Transactional
    public FamiliaCupon cambiarVencimiento(Long id, LocalDate fechaExpiracion) {
        FamiliaCupon familia = familiaCuponRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Lote de cupones no encontrado ID: " + id));
        cuponRepository.cambiarVencimientoFamilia(familia.getId(), fechaExpiracion);
        return familiaCuponRepository.findById(id).orElseThrow();
    }

    private String generarCodigoAleatorio(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUM.charAt(random.nextInt(ALPHANUM.length())));
        }
        return sb.toString();
    }

    private String generarCodigoConPrefijo(String pref) {
        String randomPart = generarCodigoAleatorio(6);
        if (pref == null || pref.isBlank()) {
            return randomPart;
        }
        return pref + "-" + randomPart;
    }
}
