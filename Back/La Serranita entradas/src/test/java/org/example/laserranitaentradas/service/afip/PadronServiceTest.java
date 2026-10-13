package org.example.laserranitaentradas.service.afip;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.laserranitaentradas.model.dto.PadronContribuyenteDTO;
import org.example.laserranitaentradas.model.dto.PadronContribuyenteDTO.Estado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/** Las respuestas son de homologación (datos ficticios de ARCA), recortadas a lo que se usa. */
class PadronServiceTest {

    private final ObjectMapper om = new ObjectMapper();
    private AfipSdkClient client;
    private PadronService service;

    @BeforeEach
    void setUp() {
        client = mock(AfipSdkClient.class);
        when(client.estaConfigurado()).thenReturn(true);
        service = new PadronService(client);
    }

    private JsonNode json(String s) throws Exception {
        return om.readTree(s.replace('\'', '"'));
    }

    @Test
    void personaJuridicaResponsableInscripta() throws Exception {
        when(client.consultarPadron(30500010912L)).thenReturn(json("""
                {'personaReturn':{'datosGenerales':{'tipoPersona':'JURIDICA','razonSocial':'IGIKK OP EG IGKUKI GRSPITUIG',
                  'estadoClave':'ACTIVO','domicilioFiscal':{'codPostal':'1036','descripcionProvincia':'CIUDAD AUTONOMA BUENOS AIRES',
                  'direccion':'MITRE BARTOLOME 326 P4 D428','idProvincia':0,'tipoDomicilio':'FISCAL'}},
                 'datosRegimenGeneral':{'impuesto':[{'idImpuesto':10,'estadoImpuesto':'AC'},{'idImpuesto':30,'descripcionImpuesto':'IVA','estadoImpuesto':'AC'}]}}}
                """));

        PadronContribuyenteDTO r = service.consultar("30-50001091-2");

        assertThat(r.estado()).isEqualTo(Estado.ENCONTRADO);
        assertThat(r.cuit()).isEqualTo("30500010912");
        assertThat(r.razonSocial()).isEqualTo("IGIKK OP EG IGKUKI GRSPITUIG");
        assertThat(r.domicilio()).isEqualTo("MITRE BARTOLOME 326 P4 D428, CIUDAD AUTONOMA BUENOS AIRES");
        assertThat(r.condicionIva()).isEqualTo(1);
        assertThat(r.admiteFacturaA()).isTrue();
        assertThat(r.mensaje()).isNull();
    }

    @Test
    void personaFisicaMonotributoSocial_apellidoYNombre_yUnaSolaActividadComoObjeto() throws Exception {
        when(client.consultarPadron(20000000001L)).thenReturn(json("""
                {'personaReturn':{'datosGenerales':{'tipoPersona':'FISICA','apellido':'SKRORPIOPRP','nombre':'SPRAGI','estadoClave':'ACTIVO',
                  'domicilioFiscal':{'descripcionProvincia':'BUENOS AIRES','direccion':'PERALTA RAMOS 1428','localidad':'BARRIO EL DORADO'}},
                 'datosMonotributo':{'categoriaMonotributo':{'descripcionCategoria':'A MONOTRIBUTO SOCIAL AGROP.','idCategoria':61},
                   'impuesto':{'idImpuesto':20,'descripcionImpuesto':'MONOTRIBUTO','estadoImpuesto':'AC'}},
                 'datosRegimenGeneral':{'impuesto':[{'idImpuesto':30,'estadoImpuesto':'AC'}]}}}
                """));

        PadronContribuyenteDTO r = service.consultar("20000000001");

        assertThat(r.razonSocial()).isEqualTo("SKRORPIOPRP SPRAGI");
        assertThat(r.domicilio()).isEqualTo("PERALTA RAMOS 1428, BARRIO EL DORADO, BUENOS AIRES");
        // Monotributo gana: una A como responsable inscripto se la rechazaría ARCA.
        assertThat(r.condicionIva()).isEqualTo(13);
        assertThat(r.admiteFacturaA()).isTrue();
    }

    @Test
    void ivaExento_noAdmiteFacturaA() throws Exception {
        when(client.consultarPadron(anyLong())).thenReturn(json("""
                {'personaReturn':{'datosGenerales':{'razonSocial':'FUNDACION X','estadoClave':'ACTIVO'},
                 'datosRegimenGeneral':{'impuesto':[{'idImpuesto':32,'estadoImpuesto':'AC'}]}}}
                """));

        PadronContribuyenteDTO r = service.consultar("30500010912");

        assertThat(r.condicionIva()).isEqualTo(4);
        assertThat(r.admiteFacturaA()).isFalse();
        assertThat(r.mensaje()).contains("Factura B");
    }

    @Test
    void constanciaConError_devuelveElMotivo() throws Exception {
        when(client.consultarPadron(anyLong())).thenReturn(json("""
                {'personaReturn':{'errorConstancia':{'apellido':'LORNA','nombre':'COCA','idPersona':20000000028,
                  'error':['La CUIT fue cancelada de acuerdo a: DOMICILIO FISCAL ELECTRONICO NO CONSTITUIDO RG 4280.']},'metadata':{}}}
                """));

        PadronContribuyenteDTO r = service.consultar("20000000028");

        assertThat(r.estado()).isEqualTo(Estado.CON_ERROR);
        assertThat(r.razonSocial()).isEqualTo("LORNA COCA");
        assertThat(r.admiteFacturaA()).isFalse();
        assertThat(r.mensaje()).contains("cancelada");
    }

    @Test
    void cuitInexistente() {
        when(client.consultarPadron(anyLong())).thenThrow(new AfipException(
                "AfipSDK respondió 422: {\"message\":\"soap:Server: No existe persona con ese Id\",\"statusCode\":422}", false));

        assertThat(service.consultar("30717015734").estado()).isEqualTo(Estado.NO_EXISTE);
    }

    @Test
    void arcaNoContesta_noEsUnErrorHttp_seCargaAMano() {
        when(client.consultarPadron(anyLong())).thenThrow(new AfipException("No se pudo contactar a AfipSDK: timeout", true));

        PadronContribuyenteDTO r = service.consultar("30717015734");

        assertThat(r.estado()).isEqualTo(Estado.NO_DISPONIBLE);
        assertThat(r.mensaje()).contains("a mano");
    }

    @Test
    void cuitInvalido_noConsulta() {
        assertThatThrownBy(() -> service.consultar("30717015735")).isInstanceOf(IllegalArgumentException.class);
        verify(client, never()).consultarPadron(anyLong());
    }
}
