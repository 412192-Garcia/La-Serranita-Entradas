package org.example.laserranitaentradas.model.dto;

/**
 * Datos del padrón de un CUIT para la Factura A.
 *
 * @param estado ENCONTRADO, NO_EXISTE, CON_ERROR (ARCA no da la constancia) o NO_DISPONIBLE (no se pudo
 * consultar)
 * @param condicionIva código ARCA (1, 4, 6, 13, 16); null si no está en IVA ni monotributo
 * @param admiteFacturaA si se le puede hacer Factura A
 * @param mensaje para mostrar tal cual; null si no hay nada
 */
public record PadronContribuyenteDTO(String cuit, Estado estado, String razonSocial, String domicilio,
                                     Integer condicionIva, String condicionIvaTexto, boolean admiteFacturaA,
                                     String mensaje) {

    public enum Estado { ENCONTRADO, NO_EXISTE, CON_ERROR, NO_DISPONIBLE }

    public static PadronContribuyenteDTO sinDatos(String cuit, Estado estado, String mensaje) {
        return new PadronContribuyenteDTO(cuit, estado, null, null, null, null, false, mensaje);
    }
}
