package org.example.laserranitaentradas.model.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String username;
    private String password;
    /** "Mantener sesión iniciada": el token dura días. Default false. */
    private boolean mantenerSesion;
}
