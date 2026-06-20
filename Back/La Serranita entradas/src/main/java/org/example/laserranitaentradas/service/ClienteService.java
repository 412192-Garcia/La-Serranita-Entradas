package org.example.laserranitaentradas.service;

import org.example.laserranitaentradas.model.entity.Cliente;

import java.util.Optional;

public interface ClienteService {
    Cliente create(Cliente cliente);
    Optional<Cliente> findById(Long id);
    Optional<Cliente> findByDni(String dni);
}

