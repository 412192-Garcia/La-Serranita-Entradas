# Cambios

Qué cambió en cada versión, contado para quien usa el sistema. Lo nuevo se va anotando en
**Sin publicar**; al sacar una versión (rama `release-X.Y.Z`), esa sección pasa a llamarse con el
número y el CI la usa como nota de la Release de GitHub.

## Sin publicar

- Se corrigió un problema que, con mucho uso, podía dejar el sistema sin responder hasta
  reiniciarlo.
- Compra online: los campos de DNI, su confirmación y la edad le piden al navegador que no los
  complete solo con datos guardados. Si el navegador pone el nombre completo en "Apellido", se
  corrige solo y queda sólo el apellido.

- Textos más cortos y claros en toda la app: tutoriales, ayudas de cada pantalla, avisos y
  confirmaciones. Se sacaron explicaciones de más y términos técnicos.
- Compra online: la página siempre abre con la última versión (antes, después de una
  actualización, se podía ver un rato la anterior y recargarse sola). Además ya no se descarga la
  parte de gestión del parque, así que pesa menos para el visitante.
- La página de compra se marca como en español: el navegador ya no ofrece traducirla.
- Compra online más liviana: los tipos de entrada, las formas de pago y los meses del calendario
  se piden una sola vez, elegir un día ya no hace esperar para ver su horario y aplicar un cupón
  responde en un solo paso. Mientras se espera el pago, la página pregunta menos seguido pasados
  los primeros 2 minutos.
- Reportes, cajas y búsquedas de compras siguen rápidos a medida que se acumulan las ventas de la
  temporada (la base ahora tiene índices para esas consultas).
- La compra online ya no muestra públicamente los tipos de entrada inactivos o exclusivos de
  boletería, ni los datos de un cupón buscado por código.

## 0.10.5 — 2026-10-25

- Configuración > Cobros (antes "Points"): nueva tarjeta "Factura por forma de pago" para elegir
  qué queda marcado en la pantalla de venta al elegir efectivo, tarjeta o QR: "Imprimir factura"
  o "Enviar por mail" (con el mail vacío no se factura). De fábrica sigue como antes: efectivo
  por mail, tarjeta y QR impresas. El boletero lo puede cambiar en cada venta.
- Pantalla de venta: el menú de dispositivos ya no tiene el botón para pasar un Point a manual o
  a integrado (confundía, y el Point suele necesitar un reinicio para tomarlo). Se hace desde
  Configuración > Cobros. La opción "Sin Point" pasa a llamarse "Sin posnet".
- Configuración > Cobros: los Points aparecen primero los que se ven en las tablets y, dentro de
  esos, los integrados. En el menú de dispositivos del POS, los integrados van primero.
- Cambiar un Point a manual o integrado queda sólo para administradores (antes lo podía hacer
  cualquier usuario desde el POS).
- Cobro con Point: "Salió aprobado: anotar a mano" ya no pierde el cobro si la venta no se llega a
  registrar (rechazada, o retomada después de recargar la página). Antes desaparecía y al
  reintentar se le volvía a cobrar al cliente; ahora queda como "el Point ya cobró" y se usa en la
  venta.
- Cobro con Point: cuando Mercado Pago no confirma el resultado, el aviso sale en ámbar con un
  ícono de alerta en vez de verde con el indicador de espera. La ventana muestra si se eligió
  débito o crédito.
- Pantalla de venta: si "Cobrar con Point" queda gris por un cobro aprobado que no coincide con la
  venta, abajo dice por qué. Pago mixto: los dos montos van uno debajo del otro y los botones de
  la segunda forma de pago tienen el nombre completo y tamaño para tocar en la tablet (antes
  decían "Efect…" / "Tarje…" y medían 20px).
- Pantalla de venta en tablets chicas (7"): cuando el carrito no entra en la pantalla (apaisada
  de 600px de alto, o en vertical con el carrito debajo de las entradas), el botón de cobrar
  queda fijo abajo, con el motivo si está gris. Antes, con pago mixto o efectivo con vuelto,
  había que scrollear para encontrarlo. Mientras se escribe en "Paga con" o en un monto, el botón
  vuelve a su lugar para no tapar el vuelto.
  
## 0.10.4 — 2026-10-22

- Control de accesos: nueva acción "Cambiar fecha" en el menú ⋮ de las reservas que todavía no
  ingresaron (pagadas online, a cobrar en caja o generadas desde Acciones). Se elige el día en el
  calendario y, si la reserva tiene mail, se le puede mandar el comprobante con la fecha nueva. El
  día tiene que estar abierto, no ser pasado y tener cupo. El código y el precio no cambian. A una
  reserva sin fecha generada desde Acciones se le puede asignar un día; a un regalo no (el día lo
  elige quien lo recibe). Queda en el historial de acciones con la fecha anterior y la nueva.
- Las reservas generadas desde Acciones figuran como "Generada" en vez de "Pagada
  online".

- Acciones > Generar reserva, reservas sin fecha:
  - El mail al titular es el mismo comprobante que el de una reserva con fecha, con "Cualquier
    día" como fecha de visita. Antes salía el mail de regalo, con los datos del receptor vacíos.
  - El código pasa a ser `RESERVA-N` en vez de `ABIERTA-N` (las ya generadas mantienen el suyo).
  - En Control de accesos y en las anticipadas del POS, el botón que las despliega dice "N sin
    fecha" cuando hay alguna que no es regalo (antes las contaba como regalos).
  - Editar contacto pide el DNI del titular, como en cualquier reserva. Antes la trataba como
    regalo: escondía el DNI del titular y mostraba el de "quien recibe el regalo".
- Acciones > Generar reserva: el mail dice "¡Reserva confirmada!" en vez de "¡Pago confirmado!"
  (no se pagó nada por la app). Al confirmar, el monto aparece como "Sin cobro · valor de lista"
  en vez de "Total".
- Control de accesos: en una reserva generada desde Acciones el menú ⋮ ofrece "Cancelar reserva"
  en vez de "Reembolsar" (no se cobró nada por la app, así que no había nada que devolver y el
  reembolso fallaba). Queda cancelada y figura en Reportes > Cancelaciones.

## 0.10.3 — 2026-10-18

- POS: los botones de cantidad de un toque (1 a 10 y "10+") ya no son sólo del Pase General.
  Cada tipo de entrada tiene la opción "Accesos rápidos en boletería" en Configuración > Tipos de
  entrada (ej. para el pase de menores). Antes salían para los tipos con precios por grupo
  cargados. En una base existente hay que correr `scripts/accesos-rapidos-tipos-entrada.sql` una
  vez, después de subir el back, para que el General (y cualquier tipo con precios por grupo) los
  siga teniendo.
- Módulo interno en el celular:
  - Sistema > Rendimiento: los botones para ordenar ya no se pisan entre sí y en cada tarjeta el
    nombre de cada dato queda a la izquierda y el valor a la derecha.
  - Sistema > Módulos: el interruptor va al lado del nombre y la descripción usa todo el ancho
    (antes quedaba en una columna angosta de tres palabras por renglón).
  - Acciones > Generar reserva: se lee el aviso "Todavía no elegiste un día" / "Día
    seleccionado" (era letra blanca sobre fondo claro, también en la compu), y los − / + de cada
    entrada y las flechas del mes son más grandes para el dedo.
  - Acciones > Facturación manual: cada renglón muestra qué es cada campo (Cant., Descripción,
    Precio unit.) y la cantidad y el precio van juntos; en la lista de facturas el número va en su
    renglón y la fecha y el total abajo.
  - Acciones > Control de facturas: el listado para el contador pasa a tarjetas con el
    comprobante de título y un dato por renglón.
  - POS: el menú de la ticketera / Point se abre abajo, de borde a borde; antes se salía de la
    pantalla por la izquierda.
  - El cartel de "Algo salió mal" usa el ancho del celular (quedaba en una columna angosta).
  - Botones que con el dedo quedaban muy chicos: Guardar / Cancelar de "Editar contacto" en
    Control de accesos (también en la compu), los filtros de Cajas, del historial de una caja y
    de Operaciones rechazadas, Día / Semana / Mes y los días en Reportes, "+ Agregar una venta" y
    los demás de corregir una caja, "Vender sin control de caja", "Sin caja abierta", "Cancelar
    reserva" del carrito y "Deshacer" de Control de accesos.
  - Menú lateral con un ícono por pantalla y la inicial del usuario cuando no tiene foto.
  - POS: la cabecera ocupa dos renglones en vez de tres (título con Tutorial y menú arriba;
    Anticipadas, Caja y el estado de la ticketera / Point abajo, este último sólo con íconos).
    Los descuentos ("Sin descuento" ya no se parte en dos renglones) y el pago mixto (los dos
    montos uno abajo del otro, sin "Efect… / Tarje…" cortados) se acomodan al celular.
  - Anticipadas y Control de accesos: la barra de semanas muestra sólo la semana elegida entre las
    flechas (antes se iba de costado con las flechas fuera de pantalla) y cada día pone el conteo
    de reservas debajo de la fecha.
  - Cajas: los números de la caja (entradas, vendido, facturado) pasan a mosaicos, con el rótulo
    chico arriba del número (antes el rótulo era casi más grande que el número, también en la
    compu); en el historial cada operación lleva su tipo como título.
  - Configuración: los lotes de cupones son tarjetas con "Ver cupones", los precios por grupo
    llevan la cantidad de pases como título, el estado de un día queda alineado con su botón, y
    en las tarjetas los valores se agrupan a la derecha de su etiqueta.
- Sistema > Estado, tarjeta Impresión: "tickets sin imprimir" ya no queda en alerta para siempre.
  Cuenta sólo las facturas cuyo último ticket falló: si se reimprimió bien, o la factura se anuló
  (su ticket se cancela), deja de contar.
- Cobro con Point: si Mercado Pago rechaza el monto por ser muy bajo (ej. un ítem de prueba de
  $1), el boletero ve "Mercado Pago no acepta cobrar ese monto con el Point" en vez de un error en
  inglés. Los demás rechazos de MP muestran también el detalle de qué dato falló.
- Regalos en Control de accesos y en las anticipadas del POS: la fila muestra primero a quien
  recibe el regalo (nombre, DNI y mail: es a quien se le pide el documento en la puerta) y abajo,
  con el ícono de regalo, quién lo compró.
- Comprar como regalo: primero se cargan los datos de quien lo recibe y después los de quien
  compra, que ya no tiene que poner DNI (ni edad ni localidad), y no aparece más el aviso de que
  ese DNI es para entrar al parque. La pantalla de compra confirmada muestra el DNI de quien
  recibe el regalo en vez del de quien lo compró.
  **Antes de subir:** correr `scripts/cliente-dni-opcional.sql` en la base.
- Motivo de cancelación: cada compra cancelada guarda por qué, cuándo y quién. Las que cancela la
  app sola quedan anotadas con lo que contestó Mercado Pago: "Abrió el checkout y no intentó
  pagar", o "Intentó pagar y Mercado Pago no lo aprobó" con el detalle de cada intento (ej. "visa:
  rechazado, fondos insuficientes"), o el error si no se pudo iniciar el pago. Al cancelar una
  venta desde Cajas se puede escribir un motivo (opcional), que queda también en el historial de
  Sistema. Si un checkout cancelado se termina pagando, el motivo se borra al reactivarse.
- Pago online con la ventana de Mercado Pago bloqueada (pasa en Safari de iPhone, en el navegador
  de Instagram, etc.): en vez de un aviso de error y volver a "Pagar", aparece un botón "Abrir
  Mercado Pago" que abre el pago de esa misma reserva. Antes cada reintento creaba otra compra:
  hubo un cliente con 30 compras en 6 minutos hasta poder pagar.
- Reintentos de pago: si el mismo cliente (mismo DNI, o mismo mail en un regalo) vuelve a pagar
  la misma fecha, entradas y cupón dentro de los 30 minutos, se reusa la compra que quedó sin
  pagar en vez de crear otra. No aparta otra vez el cupo del día ni gasta otro uso del cupón. El
  link de pago vence a las 3 horas de creada la compra, junto con la cancelación automática.
  Dos personas con distinto DNI y el mismo mail (una familia) no comparten la compra. Si con el
  link anterior ya pagó, se confirma la reserva en vez de darle otro link; si el pago está en
  proceso, se le pide esperar. Si Mercado Pago falla al dar el link nuevo, la compra no se
  cancela (el link anterior sigue sirviendo). En un regalo, el reintento no crea otro cliente.
- Reportes > Cancelaciones: cuántas compras se cancelaron en el rango y por qué, con el detalle de
  cada una (qué pasó, quién y monto).
  **Después del primer arranque del backend nuevo:** correr
  `scripts/backfill-motivos-cancelacion.sql` para completar el motivo de las canceladas de antes.

## 0.10.2 — 2026-10-16

- Control de facturas: los textos del estado sin certificado y de la numeración ya no hablan del
  "CUIT de prueba" de AfipSDK, que no existe más.
- Avisos de Mercado Pago: los del formato viejo (que nunca validan) se siguen ignorando sin error;
  los del formato nuevo que no validan vuelven a rechazarse y quedan avisados en el log con la
  cuenta de MP de la que vienen, para detectar un secreto mal configurado o una cuenta ajena.
- Avisos tipo **toast** (abajo a la derecha, abajo al centro en el celular, se van solos): el
  resultado de una acción ya no aparece como un texto dentro de la pantalla, a veces lejos del botón.
  Lo usan los Points, la facturación manual y en lote, el control de facturas, las ventanas de
  facturas de una venta o de una caja, el reenvío de mails en Control de accesos, el horario
  general, el cambio de contraseña y la cancelación de ventas.
- Reportes > Compras por estado: ahora aparecen las compras canceladas, reembolsadas y con pago
  pendiente (antes salían siempre en 0). Cuentan si se crearon en el período o si su día de visita
  cae en el período; no suman en recaudación ni en ningún otro total.
- Reportes: lo pagado con Mercado Pago cuenta el día en que se aprobó el pago (antes, el día en que
  se armó la compra; un checkout de las 23:58 pagado a las 00:05 caía el día anterior).
- Reportes: la torta "Recaudación por forma de pago" aplica los ajustes de caja hechos al revisar los
  cierres (por ejemplo, una venta anotada en efectivo que fue con tarjeta). La recaudación total no
  cambia.
- Reportes: la tarjeta Facturado incluye y nombra también las Factura A, y se corrigieron textos de
  ayuda que no describían bien lo que mostraban (recaudación anticipada, ventas por origen).
- Cajas: una caja atrasada (de un día anterior) se puede **cerrar sin controlar** desde la ventana
  de cierre cuando ya no se tiene su conteo. No registra faltante ni sobrante, figura como "sin control" y queda en el
  historial quién la cerró. La fecha de cierre es el momento en que se cerró.
- Reportes: las cajas cerradas (faltantes/sobrantes y ranking de boleteros) cuentan el día en que se
  abrió la caja, que es el de sus ventas (antes, el día en que se cerró: una caja del domingo cerrada
  el sábado caía en el sábado).
- Cobro con Point: al cobrar con tarjeta la tablet pregunta **débito o crédito** y el Point pide
  directo esa tarjeta (antes le preguntaba al cliente si pagaba con tarjeta o con QR).
- Cobro con Point: si se cancela desde la tablet con el cobro ya en la pantalla del Point, el botón
  queda en "Cancelación pedida" (no se puede apretar de nuevo) y la ventana avisa que, si el Point no
  lo suelta, hay que cancelarlo en el propio Point. Un cobro cancelado en el Point ahora dice "Se
  canceló en el Point".
- POS: el pago mixto acepta **dos tarjetas** ("Tarjeta 1" y "Tarjeta 2"), para un cliente que paga
  mitad con cada una. Con el Point se cobran de a una, y cada una pregunta débito o crédito.

## 0.10.1 — 2026-10-16

- Sistema > Estado > Cajas: una caja de otro día que se dejó abierta a propósito deja de alertar
  (punto rojo y mail diario) en cuanto un admin la ve en Cajas. Sigue figurando en la tarjeta como
  información; si queda otra caja atrasada nueva, vuelve a avisar.
- **Configuración > Points**: cada Point tiene un interruptor "En las tablets". Las tablets sólo
  ven los habilitados, así el boletero no elige Points que no se usan. Arrancan todos ocultos: hay
  que habilitar los de la boletería después de actualizar.
- Se sacaron las ventanas nativas del navegador (confirmaciones y avisos): cancelar una venta,
  devolver un cobro, reembolsar, cambiar un DNI, pasar un Point a manual, etc. ahora preguntan con
  un diálogo propio de la app, con su estilo y colores.
- Control de accesos: el botón de la semana actual dice **"Esta semana"** en vez de "Hoy" (abarca
  toda la semana, no sólo el día).
- Las sombras y resaltados que eran verdes fijos ahora siguen el color del tema elegido.
- La lista de Points de Mercado Pago pasa de Sistema > Módulos a **Configuración > Points**, donde
  la ve el admin del parque. Prender o apagar el cobro con Point sigue en Sistema > Módulos.
- Los Points se listan por nombre (antes cambiaban de orden en cada consulta) y un Point recién
  pasado a manual o integrado ya no desaparece de la lista hasta volver a actualizar.
- Las etiquetas Integrado/Manual y las de estado de los módulos se leen bien con cualquier tema de
  colores (antes se perdían con temas oscuros o con un color de borde fuerte).
- Los avisos de Mercado Pago que no se pueden validar (copias en formato viejo de un pago, o
  avisos de otra cuenta de MP que tiene configurada la URL del parque) se ignoran respondiendo OK:
  Mercado Pago deja de reintentarlos y no ensucian Rendimiento. El log dice de qué cuenta vienen.

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
