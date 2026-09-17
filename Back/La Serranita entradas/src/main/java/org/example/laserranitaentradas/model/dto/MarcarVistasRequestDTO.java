package org.example.laserranitaentradas.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class MarcarVistasRequestDTO {
    private List<Long> refIds;
}
