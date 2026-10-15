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
 * Qué módulos están prendidos (Sistema > Módulos). Se consulta en caminos calientes (cada venta,
 * cada vuelta del scheduler de facturas), así que se guarda en memoria: hay un solo backend y el
 * único que lo cambia es {@link #cambiar}, que actualiza la memoria junto con la base.
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
