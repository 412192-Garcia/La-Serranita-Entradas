package org.example.laserranitaentradas.service.impl;

import org.example.laserranitaentradas.model.dto.FacturaPredeterminadaDTO;
import org.example.laserranitaentradas.model.entity.ConfiguracionParque;
import org.example.laserranitaentradas.model.entity.DestinoFactura;
import org.example.laserranitaentradas.repository.ConfiguracionParqueRepository;
import org.example.laserranitaentradas.service.ConfiguracionParqueService;
import org.springframework.stereotype.Service;

import java.time.LocalTime;

@Service
public class ConfiguracionParqueServiceImpl implements ConfiguracionParqueService {

    private static final Long ID_UNICO = 1L;

    private final ConfiguracionParqueRepository configuracionParqueRepository;

    public ConfiguracionParqueServiceImpl(ConfiguracionParqueRepository configuracionParqueRepository) {
        this.configuracionParqueRepository = configuracionParqueRepository;
    }

    @Override
    public ConfiguracionParque getHorarioGeneral() {
        return configuracionParqueRepository.findById(ID_UNICO)
                .orElseGet(() -> configuracionParqueRepository.save(
                        ConfiguracionParque.builder()
                                .id(ID_UNICO)
                                .horaApertura(LocalTime.of(9, 0))
                                .horaCierre(LocalTime.of(18, 0))
                                .build()
                ));
    }

    @Override
    public ConfiguracionParque actualizarHorarioGeneral(LocalTime horaApertura, LocalTime horaCierre, Integer minutosLimiteCompra) {
        if (minutosLimiteCompra != null && (minutosLimiteCompra < 0 || minutosLimiteCompra > MAX_MINUTOS_LIMITE_COMPRA)) {
            throw new IllegalArgumentException("El límite de compra tiene que estar entre 0 y "
                    + MAX_MINUTOS_LIMITE_COMPRA + " minutos antes del cierre.");
        }
        ConfiguracionParque config = getHorarioGeneral();
        config.setHoraApertura(horaApertura);
        config.setHoraCierre(horaCierre);
        if (minutosLimiteCompra != null) {
            config.setMinutosLimiteCompra(minutosLimiteCompra);
        }
        return configuracionParqueRepository.save(config);
    }

    @Override
    public ConfiguracionParque actualizarFacturaPredeterminada(FacturaPredeterminadaDTO predeterminada) {
        DestinoFactura efectivo = validarDestino(predeterminada.efectivo(), "efectivo");
        DestinoFactura tarjeta = validarDestino(predeterminada.tarjeta(), "tarjeta");
        DestinoFactura qr = validarDestino(predeterminada.qr(), "QR");
        ConfiguracionParque config = getHorarioGeneral();
        config.setFacturaEfectivo(efectivo);
        config.setFacturaTarjeta(tarjeta);
        config.setFacturaQr(qr);
        return configuracionParqueRepository.save(config);
    }

    /** NINGUNO es de las facturas hechas desde la oficina: en el POS no es una opción. */
    private static DestinoFactura validarDestino(DestinoFactura destino, String formaPago) {
        if (destino != DestinoFactura.IMPRIMIR && destino != DestinoFactura.MAIL) {
            throw new IllegalArgumentException("La factura por defecto de " + formaPago + " tiene que ser imprimir o enviar por mail.");
        }
        return destino;
    }
}
