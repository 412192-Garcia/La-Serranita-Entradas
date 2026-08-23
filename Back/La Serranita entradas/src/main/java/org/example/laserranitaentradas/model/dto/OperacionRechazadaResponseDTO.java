package org.example.laserranitaentradas.model.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class OperacionRechazadaResponseDTO {
    private Long id;
    private String tipoOperacion;
    /** JSON crudo de lo que se intentó mandar; el frontend lo muestra como lista clave/valor. */
    private String payload;
    private String motivo;
    private String usuario;
    private LocalDateTime fecha;
    private boolean resuelto;
    private String notaResolucion;
    /** Quién/cuándo lo resolvió. Null mientras resuelto sea false. */
    private String resueltoPor;
    private LocalDateTime fechaResolucion;
}
