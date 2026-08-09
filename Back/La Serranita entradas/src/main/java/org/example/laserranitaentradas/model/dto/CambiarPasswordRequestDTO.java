package org.example.laserranitaentradas.model.dto;

import lombok.Data;

@Data
public class CambiarPasswordRequestDTO {
    private String passwordActual;
    private String passwordNueva;
}
