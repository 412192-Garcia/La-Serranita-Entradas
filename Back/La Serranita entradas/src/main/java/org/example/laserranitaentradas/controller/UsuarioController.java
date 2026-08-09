package org.example.laserranitaentradas.controller;

import org.example.laserranitaentradas.config.JwtService;
import org.example.laserranitaentradas.config.UsuarioAutenticado;
import org.example.laserranitaentradas.model.dto.ActualizarFotoRequestDTO;
import org.example.laserranitaentradas.model.dto.ActualizarTemaRequestDTO;
import org.example.laserranitaentradas.model.dto.CambiarPasswordRequestDTO;
import org.example.laserranitaentradas.model.dto.LoginRequest;
import org.example.laserranitaentradas.model.dto.LoginResponseDTO;
import org.example.laserranitaentradas.model.dto.UsuarioResponseDTO;
import org.example.laserranitaentradas.model.entity.Usuario;
import org.example.laserranitaentradas.service.UsuarioService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
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
        dto.setToken(jwtService.generarToken(autenticado.get()));
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
    public ResponseEntity<UsuarioResponseDTO> crearUsuario(@RequestBody Usuario usuario) {
        Usuario nuevoUsuario = usuarioService.crearUsuario(usuario);
        return ResponseEntity.status(HttpStatus.CREATED).body(entityToDto(nuevoUsuario));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener usuario por ID", description = "Obtiene un usuario específico por su ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Usuario encontrado"),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    })
    public ResponseEntity<UsuarioResponseDTO> obtenerUsuarioPorId(@PathVariable @Parameter(description = "ID del usuario") Long id) {
        return usuarioService.obtenerUsuarioPorId(id)
                .map(u -> ResponseEntity.ok(entityToDto(u)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/username/{username}")
    @Operation(summary = "Obtener usuario por username", description = "Obtiene un usuario específico por su nombre de usuario")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Usuario encontrado"),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    })
    public ResponseEntity<UsuarioResponseDTO> obtenerUsuarioPorUsername(@PathVariable @Parameter(description = "Nombre de usuario") String username) {
        return usuarioService.obtenerUsuarioPorUsername(username)
                .map(u -> ResponseEntity.ok(entityToDto(u)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    @Operation(summary = "Obtener todos los usuarios", description = "Obtiene la lista de todos los usuarios registrados")
    @ApiResponse(responseCode = "200", description = "Lista de usuarios obtenida exitosamente")
    public ResponseEntity<List<UsuarioResponseDTO>> obtenerTodosUsuarios() {
        List<UsuarioResponseDTO> usuarios = usuarioService.obtenerTodosUsuarios().stream()
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
    public ResponseEntity<UsuarioResponseDTO> actualizarUsuario(@PathVariable @Parameter(description = "ID del usuario") Long id, @RequestBody Usuario usuario) {
        if (usuarioService.obtenerUsuarioPorId(id).isPresent()) {
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
    public ResponseEntity<Void> eliminarUsuario(@PathVariable @Parameter(description = "ID del usuario") Long id) {
        if (usuarioService.obtenerUsuarioPorId(id).isPresent()) {
            usuarioService.eliminarUsuario(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
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
