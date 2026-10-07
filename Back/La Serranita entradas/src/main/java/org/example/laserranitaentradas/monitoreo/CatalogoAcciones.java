package org.example.laserranitaentradas.monitoreo;

import java.util.Map;
import java.util.Set;

import static java.util.Map.entry;

/**
 * Nombre legible de cada operación que modifica algo, por método y patrón de ruta (el del
 * controller). Una operación que no esté acá igual se registra, con su ruta: el catálogo sólo la
 * hace más fácil de leer.
 */
final class CatalogoAcciones {

    record Accion(String codigo, String descripcion, String entidad) {}

    private CatalogoAcciones() {}

    /** Mucho volumen y ya trazables por otro lado (la caja, la compra): no ensucian el historial. */
    static final Set<String> EXCLUIDAS = Set.of(
            "POST /api/interno/compras/venta-pos",
            "POST /api/interno/compras/caja/{cajaId}/venta-pos",
            "PUT /api/compras/{id}/validar",
            "POST /api/usuarios/renovar-sesion",
            "POST /api/interno/notificaciones/{tipo}/marcar-vistas",
            "PUT /api/usuarios/me/tema",
            "PUT /api/usuarios/me/foto",
            // Monitoreo: el latido de cada terminal (cada minuto) y los errores del navegador.
            "POST /api/interno/terminales/latido",
            "POST /api/errores-cliente");

    static final Map<String, Accion> ACCIONES = Map.ofEntries(
            // Sesión
            entry("POST /api/usuarios/login", new Accion("LOGIN", "Inició sesión", null)),
            entry("PUT /api/usuarios/me/password", new Accion("PASSWORD_CAMBIADA", "Cambió su contraseña", null)),
            // Ventas y reservas
            entry("PUT /api/interno/compras/{id}/cancelar-venta", new Accion("VENTA_CANCELADA", "Canceló la venta", "Compra")),
            entry("PUT /api/interno/compras/{id}/editar-venta", new Accion("VENTA_EDITADA", "Editó la venta", "Compra")),
            entry("PUT /api/interno/compras/{id}/contacto", new Accion("CONTACTO_MODIFICADO", "Editó el contacto de la compra", "Compra")),
            entry("POST /api/interno/compras/{id}/reembolsar", new Accion("COMPRA_REEMBOLSADA", "Reembolsó la compra", "Compra")),
            entry("POST /api/interno/compras/{id}/confirmar-pago-efectivo", new Accion("PAGO_EFECTIVO_CONFIRMADO", "Confirmó el pago en efectivo de la compra", "Compra")),
            entry("POST /api/interno/compras/{id}/reenviar-mail", new Accion("MAIL_REENVIADO", "Reenvió el mail de la compra", "Compra")),
            entry("PUT /api/compras/{id}/deshacer-validacion", new Accion("VALIDACION_DESHECHA", "Deshizo la validación de ingreso", "Compra")),
            entry("POST /api/interno/compras/generar-reserva", new Accion("RESERVA_GENERADA", "Generó una reserva manual", "Compra")),
            // Facturas
            entry("POST /api/interno/facturas/manual", new Accion("FACTURA_MANUAL", "Emitió una factura manual", "Factura")),
            entry("POST /api/interno/facturas/{id}/anular", new Accion("FACTURA_ANULADA", "Anuló la factura", "Factura")),
            entry("POST /api/interno/facturas/{id}/reintentar", new Accion("FACTURA_REINTENTADA", "Reintentó la factura", "Factura")),
            entry("POST /api/interno/facturas/{id}/enviar-mail", new Accion("FACTURA_ENVIADA", "Mandó por mail la factura", "Factura")),
            entry("POST /api/interno/facturas/{id}/reenviar-mail", new Accion("FACTURA_REENVIADA", "Reenvió por mail la factura", "Factura")),
            entry("POST /api/interno/facturas/compra/{compraId}", new Accion("VENTA_FACTURADA", "Facturó la venta", "Compra")),
            entry("POST /api/interno/facturas/compra/{compraId}/online", new Accion("COMPRA_ONLINE_FACTURADA", "Facturó la compra online", "Compra")),
            entry("POST /api/interno/facturas/control/numeracion", new Accion("NUMERACION_CONTROLADA", "Controló la numeración contra ARCA", null)),
            entry("POST /api/interno/impresion/facturas/{facturaId}", new Accion("FACTURA_IMPRESA", "Mandó a imprimir la factura", "Factura")),
            // Caja
            entry("POST /api/interno/caja/abrir", new Accion("CAJA_ABIERTA", "Abrió caja", "Caja")),
            entry("POST /api/interno/caja/abrir-sin-control", new Accion("CAJA_ABIERTA_SIN_CONTROL", "Abrió caja sin control", "Caja")),
            entry("POST /api/interno/caja/{id}/cerrar", new Accion("CAJA_CERRADA", "Cerró la caja", "Caja")),
            entry("POST /api/interno/caja/{id}/cerrar-sin-control", new Accion("CAJA_CERRADA_SIN_CONTROL", "Cerró la caja sin control", "Caja")),
            entry("PUT /api/interno/caja/{id}/correccion", new Accion("CAJA_CORREGIDA", "Corrigió el cierre de la caja", "Caja")),
            entry("POST /api/interno/caja/{id}/deshabilitar", new Accion("CAJA_DESHABILITADA", "Borró (deshabilitó) la caja", "Caja")),
            entry("DELETE /api/interno/caja/{id}/ajustes/{ajusteId}", new Accion("AJUSTE_CAJA_DESHECHO", "Deshizo un ajuste de la caja", "Caja")),
            entry("POST /api/interno/caja/retiros", new Accion("RETIRO_APORTE", "Registró un retiro/aporte de efectivo", "Caja")),
            entry("POST /api/interno/caja/{id}/retiros", new Accion("RETIRO_APORTE", "Registró un retiro/aporte de efectivo en la caja", "Caja")),
            entry("POST /api/interno/caja/ingresos-entradas", new Accion("ENTRADAS_FISICAS", "Registró un movimiento de entradas físicas", "Caja")),
            entry("POST /api/interno/caja/{id}/ingresos-entradas", new Accion("ENTRADAS_FISICAS", "Registró un movimiento de entradas físicas en la caja", "Caja")),
            // Operaciones rechazadas
            entry("PUT /api/interno/rechazos/{id}/resolver", new Accion("RECHAZO_RESUELTO", "Marcó resuelta la operación rechazada", "Rechazo")),
            entry("POST /api/interno/rechazos/{id}/reabrir-y-reintentar", new Accion("RECHAZO_REINTENTADO", "Reintentó la operación rechazada", "Rechazo")),
            entry("POST /api/interno/rechazos/reabrir-y-reintentar-lote", new Accion("RECHAZOS_REINTENTADOS", "Reintentó operaciones rechazadas en lote", null)),
            // Configuración: entradas, artículos, precios
            entry("POST /api/tipos-entrada", new Accion("TIPO_ENTRADA_CREADO", "Creó el tipo de entrada", "Tipo de entrada")),
            entry("PUT /api/tipos-entrada/{id}", new Accion("TIPO_ENTRADA_MODIFICADO", "Modificó el tipo de entrada", "Tipo de entrada")),
            entry("DELETE /api/tipos-entrada/{id}", new Accion("TIPO_ENTRADA_ELIMINADO", "Eliminó el tipo de entrada", "Tipo de entrada")),
            entry("PUT /api/tipos-entrada/reordenar", new Accion("TIPOS_ENTRADA_REORDENADOS", "Reordenó los tipos de entrada", null)),
            entry("POST /api/articulos-varios", new Accion("ARTICULO_CREADO", "Creó el artículo", "Artículo")),
            entry("PUT /api/articulos-varios/{id}", new Accion("ARTICULO_MODIFICADO", "Modificó el artículo", "Artículo")),
            entry("DELETE /api/articulos-varios/{id}", new Accion("ARTICULO_ELIMINADO", "Eliminó el artículo", "Artículo")),
            entry("POST /api/descuentos-efectivo", new Accion("PRECIO_GRUPO_CREADO", "Creó un precio por grupo", "Precio por grupo")),
            entry("PUT /api/descuentos-efectivo/{id}", new Accion("PRECIO_GRUPO_MODIFICADO", "Modificó un precio por grupo", "Precio por grupo")),
            entry("DELETE /api/descuentos-efectivo/{id}", new Accion("PRECIO_GRUPO_ELIMINADO", "Eliminó un precio por grupo", "Precio por grupo")),
            entry("POST /api/promociones", new Accion("PROMOCION_CREADA", "Creó la promoción", "Promoción")),
            entry("PUT /api/promociones/{id}", new Accion("PROMOCION_MODIFICADA", "Modificó la promoción", "Promoción")),
            entry("DELETE /api/promociones/{id}", new Accion("PROMOCION_ELIMINADA", "Eliminó la promoción", "Promoción")),
            // Cupones
            entry("POST /api/cupones", new Accion("CUPON_CREADO", "Creó el cupón", "Cupón")),
            entry("PUT /api/cupones/{id}", new Accion("CUPON_MODIFICADO", "Modificó el cupón", "Cupón")),
            entry("POST /api/cupones/familias/generar", new Accion("CUPONES_GENERADOS", "Generó una familia de cupones", null)),
            entry("PUT /api/cupones/familias/{id}/activo", new Accion("FAMILIA_CUPONES_ACTIVADA", "Activó/desactivó una familia de cupones", "Familia de cupones")),
            entry("PUT /api/cupones/familias/{id}/vencimiento", new Accion("FAMILIA_CUPONES_VENCIMIENTO", "Cambió el vencimiento de una familia de cupones", "Familia de cupones")),
            // Días y horarios
            entry("PUT /api/configuracion/horario", new Accion("HORARIO_MODIFICADO", "Cambió el horario del parque", null)),
            entry("PUT /api/dias-apertura/fecha/{fecha}", new Accion("DIA_APERTURA_MODIFICADO", "Abrió/cerró el día", "Día")),
            entry("PUT /api/dias-apertura/fecha/{fecha}/horario", new Accion("DIA_HORARIO_MODIFICADO", "Cambió el horario del día", "Día")),
            // Usuarios
            entry("POST /api/usuarios", new Accion("USUARIO_CREADO", "Creó el usuario", "Usuario")),
            entry("PUT /api/usuarios/{id}", new Accion("USUARIO_MODIFICADO", "Modificó el usuario", "Usuario")),
            entry("DELETE /api/usuarios/{id}", new Accion("USUARIO_ELIMINADO", "Eliminó el usuario", "Usuario")),
            // Sistema
            entry("POST /api/interno/sistema/incidentes/{id}/resolver", new Accion("ERROR_RESUELTO", "Marcó resuelto un error del sistema", "Error")));
}
