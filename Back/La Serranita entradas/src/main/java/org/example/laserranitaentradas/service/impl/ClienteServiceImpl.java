package org.example.laserranitaentradas.service.impl;

import org.example.laserranitaentradas.model.entity.Cliente;
import org.example.laserranitaentradas.repository.ClienteRepository;
import org.example.laserranitaentradas.service.ClienteService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ClienteServiceImpl implements ClienteService {

    private final ClienteRepository clienteRepository;

    public ClienteServiceImpl(ClienteRepository clienteRepository) {
        this.clienteRepository = clienteRepository;
    }

    @Override
    public Cliente create(Cliente cliente) {
        return clienteRepository.save(cliente);
    }

    @Override
    public Optional<Cliente> findById(Long id) {
        return clienteRepository.findById(id);
    }

    @Override
    public List<Cliente> findAllByDni(String dni) {
        return clienteRepository.findAllByDni(dni);
    }
}

