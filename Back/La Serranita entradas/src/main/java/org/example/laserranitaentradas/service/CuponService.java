package org.example.laserranitaentradas.service;

import org.example.laserranitaentradas.model.entity.Cupon;

import java.util.List;
import java.util.Optional;

public interface CuponService {
    Cupon crearCupon(Cupon cupon);
    Optional<Cupon> obtenerCuponPorId(Long id);
    Optional<Cupon> obtenerCuponPorCodigo(String codigo);
    List<Cupon> obtenerTodosCupones();
    List<Cupon> obtenerCuponesActivos();
    Cupon actualizarCupon(Cupon cupon);
    void eliminarCupon(Long id);
}

