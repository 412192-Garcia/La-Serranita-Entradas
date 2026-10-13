package org.example.laserranitaentradas.model.dto;

/**
 * Lo que dice el padrón de ARCA de un CUIT, para completar la Factura A sin tipear.
 *
 * @param estado        ENCONTRADO, NO_EXISTE (el CUIT no está en el padrón), CON_ERROR (ARCA no
 *                      da la constancia: CUIT cancelada, bloqueada, con requerimientos pendientes...)
 *                      o NO_DISPONIBLE (no se pudo consultar: ARCA caída, servicio no autorizado)
 * @param condicionIva  código ARCA de la condición frente al IVA (1, 4, 6, 13, 16); null si no está
 *                      inscripto en IVA ni en monotributo
 * @param admiteFacturaA si con esa condición se le puede hacer Factura A
 * @param mensaje       para mostrar tal cual (el motivo del error, o un aviso); null si no hay nada que decir
 */
public record PadronContribuyenteDTO(String cuit, Estado estado, String razonSocial, String domicilio,
                                     Integer condicionIva, String condicionIvaTexto, boolean admiteFacturaA,
                                     String mensaje) {

    public enum Estado { ENCONTRADO, NO_EXISTE, CON_ERROR, NO_DISPONIBLE }

    public static PadronContribuyenteDTO sinDatos(String cuit, Estado estado, String mensaje) {
        return new PadronContribuyenteDTO(cuit, estado, null, null, null, null, false, mensaje);
    }
}
