package org.example.laserranitaentradas.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.laserranitaentradas.config.UsuarioAutenticado;
import org.example.laserranitaentradas.model.dto.VoucherManualRequestDTO;
import org.example.laserranitaentradas.model.dto.VoucherResponseDTO;
import org.example.laserranitaentradas.model.entity.Voucher;
import org.example.laserranitaentradas.repository.UsuarioRepository;
import org.example.laserranitaentradas.service.impresion.VoucherService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/interno/vouchers/manuales")
@Tag(name = "Vouchers manuales", description = "Vouchers sueltos, sin reserva, que emite un admin")
public class VoucherManualController {

    private final VoucherService voucherService;
    private final UsuarioRepository usuarioRepository;

    public VoucherManualController(VoucherService voucherService, UsuarioRepository usuarioRepository) {
        this.voucherService = voucherService;
        this.usuarioRepository = usuarioRepository;
    }

    @PostMapping
    @Operation(summary = "Emitir e imprimir un voucher manual")
    public VoucherResponseDTO emitir(@RequestBody VoucherManualRequestDTO request,
                                     @AuthenticationPrincipal UsuarioAutenticado operador) {
        return dto(voucherService.emitirManual(request, usuarioRepository.getReferenceById(operador.id())));
    }

    private static VoucherResponseDTO dto(Voucher v) {
        return VoucherResponseDTO.builder()
                .id(v.getId())
                .codigo(v.getCodigo())
                .tipo(v.getTipo())
                .descripcion(v.getDescripcion())
                .cantidad(v.getCantidad())
                .monto(v.getMonto())
                .fechaEmision(v.getFechaEmision())
                .anulado(v.isAnulado())
                .build();
    }
}
