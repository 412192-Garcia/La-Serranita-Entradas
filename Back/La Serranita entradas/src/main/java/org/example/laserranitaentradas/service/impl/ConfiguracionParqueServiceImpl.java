package org.example.laserranitaentradas.service.impl;

import org.example.laserranitaentradas.model.entity.ConfiguracionParque;
import org.example.laserranitaentradas.repository.ConfiguracionParqueRepository;
import org.example.laserranitaentradas.service.ConfiguracionParqueService;
import org.springframework.stereotype.Service;

import java.time.LocalTime;

@Service
public class ConfiguracionParqueServiceImpl implements ConfiguracionParqueService {

    private static final Long ID_UNICO = 1L;

    private final ConfiguracionParqueRepository configuracionParqueRepository;

    public ConfiguracionParqueServiceImpl(ConfiguracionParqueRepository configuracionParqueRepository) {
        this.configuracionParqueRepository = configuracionParqueRepository;
    }

    @Override
    public ConfiguracionParque getHorarioGeneral() {
        return configuracionParqueRepository.findById(ID_UNICO)
                .orElseGet(() -> configuracionParqueRepository.save(
                        ConfiguracionParque.builder()
                                .id(ID_UNICO)
                                .horaApertura(LocalTime.of(9, 0))
                                .horaCierre(LocalTime.of(18, 0))
                                .build()
                ));
    }

    @Override
    public ConfiguracionParque actualizarHorarioGeneral(LocalTime horaApertura, LocalTime horaCierre) {
        ConfiguracionParque config = getHorarioGeneral();
        config.setHoraApertura(horaApertura);
        config.setHoraCierre(horaCierre);
        return configuracionParqueRepository.save(config);
    }
}
