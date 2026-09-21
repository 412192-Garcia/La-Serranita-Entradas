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
     * Días en que el parque abre en ese mes (lo que arma el calendario público). Es sólo el estado
     * de apertura: no descuenta el límite de compra de hoy — un día que estuvo abierto sigue figurando
     * como abierto aunque ya no se pueda comprar para él (ver compraDelDiaCerrada), así el calendario
     * puede distinguir "hoy estuvo abierto pero ya no se compra" de "hoy está cerrado".
     */
    List<String> getDiasAbiertos(Integer year, Integer month);

    /**
     * Hasta cuándo se puede comprar para esa fecha: la hora de cierre de ese día (la del horario
     * especial si lo tiene, si no la del horario general) menos el lapso configurado en
     * ConfiguracionParque.minutosLimiteCompra.
     */
    LocalDateTime getLimiteDeCompra(LocalDate fecha);

    /**
     * true si esa fecha es HOY y ya pasó el límite de compra. Sólo el día de hoy se corta: para una
     * fecha futura siempre se puede comprar, y una pasada no es asunto de esta regla.
     */
    boolean compraDelDiaCerrada(LocalDate fecha, LocalDateTime ahora);
    /** Establece (o limpia, si ambos son null) el horario especial de un día puntual. */
    DiaApertura setHorarioEspecial(LocalDate fecha, LocalTime horaApertura, LocalTime horaCierre);

    /** Fecha abierta más lejana ya cargada; null si no hay ninguna. Limita hasta dónde puede avanzar el calendario. */
    LocalDate getUltimaFechaAbierta();
}
