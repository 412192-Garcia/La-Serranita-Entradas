package org.example.laserranitaentradas.service;

import org.example.laserranitaentradas.model.entity.DiaApertura;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DiaAperturaService {
    Optional<DiaApertura> findById(Long id);
    Optional<DiaApertura> findByDate(LocalDate fecha);
    DiaApertura setAbiertoByDate(LocalDate fecha, boolean abierto);
    List<DiaApertura> getMonthStatus(Integer year, Integer month);
    Boolean getAbiertoByDate(LocalDate fecha);
}
