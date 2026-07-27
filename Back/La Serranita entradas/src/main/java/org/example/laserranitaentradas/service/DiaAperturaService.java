package org.example.laserranitaentradas.service;

import org.example.laserranitaentradas.model.entity.DiaApertura;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface DiaAperturaService {
    Optional<DiaApertura> findById(Long id);
    Optional<DiaApertura> findByDate(LocalDate fecha);
    DiaApertura setAbiertoByDate(LocalDate fecha, boolean abierto);
    List<DiaApertura> getMonthStatus(Integer year, Integer month);
    Boolean getAbiertoByDate(LocalDate fecha);
    List<String> getDiasAbiertos(Integer year, Integer month);
    /** Establece (o limpia, si ambos son null) el horario especial de un día puntual. */
    DiaApertura setHorarioEspecial(LocalDate fecha, LocalTime horaApertura, LocalTime horaCierre);
}
