# Agente de impresión

Programa chico que corre en la **PC de la entrada** e imprime las facturas en la ticketera de la
boletería (Elitronic SOL 802). La ticketera puede estar **por red** (con IP propia) o **por USB**
(instalada en Windows); se pueden mezclar varias en el mismo agente.

## Por qué hace falta

La venta se hace desde una tablet y el backend está en un servidor. El parque usa Starlink, así
que desde afuera no se puede entrar a su red: el servidor no puede llegar a la impresora. El
agente invierte la conexión:

```
Tablet ──► Backend (servidor) ──► ARCA (CAE)
                │
                └──SSE──► Agente (PC entrada) ──puerto 9100──► SOL 802
               (la conexión la abre el agente, desde el parque)
```

1. Al arrancar, el agente se conecta al backend y anuncia sus impresoras.
2. Cuando se vende con "Imprimir factura", el backend emite la factura y le empuja el ticket
   (ya armado en ESC/POS) por esa conexión.
3. El agente lo manda a la impresora por la red local y confirma.

Si se corta internet o se reinicia el servidor, el agente se reconecta solo. Los tickets que
quedaron sin confirmar se reenvían al reconectar y el agente no imprime dos veces el mismo
(los recuerda en `impresos.txt`). Un ticket que no se pudo imprimir en 15 minutos se descarta
(el cliente ya se fue); se puede reimprimir desde la tablet.

## Requisitos

- Ticketera **por red**: en la misma red local que la PC y con **IP fija** (configurada en la
  propia impresora, o reservada en el router): si la IP cambia, el agente deja de encontrarla.
- Ticketera **por USB**: instalada en Windows como impresora, con el driver de la marca o con el
  genérico "Generic / Text Only". El agente le pasa a la cola de Windows los bytes del ticket tal
  cual (sin convertir), así que salen igual que por red, con QR y corte.
- En el servidor, `IMPRESION_AGENTE_TOKEN` en el `.env` (generar con `openssl rand -base64 32`)
  y reiniciar el backend.

## Instalar en la PC de la entrada

No hace falta el repo, ni Maven, ni Java:

1. Bajar `AgenteImpresion-windows.zip` de la sección
   [Releases](https://github.com/412192-Garcia/La-Serranita-Entradas/releases) del repo y
   descomprimirlo.
2. Doble clic en **`instalar.bat`** (pide permiso de administrador) y contestar:

   | Pregunta | Qué poner |
   |---|---|
   | Dirección del backend | `https://<dominio del sistema>/api` |
   | Token del agente | el `IMPRESION_AGENTE_TOKEN` del `.env` del servidor. Si todavía no hay uno, **Enter**: genera uno seguro, lo muestra y lo deja copiado para pegarlo en el `.env` (y reiniciar el backend). Pide al menos 16 caracteres |
   | Nombre de la ticketera | el que se va a ver en la tablet (Enter = `Boleteria`) |
   | Conexión | `1` = red, `2` = USB / instalada en Windows |
   | Red: IP y puerto | la IP fija de la impresora; puerto Enter (9100, el estándar) |
   | USB: impresora | se elige por número de la lista de impresoras instaladas en Windows |
   | ¿Agregar otra ticketera? | `s` para cargar otra (nombre, IP, puerto) en el mismo agente |

   Con más de una ticketera, en la tablet aparece un selector para elegir en cuál imprime;
   cada tablet recuerda la suya.

   El instalador prueba que llegue a la ticketera y al backend, copia el agente a
   `C:\AgenteImpresion`, lo deja como tarea programada (arranca sola al prender la PC, sin
   iniciar sesión, y Windows la vuelve a levantar si se cae) y avisa si quedó conectado.

Para **cambiar la configuración** (token, una IP, pasar de red a USB, agregar o sacar una
ticketera): `C:\AgenteImpresion\configurar.bat`. Hace las mismas preguntas con los valores
actuales (Enter deja cada uno), guarda y reinicia el agente, sin reinstalar nada.

Para **actualizar** a una versión nueva: correr el `instalar.bat` del zip nuevo (también ofrece
los valores actuales). Para **sacarlo**: `C:\AgenteImpresion\desinstalar.bat`.

## Publicar una versión nueva

El zip lo arma GitHub Actions (`.github/workflows/agente-impresion.yml`) en Windows: compila,
empaqueta con su propio Java recortado (~65 MB) y lo sube al Release. El
módulo `jdk.crypto.ec` no se puede sacar del paquete: sin él no conecta por HTTPS con
certificados de curva elíptica, que son los que usa Caddy por defecto.

```bash
git tag agente-v1.0.0
git push origin agente-v1.0.0
```

Cada cambio en `AgenteImpresion/` que llega a `main` también lo compila y deja el zip como
artefacto del run (sin publicar Release), para probarlo antes.

## Compilar a mano (desarrollo)

Desde la raíz del repo, con el Maven del backend:

```bash
"Back/La Serranita entradas/mvnw" -f AgenteImpresion/pom.xml package
java -jar AgenteImpresion/target/agente-impresion.jar ruta/a/agente.properties
```

## Verificar

- En la tablet, al cobrar con Tarjeta debería quedar habilitado "Imprimir factura". Si dice
  "La ticketera no está conectada", el agente no llega al backend.
- `C:\AgenteImpresion\agente0.log`: tiene que decir `Conectado al backend`. Si dice
  `rechazó el token`, el `agente.token` no coincide con `IMPRESION_AGENTE_TOKEN` del servidor.
- Si la factura sale pero el ticket dice "No se pudo imprimir", el agente no llega a la
  impresora. Por red: revisar la IP, que esté prendida y en la misma red. Por USB: que Windows la
  muestre como lista (sin error ni "sin conexión") y que el nombre coincida.
