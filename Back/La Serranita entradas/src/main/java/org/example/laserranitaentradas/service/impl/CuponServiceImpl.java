package org.example.laserranitaentradas.service.impl;

import org.example.laserranitaentradas.model.dto.ActualizarCuponRequest;
import org.example.laserranitaentradas.model.dto.CrearCuponRequest;
import org.example.laserranitaentradas.model.entity.AplicacionDescuento;
import org.example.laserranitaentradas.model.entity.Cupon;
import org.example.laserranitaentradas.repository.CuponRepository;
import org.example.laserranitaentradas.service.CuponService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CuponServiceImpl implements CuponService {

    private final CuponRepository cuponRepository;
    private final SecureRandom random = new SecureRandom();
    private static final String ALPHANUM = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    public CuponServiceImpl(CuponRepository cuponRepository) {
        this.cuponRepository = cuponRepository;
    }

    @Override
    public Optional<Cupon> getById(Long id) {
        return cuponRepository.findById(id);
    }

    @Override
    public Optional<Cupon> getByCode(String codigo) {
        return cuponRepository.findByCodigo(codigo);
    }

    @Override
    public List<Cupon> getAll() {
        return cuponRepository.findAll();
    }

    @Override
    public List<Cupon> getAllActive() {
        LocalDate hoy = LocalDate.now();
        return cuponRepository.findAll().stream()
                .filter(cupon -> cupon.getActivo() && (cupon.getFechaExpiracion() == null || cupon.getFechaExpiracion().isAfter(hoy)))
                .collect(Collectors.toList());
    }

    @Override
    public List<Cupon> getAllIndividuales() {
        return cuponRepository.findAllByFamiliaCuponIsNull();
    }

    @Override
    public Cupon update(Cupon cupon) {
        return cuponRepository.save(cupon);
    }

    @Override
    public boolean consumirUso(Long cuponId) {
        return cuponRepository.consumirUso(cuponId, LocalDate.now()) == 1;
    }

    @Override
    public void liberarUso(Long cuponId) {
        cuponRepository.liberarUso(cuponId, LocalDate.now());
    }

    @Override
    public Cupon create(CrearCuponRequest request) {
        CuponDescuentoCalculator.validarReglas(request.getPorcentajeDescuento(), request.getMontoDescuento(),
                request.getAplicaPor(), request.getMinEntradas(), request.getMaxEntradasAfectadas(),
                request.getTopeDescuento(), request.getFechaDesde(), request.getFechaExpiracion());

        if (request.getUsosMaximos() != null && request.getUsosMaximos() < 1) {
            throw new IllegalArgumentException("Los usos máximos tienen que ser 1 o más (o dejarlo vacío para no limitarlos).");
        }

        String codigo = request.getCodigo();
        if (codigo == null || codigo.isBlank()) {
            // generar hasta encontrar un codigo unico (probablemente inmediato)
            codigo = generarCodigoAleatorio(10);
            while (cuponRepository.findByCodigo(codigo).isPresent()) {
                codigo = generarCodigoAleatorio(10);
            }
        } else {
            // si viene codigo, asegurar que no exista
            if (cuponRepository.findByCodigo(codigo).isPresent()) {
                throw new IllegalArgumentException("Código de cupón ya existe: " + codigo);
            }
        }

        Cupon cupon = Cupon.builder()
                .codigo(codigo)
                // Opcionales: vacío es sin vencimiento / sin límite de usos.
                .fechaExpiracion(request.getFechaExpiracion())
                .usosMaximos(request.getUsosMaximos())
                .porcentajeDescuento(request.getPorcentajeDescuento())
                .montoDescuento(request.getMontoDescuento())
                .activo(true)
                .usosActuales(0)
                .fechaDesde(request.getFechaDesde())
                .aplicaPor(request.getAplicaPor() == null ? AplicacionDescuento.COMPRA : request.getAplicaPor())
                .tiposEntradaIds(request.getTiposEntradaIds() == null ? new HashSet<>() : new HashSet<>(request.getTiposEntradaIds()))
                .minEntradas(request.getMinEntradas())
                .maxEntradasAfectadas(request.getMaxEntradasAfectadas())
                .topeDescuento(request.getTopeDescuento())
                .build();

        return cuponRepository.save(cupon);
    }

    @Override
    @Transactional
    public Cupon actualizar(Long id, ActualizarCuponRequest request) {
        Cupon cupon = cuponRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cupón no encontrado ID: " + id));
        if (request.getActivo() == null) {
            throw new IllegalArgumentException("Falta indicar si el cupón queda activo o no.");
        }
        Integer usos = request.getUsosMaximos();
        LocalDate vence = request.getFechaExpiracion();
        boolean activo = request.getActivo();

        if (usos != null && usos < 1) {
            throw new IllegalArgumentException("Los usos máximos tienen que ser 1 o más (o dejarlo vacío para no limitarlos).");
        }
        if (usos != null && usos < cupon.getUsosActuales()) {
            throw new IllegalArgumentException("El cupón ya se usó " + cupon.getUsosActuales()
                    + " veces: los usos máximos no pueden ser menos.");
        }
        if (vence != null && cupon.getFechaDesde() != null && vence.isBefore(cupon.getFechaDesde())) {
            throw new IllegalArgumentException("El vencimiento no puede ser anterior a la fecha desde (" + cupon.getFechaDesde() + ").");
        }
        // Un cupón agotado o vencido no se puede dejar activo: se rechazaría en cada compra.
        if (activo && usos != null && cupon.getUsosActuales() >= usos) {
            throw new IllegalArgumentException("El cupón está agotado: subí los usos máximos para poder activarlo.");
        }
        if (activo && vence != null && vence.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("El cupón está vencido: cambiá el vencimiento para poder activarlo.");
        }

        cuponRepository.actualizarVigencia(id, usos, vence, activo);
        return cuponRepository.findById(id).orElseThrow();
    }

    private String generarCodigoAleatorio(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUM.charAt(random.nextInt(ALPHANUM.length())));
        }
        return sb.toString();
    }
}
