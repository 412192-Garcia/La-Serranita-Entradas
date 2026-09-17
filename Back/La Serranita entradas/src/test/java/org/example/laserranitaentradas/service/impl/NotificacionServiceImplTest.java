package org.example.laserranitaentradas.service.impl;

import org.example.laserranitaentradas.model.entity.NotificacionVista;
import org.example.laserranitaentradas.model.entity.TipoNotificacion;
import org.example.laserranitaentradas.repository.NotificacionVistaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * hayPendientes/marcarVistas son el corazón del sistema de notificaciones genérico: la diferencia
 * de comportamiento entre un tipo que se apaga con sólo verlo (CAJA_ATRASADA) y uno que exige
 * resolución explícita (RECHAZO_OPERACION) vive acá, no en cada feature.
 */
@ExtendWith(MockitoExtension.class)
class NotificacionServiceImplTest {

    @Mock private NotificacionVistaRepository repository;

    private NotificacionServiceImpl service;

    private static final Long USUARIO_ID = 5L;

    @BeforeEach
    void setUp() {
        service = new NotificacionServiceImpl(repository);
    }

    @Test
    void hayPendientes_conListaVacia_devuelveFalse() {
        assertThat(service.hayPendientes(TipoNotificacion.CAJA_ATRASADA, List.of(), USUARIO_ID)).isFalse();
        assertThat(service.hayPendientes(TipoNotificacion.CAJA_ATRASADA, null, USUARIO_ID)).isFalse();
    }

    @Test
    void hayPendientes_tipoQueNoDesaparece_devuelveTrueAunqueEsteTodoVisto() {
        // RECHAZO_OPERACION no consulta el repository: alcanza con que haya candidatos.
        boolean resultado = service.hayPendientes(TipoNotificacion.RECHAZO_OPERACION, List.of(1L, 2L), USUARIO_ID);

        assertThat(resultado).isTrue();
    }

    @Test
    void hayPendientes_tipoQueDesaparece_conAlgunIdSinVer_devuelveTrue() {
        when(repository.findAllByTipoAndUsuarioIdAndRefIdIn(TipoNotificacion.CAJA_ATRASADA, USUARIO_ID, List.of(1L, 2L)))
                .thenReturn(List.of(vista(1L)));

        boolean resultado = service.hayPendientes(TipoNotificacion.CAJA_ATRASADA, List.of(1L, 2L), USUARIO_ID);

        assertThat(resultado).isTrue();
    }

    @Test
    void hayPendientes_tipoQueDesaparece_conTodoVisto_devuelveFalse() {
        when(repository.findAllByTipoAndUsuarioIdAndRefIdIn(TipoNotificacion.CAJA_ATRASADA, USUARIO_ID, List.of(1L, 2L)))
                .thenReturn(List.of(vista(1L), vista(2L)));

        boolean resultado = service.hayPendientes(TipoNotificacion.CAJA_ATRASADA, List.of(1L, 2L), USUARIO_ID);

        assertThat(resultado).isFalse();
    }

    @Test
    void marcarVistas_insertaSoloLosQueFaltan() {
        when(repository.findAllByTipoAndUsuarioIdAndRefIdIn(TipoNotificacion.CAJA_ATRASADA, USUARIO_ID, List.of(1L, 2L, 3L)))
                .thenReturn(List.of(vista(2L)));

        service.marcarVistas(TipoNotificacion.CAJA_ATRASADA, List.of(1L, 2L, 3L), USUARIO_ID);

        ArgumentCaptor<List<NotificacionVista>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(NotificacionVista::getRefId).containsExactlyInAnyOrder(1L, 3L);
    }

    @Test
    void marcarVistas_conTodoYaVisto_noGuardaNada() {
        when(repository.findAllByTipoAndUsuarioIdAndRefIdIn(TipoNotificacion.CAJA_ATRASADA, USUARIO_ID, List.of(1L)))
                .thenReturn(List.of(vista(1L)));

        service.marcarVistas(TipoNotificacion.CAJA_ATRASADA, List.of(1L), USUARIO_ID);

        verify(repository, never()).saveAll(anyList());
    }

    @Test
    void marcarVistas_conListaVacia_noConsultaElRepository() {
        service.marcarVistas(TipoNotificacion.CAJA_ATRASADA, List.of(), USUARIO_ID);

        verify(repository, never()).saveAll(anyList());
    }

    private NotificacionVista vista(Long refId) {
        return NotificacionVista.builder()
                .tipo(TipoNotificacion.CAJA_ATRASADA)
                .refId(refId)
                .usuarioId(USUARIO_ID)
                .vistoEn(LocalDateTime.now())
                .build();
    }
}
