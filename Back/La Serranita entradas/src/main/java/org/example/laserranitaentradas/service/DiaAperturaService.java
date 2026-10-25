package org.example.laserranitaentradas.service;

import org.example.laserranitaentradas.model.entity.DiaApertura;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface DiaAperturaService {
    Optional<DiaApertura> findById(Long id);
    Optional<DiaApertura> findByDate(LocalDate fecha);
    DiaApertura setAbiertoByDate(LocalDate fecha, boolean abierto);
    List<DiaApertura> getMonthStatus(Integer year, Integer month);
    Boolean getAbiertoByDate(LocalDate fecha);
    /**
     * Días abiertos del mes para el calendario. No descuenta el límite de compra de hoy (ver
     * compraDelDiaCerrada).
     */
    List<String> getDiasAbiertos(Integer year, Integer month);

    /** Cierre del día (especial o general) menos minutosLimiteCompra. */
    LocalDateTime getLimiteDeCompra(LocalDate fecha);

    /** true si la fecha es HOY y ya pasó el límite de compra. */
    boolean compraDelDiaCerrada(LocalDate fecha, LocalDateTime ahora);
    /** Establece (o limpia, si ambos son null) el horario especial de un día puntual. */
    DiaApertura setHorarioEspecial(LocalDate fecha, LocalTime horaApertura, LocalTime horaCierre);

    /** Fecha abierta más lejana; null si no hay. Limita el calendario. */
    LocalDate getUltimaFechaAbierta();
}
