package org.example.laserranitaentradas.service.impl;

import org.example.laserranitaentradas.model.entity.NotificacionVista;
import org.example.laserranitaentradas.model.entity.TipoNotificacion;
import org.example.laserranitaentradas.repository.NotificacionVistaRepository;
import org.example.laserranitaentradas.service.NotificacionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class NotificacionServiceImpl implements NotificacionService {

    private final NotificacionVistaRepository repository;

    public NotificacionServiceImpl(NotificacionVistaRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean hayPendientes(TipoNotificacion tipo, List<Long> refIdsActuales, Long usuarioId) {
        if (refIdsActuales == null || refIdsActuales.isEmpty()) {
            return false;
        }
        if (!tipo.isDesapareceAlVerse()) {
            // Hay candidatos vigentes y este tipo exige resolverlos, no sólo verlos.
            return true;
        }
        Set<Long> vistos = repository.findAllByTipoAndUsuarioIdAndRefIdIn(tipo, usuarioId, refIdsActuales).stream()
                .map(NotificacionVista::getRefId)
                .collect(Collectors.toSet());
        return refIdsActuales.stream().anyMatch(id -> !vistos.contains(id));
    }

    @Transactional
    @Override
    public void marcarVistas(TipoNotificacion tipo, List<Long> refIds, Long usuarioId) {
        if (refIds == null || refIds.isEmpty()) {
            return;
        }
        if (!tipo.isDesapareceAlVerse()) {
            // hayPendientes nunca consulta las vistas de este tipo: guardarlas sería estado muerto.
            return;
        }
        Set<Long> yaVistos = repository.findAllByTipoAndUsuarioIdAndRefIdIn(tipo, usuarioId, refIds).stream()
                .map(NotificacionVista::getRefId)
                .collect(Collectors.toSet());
        LocalDateTime ahora = LocalDateTime.now();
        List<NotificacionVista> nuevas = refIds.stream()
                .filter(id -> !yaVistos.contains(id))
                .map(id -> NotificacionVista.builder().tipo(tipo).refId(id).usuarioId(usuarioId).vistoEn(ahora).build())
                .toList();
        if (!nuevas.isEmpty()) {
            repository.saveAll(nuevas);
        }
    }
}
