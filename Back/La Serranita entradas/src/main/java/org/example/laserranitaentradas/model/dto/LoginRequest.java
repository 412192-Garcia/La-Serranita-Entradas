package org.example.laserranitaentradas.model.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String username;
    private String password;
    /** "Mantener sesión iniciada": el token dura días en vez de un turno (ver JwtService). Si no viene, false. */
    private boolean mantenerSesion;
}
