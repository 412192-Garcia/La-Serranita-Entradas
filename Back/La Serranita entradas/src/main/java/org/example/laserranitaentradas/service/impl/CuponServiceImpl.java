package org.example.laserranitaentradas.service.impl;

import org.example.laserranitaentradas.model.dto.CrearCuponRequest;
import org.example.laserranitaentradas.model.entity.Cupon;
import org.example.laserranitaentradas.repository.CuponRepository;
import org.example.laserranitaentradas.service.CuponService;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CuponServiceImpl implements CuponService {

    private final CuponRepository cuponRepository;
    private final SecureRandom random = new SecureRandom();
    private static final String ALPHANUM = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    public CuponServiceImpl(CuponRepository cuponRepository) {
        this.cuponRepository = cuponRepository;
    }

    @Override
    public Optional<Cupon> getById(Long id) {
        return cuponRepository.findById(id);
    }

    @Override
    public Optional<Cupon> getByCode(String codigo) {
        return cuponRepository.findByCodigo(codigo);
    }

    @Override
    public List<Cupon> getAll() {
        return cuponRepository.findAll();
    }

    @Override
    public List<Cupon> getAllActive() {
        LocalDate hoy = LocalDate.now();
        return cuponRepository.findAll().stream()
                .filter(cupon -> cupon.getActivo() && cupon.getFechaExpiracion().isAfter(hoy))
                .collect(Collectors.toList());
    }

    @Override
    public Cupon update(Cupon cupon) {
        return cuponRepository.save(cupon);
    }

    @Override
    public Cupon create(CrearCuponRequest request) {
        String codigo = request.getCodigo();
        if (codigo == null || codigo.isBlank()) {
            // generar hasta encontrar un codigo unico (probablemente inmediato)
            codigo = generarCodigoAleatorio(10);
            while (cuponRepository.findByCodigo(codigo).isPresent()) {
                codigo = generarCodigoAleatorio(10);
            }
        } else {
            // si viene codigo, asegurar que no exista
            if (cuponRepository.findByCodigo(codigo).isPresent()) {
                throw new IllegalArgumentException("Código de cupón ya existe: " + codigo);
            }
        }

        Cupon cupon = Cupon.builder()
                .codigo(codigo)
                .fechaExpiracion(request.getFechaExpiracion() == null ? LocalDate.now().plusMonths(1) : request.getFechaExpiracion())
                .usosMaximos(request.getUsosMaximos() == null ? 1 : request.getUsosMaximos())
                .porcentajeDescuento(request.getPorcentajeDescuento())
                .montoDescuento(request.getMontoDescuento())
                .activo(true)
                .usosActuales(0)
                .build();

        return cuponRepository.save(cupon);
    }

    private String generarCodigoAleatorio(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUM.charAt(random.nextInt(ALPHANUM.length())));
        }
        return sb.toString();
    }
}
