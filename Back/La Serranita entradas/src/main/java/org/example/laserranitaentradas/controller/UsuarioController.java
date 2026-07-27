package org.example.laserranitaentradas.controller;

import org.example.laserranitaentradas.config.JwtService;
import org.example.laserranitaentradas.model.dto.LoginRequest;
import org.example.laserranitaentradas.model.dto.LoginResponseDTO;
import org.example.laserranitaentradas.model.dto.UsuarioResponseDTO;
import org.example.laserranitaentradas.model.entity.Usuario;
import org.example.laserranitaentradas.service.UsuarioService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
        return dto;
    }

    private static LoginResponseDTO entityToLoginDto(Usuario u) {
        LoginResponseDTO dto = new LoginResponseDTO();
        dto.setId(u.getId());
        dto.setUsername(u.getUsername());
        dto.setNombre(u.getNombre());
        dto.setApellido(u.getApellido());
        dto.setRol(u.getRol());
        return dto;
    }
}
