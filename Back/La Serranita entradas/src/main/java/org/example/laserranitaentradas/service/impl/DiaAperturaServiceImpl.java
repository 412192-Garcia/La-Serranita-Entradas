package org.example.laserranitaentradas.service.impl;

import org.example.laserranitaentradas.model.entity.DiaApertura;
import org.example.laserranitaentradas.repository.DiaAperturaRepository;
import org.example.laserranitaentradas.service.DiaAperturaService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DiaAperturaServiceImpl implements DiaAperturaService {

    private final DiaAperturaRepository diaAperturaRepository;

    public DiaAperturaServiceImpl(DiaAperturaRepository diaAperturaRepository) {
        this.diaAperturaRepository = diaAperturaRepository;
    }

    @Override
    public Optional<DiaApertura> findById(Long id) {
        return diaAperturaRepository.findById(id);
    }

    @Override
    public Optional<DiaApertura> findByDate(LocalDate fecha) {
        return diaAperturaRepository.findByFecha(fecha);
    }

    @Override
    public List<DiaApertura> getMonthStatus(Integer year, Integer month) {
        LocalDate inicioMes = LocalDate.of(year, month, 1);
        int daysInMonth = inicioMes.lengthOfMonth();
        LocalDate finMes = inicioMes.withDayOfMonth(daysInMonth);

        // Una sola consulta por rango: antes se hacía un findByFecha por cada día,
        // o sea 28-31 viajes a la base para pintar un mes del calendario.
        Map<LocalDate, DiaApertura> existentes = new HashMap<>();
        for (DiaApertura dia : diaAperturaRepository.findAllByFechaBetween(inicioMes, finMes)) {
            existentes.put(dia.getFecha(), dia);
        }

        // Los días que no están cargados se completan como cerrados para que el
        // calendario siempre reciba el mes entero.
        List<DiaApertura> resultado = new ArrayList<>(daysInMonth);
        for (int d = 1; d <= daysInMonth; d++) {
            LocalDate fecha = LocalDate.of(year, month, d);
            DiaApertura dia = existentes.get(fecha);
            resultado.add(dia != null ? dia : DiaApertura.builder()
                    .id(null)
                    .fecha(fecha)
                    .abierto(false)
                    .build());
        }
        return resultado;
    }

    @Override
    public Boolean getAbiertoByDate(LocalDate fecha) {
        Optional<DiaApertura> opt = diaAperturaRepository.findByFecha(fecha);
        if (opt.isPresent()) {
            return opt.get().getAbierto();
        }
        else {
            return false;
        }
    }

    @Override
    public DiaApertura setAbiertoByDate(LocalDate fecha, boolean abierto) {
        Optional<DiaApertura> opt = diaAperturaRepository.findByFecha(fecha);
        DiaApertura dia;
        if (opt.isPresent()) {
            dia = opt.get();
            dia.setAbierto(abierto);
        } else {
            dia = DiaApertura.builder()
                    .fecha(fecha)
                    .abierto(abierto)
                    .build();
        }
        return diaAperturaRepository.save(dia);
    }

    @Override
    public List<String> getDiasAbiertos(Integer year, Integer month) {
        List<DiaApertura> dias = getMonthStatus(year, month);
        return dias.stream()
                .filter(DiaApertura::getAbierto)
                .map(dia -> dia.getFecha().toString())
                .toList();
    }

    @Override
    public DiaApertura setHorarioEspecial(LocalDate fecha, LocalTime horaApertura, LocalTime horaCierre) {
        Optional<DiaApertura> opt = diaAperturaRepository.findByFecha(fecha);
        DiaApertura dia = opt.orElseGet(() -> DiaApertura.builder()
                .fecha(fecha)
                .abierto(true)
                .build());
        dia.setHoraApertura(horaApertura);
        dia.setHoraCierre(horaCierre);
        return diaAperturaRepository.save(dia);
    }

}