package org.example.laserranitaentradas.service.impl;

import org.example.laserranitaentradas.model.dto.CrearArticuloVarioRequest;
import org.example.laserranitaentradas.model.entity.ArticuloVario;
import org.example.laserranitaentradas.repository.ArticuloVarioRepository;
import org.example.laserranitaentradas.service.ArticuloVarioService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ArticuloVarioServiceImpl implements ArticuloVarioService {

    private final ArticuloVarioRepository articuloVarioRepository;

    public ArticuloVarioServiceImpl(ArticuloVarioRepository articuloVarioRepository) {
        this.articuloVarioRepository = articuloVarioRepository;
    }

    @Override
    public List<ArticuloVario> getAll() {
        return articuloVarioRepository.findAll();
    }

    @Override
    public Optional<ArticuloVario> findById(Long id) {
        return articuloVarioRepository.findById(id);
    }

    @Override
    public ArticuloVario create(CrearArticuloVarioRequest request) {
        validar(request);
        ArticuloVario articulo = ArticuloVario.builder()
                .nombre(request.getNombre().trim())
                .precioSugerido(request.getPrecioSugerido())
                .activo(true)
                .build();
        return articuloVarioRepository.save(articulo);
    }

    @Override
    public ArticuloVario update(Long id, CrearArticuloVarioRequest request) {
        validar(request);
        ArticuloVario articulo = articuloVarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Artículo no encontrado para id: " + id));
        articulo.setNombre(request.getNombre().trim());
        articulo.setPrecioSugerido(request.getPrecioSugerido());
        if (request.getActivo() != null) {
            articulo.setActivo(request.getActivo());
        }
        return articuloVarioRepository.save(articulo);
    }

    @Override
    public void delete(Long id) {
        articuloVarioRepository.findById(id).ifPresent(articulo -> {
            articulo.setActivo(false);
            articuloVarioRepository.save(articulo);
        });
    }

    private void validar(CrearArticuloVarioRequest request) {
        if (request.getNombre() == null || request.getNombre().isBlank()) {
            throw new IllegalArgumentException("Indicá el nombre del artículo");
        }
    }
}
