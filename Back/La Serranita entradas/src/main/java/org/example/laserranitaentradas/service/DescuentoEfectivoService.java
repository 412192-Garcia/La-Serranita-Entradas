package org.example.laserranitaentradas.service;

import org.example.laserranitaentradas.model.dto.CrearDescuentoEfectivoRequest;
import org.example.laserranitaentradas.model.entity.DescuentoEfectivo;

import java.util.List;

public interface DescuentoEfectivoService {
    List<DescuentoEfectivo> getAll();
    DescuentoEfectivo create(CrearDescuentoEfectivoRequest request);
    DescuentoEfectivo update(Long id, CrearDescuentoEfectivoRequest request);
    void delete(Long id);
}
