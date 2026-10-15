# Cambios

Qué cambió en cada versión, contado para quien usa el sistema. Lo nuevo se va anotando en
**Sin publicar**; al sacar una versión (rama `release-X.Y.Z`), esa sección pasa a llamarse con el
número y el CI la usa como nota de la Release de GitHub.

## Sin publicar

## 0.10.0 — 2026-10-15

### Cobro con Point de Mercado Pago
- La parte en tarjeta o QR de una venta del POS se cobra en el Point de la boletería, y la venta se
  registra recién cuando el pago queda aprobado. Un pago mixto tarjeta + QR son dos cobros, de a uno.
- Si el pago salió pero la venta no (se rechazó, se recargó la página), el cobro queda guardado en
  la tablet: se usa en la venta siguiente o se devuelve al cliente.
- Cancelar una venta pagada con el Point le devuelve la plata al cliente. Si Mercado Pago no la deja
  devuelta, la venta no se cancela y avisa que hay que devolverla desde el Point.
- Cierre de caja: el posnet se precarga con lo cobrado por el Point y se listan los cobros
  aprobados que no terminaron en venta, con botón para devolverlos.
- Botón **Dispositivos** en el POS: cada tablet elige su ticketera y su Point. Cualquier usuario
  puede pasar un Point a manual (por ejemplo, si se corta internet en la boletería).

### Sistema > Módulos
- Se pueden apagar la Facturación y el Cobro con Point sin tocar el servidor (sólo SUPERADMIN).
  Con la facturación apagada se vende igual y las facturas pendientes esperan a que se prenda.
- Facturación tiene dos submódulos que se apagan por separado: **Boletería** (las facturas del
  POS) y **Compras online** (las de la web). Apagar Facturación apaga las dos.
- **Reservas a pagar en el parque**: apagado, en la compra online sólo se puede pagar con Mercado
  Pago. Las reservas ya hechas se cobran igual en la boletería.
- Ahí también se nombran los Points ("Boletería 1") y se pasan a integrado o manual.

### Facturación
- La facturación habla directo con ARCA, sin AfipSDK: ya no hace falta el token de AfipSDK, sólo
  el certificado y la clave del CUIT.
- **Factura A** (y su nota de crédito) en Facturación manual, a un CUIT. Al completar el CUIT se
  traen razón social, domicilio y condición frente al IVA del padrón de ARCA.
- **Facturación en lote**: las ventas que quedaron sin facturar se emiten todas juntas, una
  Factura B por venta. Reintentar un lote que no se sabe si salió no duplica las facturas.
- Campo "Atendido por" en la factura impresa.
- Selector de ticketera dentro de Facturación manual.

### Monitoreo
- Operaciones rechazadas pasa de Acciones a Sistema (sólo SUPERADMIN).
- Rendimiento: p95, tiempo total, errores 4xx y orden por columna como en el resto de las tablas.
- Sistema > Estado muestra el número de versión del servidor.
- Las métricas para Grafana sólo se calculan si Grafana está configurado (antes corrían siempre,
  cada minuto, y aparecían en Tareas programadas aunque no hubiera Grafana).
- Alertas menos ruidosas: anticipadas sin validar, cajas sin control antes de su cierre
  automático, y las desconexiones del navegador ya no se registran como error.

### Arreglos
- Un error del servidor al registrar una venta (por ejemplo, la base reiniciándose) ya no puede
  hacer que el POS dé la venta por guardada y la saque de la cola sin estar registrada.
- La tarjeta "Mails" de Sistema lleva a Operaciones rechazadas, donde se reenvían.

## 0.9.3 — 2026-10-07
- Arreglo: el pago con Mercado Pago ya no falla al mandar los mails de la compra.

## 0.9.2 — 2026-10-06
- Pantalla **Sistema** (monitoreo): estado de cada parte, historial de acciones, errores y
  rendimiento, con alertas por mail.
- **Control de facturas**: numeración contra ARCA y totales por período.
- Facturación de las compras anticipadas online, por mail.
- Gestión de facturas desde el control de accesos.
- La reserva en efectivo guarda el precio del día en que se reservó.
- Backups automáticos de la base.
- Cantidad de entradas por teclado en el POS. Hora en las facturas y un solo mail para las anticipadas.

## 0.9.1 — 2026-10-04
- **Facturación manual**: Factura B sin venta desde Acciones de admin.
- El número de comprobante sale de la base y el certificado de ARCA queda montado en el servidor.
- Ajustes en el cierre de caja.
- Arreglo: el agente de impresión detecta cuando se desconecta la ticketera USB.

## 0.9 — 2026-10-02
- **Facturación electrónica**: Factura B en la venta de boletería, impresa en la ticketera o por
  mail, y nota de crédito al cancelar o editar una venta facturada.
- **Agente de impresión** para la ticketera de la boletería (USB o red), con instalador.
- Ventana "Ventas y facturas": reimprimir, reenviar por mail o facturar una venta que quedó sin factura.
