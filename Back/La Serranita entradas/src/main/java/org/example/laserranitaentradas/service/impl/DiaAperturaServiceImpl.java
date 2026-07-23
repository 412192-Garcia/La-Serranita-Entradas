package org.example.laserranitaentradas.service.impl;

import org.example.laserranitaentradas.model.entity.DiaApertura;
import org.example.laserranitaentradas.repository.DiaAperturaRepository;
import org.example.laserranitaentradas.service.DiaAperturaService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
        List<DiaApertura> resultado = new ArrayList<>(daysInMonth);

        for (int d = 1; d <= daysInMonth; d++) {
            LocalDate fecha = LocalDate.of(year, month, d);
            Optional<DiaApertura> existente = diaAperturaRepository.findByFecha(fecha);
            if (existente.isPresent()) {
                resultado.add(existente.get());
            } else {
                // Si no existe registro, se considera cerrado.
                DiaApertura noExiste = DiaApertura.builder()
                        .id(null)
                        .fecha(fecha)
                        .abierto(false)
                        .build();
                resultado.add(noExiste);
            }
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

}