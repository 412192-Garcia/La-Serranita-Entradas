package org.example.laserranitaentradas.service;

import org.example.laserranitaentradas.model.entity.Modulo;
import org.example.laserranitaentradas.model.entity.ModuloSistema;
import org.example.laserranitaentradas.monitoreo.AuditoriaContexto;
import org.example.laserranitaentradas.repository.ModuloSistemaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Módulos prendidos, en memoria porque se consultan en cada venta. Hay un solo backend y {@link #cambiar}
 * actualiza memoria y base.
 */
@Service
public class ModulosService {

    private static final Logger log = LoggerFactory.getLogger(ModulosService.class);

    private final ModuloSistemaRepository repository;
    private final Map<Modulo, Boolean> cache = new ConcurrentHashMap<>();

    public ModulosService(ModuloSistemaRepository repository) {
        this.repository = repository;
    }

    public boolean activo(Modulo modulo) {
        return cache.computeIfAbsent(modulo, m -> repository.findById(m.name())
                .map(ModuloSistema::isActivo)
                .orElse(m.isActivoPorDefecto()));
    }

    public void cambiar(Modulo modulo, boolean activo) {
        boolean antes = activo(modulo);
        ModuloSistema fila = repository.findById(modulo.name()).orElseGet(() -> new ModuloSistema(modulo.name(), activo));
        fila.setActivo(activo);
        AuditoriaContexto.cambio(modulo.getNombre(), antes ? "Prendido" : "Apagado", activo ? "Prendido" : "Apagado");
        repository.save(fila);
        cache.put(modulo, activo);
        log.info("Módulo {} {}", modulo, activo ? "PRENDIDO" : "APAGADO");
    }
}
