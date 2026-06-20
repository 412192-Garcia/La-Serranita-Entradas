package org.example.laserranitaentradas.service.impl;

import org.example.laserranitaentradas.model.entity.Cupon;
import org.example.laserranitaentradas.repository.CuponRepository;
import org.example.laserranitaentradas.service.CuponService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CuponServiceImpl implements CuponService {

    private final CuponRepository cuponRepository;

    public CuponServiceImpl(CuponRepository cuponRepository) {
        this.cuponRepository = cuponRepository;
    }

    @Override
    public Cupon crearCupon(Cupon cupon) {
        return cuponRepository.save(cupon);
    }

    @Override
    public Optional<Cupon> obtenerCuponPorId(Long id) {
        return cuponRepository.findById(id);
    }

    @Override
    public Optional<Cupon> obtenerCuponPorCodigo(String codigo) {
        return cuponRepository.findByCodigo(codigo);
    }

    @Override
    public List<Cupon> obtenerTodosCupones() {
        return cuponRepository.findAll();
    }

    @Override
    public List<Cupon> obtenerCuponesActivos() {
        LocalDate hoy = LocalDate.now();
        return cuponRepository.findAll().stream()
                .filter(cupon -> cupon.getActivo() && cupon.getFechaExpiracion().isAfter(hoy))
                .collect(Collectors.toList());
    }

    @Override
    public Cupon actualizarCupon(Cupon cupon) {
        return cuponRepository.save(cupon);
    }

    @Override
    public void eliminarCupon(Long id) {
        cuponRepository.deleteById(id);
    }
}
