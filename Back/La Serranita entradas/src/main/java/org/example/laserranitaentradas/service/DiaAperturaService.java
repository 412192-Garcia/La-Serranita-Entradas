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
     * Días abiertos del mes PARA COMPRAR (es lo que arma el calendario público): igual que el estado
     * del mes, pero "hoy" queda afuera una vez pasado el límite de compra (ver getLimiteDeCompra).
     */
    List<String> getDiasAbiertos(Integer year, Integer month);

    /** Igual que getDiasAbiertos(year, month) pero con "ahora" explícito (para poder probarlo). */
    List<String> getDiasAbiertos(Integer year, Integer month, LocalDateTime ahora);

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
