package org.example.laserranitaentradas.controller;

import org.example.laserranitaentradas.config.JwtService;
import org.example.laserranitaentradas.config.UsuarioAutenticado;
import org.example.laserranitaentradas.model.dto.ActualizarFotoRequestDTO;
import org.example.laserranitaentradas.model.dto.ActualizarTemaRequestDTO;
import org.example.laserranitaentradas.model.dto.CambiarPasswordRequestDTO;
import org.example.laserranitaentradas.model.dto.LoginRequest;
import org.example.laserranitaentradas.model.dto.LoginResponseDTO;
import org.example.laserranitaentradas.model.dto.UsuarioResponseDTO;
import org.example.laserranitaentradas.model.entity.RolUsuario;
import org.example.laserranitaentradas.model.entity.Usuario;
import org.example.laserranitaentradas.service.UsuarioService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/usuarios")
@Tag(name = "Usuarios", description = "Operaciones para gestionar usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final JwtService jwtService;

    public UsuarioController(UsuarioService usuarioService, JwtService jwtService) {
        this.usuarioService = usuarioService;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión", description = "Valida usuario/contraseña para el módulo interno (boletería/configuración)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Credenciales válidas"),
            @ApiResponse(responseCode = "401", description = "Usuario o contraseña incorrectos")
    })
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        Optional<Usuario> autenticado = usuarioService.autenticar(request.getUsername(), request.getPassword());
        if (autenticado.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario o contraseña incorrectos");
        }
        LoginResponseDTO dto = entityToLoginDto(autenticado.get());
        dto.setToken(jwtService.generarToken(autenticado.get(), request.isMantenerSesion()));
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/renovar-sesion")
    @Operation(summary = "Renovar la sesión", description = "Devuelve un token nuevo (otra vuelta de la duración larga) a quien inició sesión con \"Mantener sesión iniciada\", así no vence mientras use la app. Vuelve a consultar el usuario: si se dio de baja, no renueva.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Sesión renovada, con los datos actuales del usuario"),
            @ApiResponse(responseCode = "400", description = "La sesión no se inició con \"Mantener sesión iniciada\""),
            @ApiResponse(responseCode = "401", description = "El usuario ya no existe o está inactivo")
    })
    public ResponseEntity<?> renovarSesion(@RequestHeader("Authorization") String authorization,
                                           @AuthenticationPrincipal UsuarioAutenticado operador) {
        String token = authorization.startsWith("Bearer ") ? authorization.substring(7) : authorization;
        boolean sesionLarga = jwtService.validarYExtraerClaims(token).map(jwtService::esSesionLarga).orElse(false);
        if (!sesionLarga) {
            return ResponseEntity.badRequest().body("Esta sesión no se inició con \"Mantener sesión iniciada\": no se renueva.");
        }

        // Se relee de la base a propósito: es lo que cierra el hueco de un token de días que no se
        // puede revocar — un usuario dado de baja no logra renovar, y uno al que le cambiaron el
        // rol recibe el token con el rol actual.
        Optional<Usuario> usuario = usuarioService.obtenerUsuarioPorId(operador.id())
                .filter(u -> Boolean.TRUE.equals(u.getActivo()));
        if (usuario.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("El usuario ya no está habilitado");
        }
        LoginResponseDTO dto = entityToLoginDto(usuario.get());
        dto.setToken(jwtService.generarToken(usuario.get(), true));
        return ResponseEntity.ok(dto);
    }

    @PutMapping("/me/password")
    @Operation(summary = "Cambiar mi propia contraseña", description = "Cualquier usuario logueado puede cambiar su propia contraseña, verificando la actual antes de pisarla")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Contraseña cambiada"),
            @ApiResponse(responseCode = "400", description = "Contraseña actual incorrecta o la nueva no cumple el mínimo")
    })
    public ResponseEntity<Void> cambiarMiPassword(@RequestBody CambiarPasswordRequestDTO request,
                                                   @AuthenticationPrincipal UsuarioAutenticado operador) {
        usuarioService.cambiarPassword(operador.id(), request.getPasswordActual(), request.getPasswordNueva());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/me/tema")
    @Operation(summary = "Cambiar mi color de tema", description = "Cualquier usuario logueado puede personalizar el color principal de la interfaz. Mandar colorTema en null vuelve al tema por defecto.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Color actualizado"),
            @ApiResponse(responseCode = "400", description = "El color no es un hex válido")
    })
    public ResponseEntity<UsuarioResponseDTO> cambiarMiTema(@RequestBody ActualizarTemaRequestDTO request,
                                                             @AuthenticationPrincipal UsuarioAutenticado operador) {
        Usuario actualizado = usuarioService.actualizarColorTema(operador.id(), request.getColorTema(), request.getColorFondo(), request.getColorTarjeta(), request.getColorBorde());
        return ResponseEntity.ok(entityToDto(actualizado));
    }

    @PutMapping("/me/foto")
    @Operation(summary = "Cambiar mi foto de perfil", description = "Cualquier usuario logueado puede subir o sacar su propia foto de perfil. Mandar fotoPerfil en null la saca.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Foto actualizada"),
            @ApiResponse(responseCode = "400", description = "La foto no es una imagen válida o es demasiado grande")
    })
    public ResponseEntity<UsuarioResponseDTO> cambiarMiFoto(@RequestBody ActualizarFotoRequestDTO request,
                                                             @AuthenticationPrincipal UsuarioAutenticado operador) {
        Usuario actualizado = usuarioService.actualizarFotoPerfil(operador.id(), request.getFotoPerfil());
        return ResponseEntity.ok(entityToDto(actualizado));
    }

    @PostMapping
    @Operation(summary = "Crear un nuevo usuario", description = "Crea un nuevo usuario en el sistema")
    @ApiResponse(responseCode = "201", description = "Usuario creado exitosamente")
    public ResponseEntity<UsuarioResponseDTO> crearUsuario(@RequestBody Usuario usuario,
                                                           @AuthenticationPrincipal UsuarioAutenticado operador) {
        exigirSuperadminSi(usuario.getRol() == RolUsuario.SUPERADMIN, operador);
        Usuario nuevoUsuario = usuarioService.crearUsuario(usuario);
        return ResponseEntity.status(HttpStatus.CREATED).body(entityToDto(nuevoUsuario));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener usuario por ID", description = "Obtiene un usuario específico por su ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Usuario encontrado"),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    })
    public ResponseEntity<UsuarioResponseDTO> obtenerUsuarioPorId(@PathVariable @Parameter(description = "ID del usuario") Long id,
                                                                  @AuthenticationPrincipal UsuarioAutenticado operador) {
        return usuarioService.obtenerUsuarioPorId(id)
                .filter(u -> visiblePara(u, operador))
                .map(u -> ResponseEntity.ok(entityToDto(u)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/username/{username}")
    @Operation(summary = "Obtener usuario por username", description = "Obtiene un usuario específico por su nombre de usuario")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Usuario encontrado"),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    })
    public ResponseEntity<UsuarioResponseDTO> obtenerUsuarioPorUsername(@PathVariable @Parameter(description = "Nombre de usuario") String username,
                                                                        @AuthenticationPrincipal UsuarioAutenticado operador) {
        return usuarioService.obtenerUsuarioPorUsername(username)
                .filter(u -> visiblePara(u, operador))
                .map(u -> ResponseEntity.ok(entityToDto(u)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    @Operation(summary = "Obtener todos los usuarios", description = "Obtiene la lista de todos los usuarios registrados")
    @ApiResponse(responseCode = "200", description = "Lista de usuarios obtenida exitosamente")
    public ResponseEntity<List<UsuarioResponseDTO>> obtenerTodosUsuarios(@AuthenticationPrincipal UsuarioAutenticado operador) {
        List<UsuarioResponseDTO> usuarios = usuarioService.obtenerTodosUsuarios().stream()
                .filter(u -> visiblePara(u, operador))
                .map(UsuarioController::entityToDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(usuarios);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar usuario", description = "Actualiza los datos de un usuario existente")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Usuario actualizado exitosamente"),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    })
    public ResponseEntity<UsuarioResponseDTO> actualizarUsuario(@PathVariable @Parameter(description = "ID del usuario") Long id, @RequestBody Usuario usuario,
                                                                @AuthenticationPrincipal UsuarioAutenticado operador) {
        Optional<Usuario> existente = usuarioService.obtenerUsuarioPorId(id).filter(u -> visiblePara(u, operador));
        if (existente.isPresent()) {
            exigirSuperadminSi(usuario.getRol() == RolUsuario.SUPERADMIN, operador);
            usuario.setId(id);
            return ResponseEntity.ok(entityToDto(usuarioService.actualizarUsuario(usuario)));
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar usuario", description = "Elimina un usuario del sistema")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Usuario eliminado exitosamente"),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    })
    public ResponseEntity<Void> eliminarUsuario(@PathVariable @Parameter(description = "ID del usuario") Long id,
                                                @AuthenticationPrincipal UsuarioAutenticado operador) {
        if (usuarioService.obtenerUsuarioPorId(id).filter(u -> visiblePara(u, operador)).isPresent()) {
            usuarioService.eliminarUsuario(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * Los SUPERADMIN (soporte de la app) no aparecen para el admin del parque: no los ve en la lista,
     * no los puede editar ni borrar (para él no existen: 404) y no puede crear ni ascender a nadie a
     * ese rol (403). Así el acceso a Sistema no se puede conseguir desde Usuarios.
     */
    private static boolean visiblePara(Usuario u, UsuarioAutenticado operador) {
        return u.getRol() != RolUsuario.SUPERADMIN || esSuperadmin(operador);
    }

    private static void exigirSuperadminSi(boolean condicion, UsuarioAutenticado operador) {
        if (condicion && !esSuperadmin(operador)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ese rol no se puede asignar desde acá.");
        }
    }

    private static boolean esSuperadmin(UsuarioAutenticado operador) {
        return operador != null && operador.rol() == RolUsuario.SUPERADMIN;
    }

    private static UsuarioResponseDTO entityToDto(Usuario u) {
        UsuarioResponseDTO dto = new UsuarioResponseDTO();
        dto.setId(u.getId());
        dto.setUsername(u.getUsername());
        dto.setNombre(u.getNombre());
        dto.setApellido(u.getApellido());
        dto.setRol(u.getRol());
        dto.setActivo(u.getActivo());
        dto.setColorTema(u.getColorTema());
        dto.setColorFondo(u.getColorFondo());
        dto.setColorTarjeta(u.getColorTarjeta());
        dto.setColorBorde(u.getColorBorde());
        dto.setFotoPerfil(u.getFotoPerfil());
        return dto;
    }

    private static LoginResponseDTO entityToLoginDto(Usuario u) {
        LoginResponseDTO dto = new LoginResponseDTO();
        dto.setId(u.getId());
        dto.setUsername(u.getUsername());
        dto.setNombre(u.getNombre());
        dto.setApellido(u.getApellido());
        dto.setRol(u.getRol());
        dto.setColorTema(u.getColorTema());
        dto.setColorFondo(u.getColorFondo());
        dto.setColorTarjeta(u.getColorTarjeta());
        dto.setColorBorde(u.getColorBorde());
        dto.setFotoPerfil(u.getFotoPerfil());
        return dto;
    }
}
