package org.example.laserranitaentradas.service.impl;

import org.example.laserranitaentradas.model.dto.CrearFamiliaCuponRequest;
import org.example.laserranitaentradas.model.entity.Cupon;
import org.example.laserranitaentradas.model.entity.FamiliaCupon;
import org.example.laserranitaentradas.repository.CuponRepository;
import org.example.laserranitaentradas.repository.FamiliaCuponRepository;
import org.example.laserranitaentradas.service.FamiliaCuponService;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.ArrayList;
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
        int cantidad = (request.getCantidad() == null || request.getCantidad() < 1) ? 1 : request.getCantidad();
        int usos = (request.getUsosMaximos() == null || request.getUsosMaximos() < 1) ? 1 : request.getUsosMaximos();
        LocalDate fechaExp = (request.getFechaExpiracion() == null) ? LocalDate.now().plusMonths(1) : request.getFechaExpiracion();

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
                    .build();
            cupon.setFamiliaCupon(familia);
            cupones.add(cupon);
        }
        familia.setCupones(cupones);
        return familiaCuponRepository.save(familia);
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
