package org.example.laserranitaentradas.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PagoResponseDTO {
    private String preferenceId;
    private boolean exitoso;
    private String initPoint;
    private String mensaje;
    private Long compraId;
}