# Agente de impresión

Programa chico que corre en la **PC de la entrada** e imprime las facturas en la ticketera de la
boletería (Elitronic SOL 802, por red).

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

- La PC de la entrada y la ticketera en la **misma red local**.
- La ticketera con **IP fija** (configurada en la propia impresora, o reservada en el router):
  si la IP cambia, el agente deja de encontrarla.
- En el servidor, `IMPRESION_AGENTE_TOKEN` en el `.env` (generar con `openssl rand -base64 32`).

## Compilar

Desde la raíz del repo (usa el Maven del backend):

```bash
"Back/La Serranita entradas/mvnw" -f AgenteImpresion/pom.xml package
```

Queda `AgenteImpresion/target/agente-impresion.jar`. Para no tener que instalar Java en la PC de
la entrada, empaquetarlo con su propio Java (necesita JDK 17+ en la máquina donde se arma). El
`.jar` va solo en una carpeta aparte: si `--input` apunta a `target/` entero, jpackage copia
también su propia salida y se va de tamaño.

```bash
mkdir -p AgenteImpresion/target/paquete
cp AgenteImpresion/target/agente-impresion.jar AgenteImpresion/target/paquete/
jpackage --type app-image --name AgenteImpresion --input AgenteImpresion/target/paquete --main-jar agente-impresion.jar --add-modules java.base,java.logging --dest AgenteImpresion/dist
```

Queda la carpeta `AgenteImpresion/dist/AgenteImpresion/` (~37 MB) con `AgenteImpresion.exe` y
su propio Java recortado a lo que usa el agente.

## Instalar en la PC de la entrada

1. Copiar la carpeta `AgenteImpresion` (la de jpackage, o el `.jar` si la PC tiene Java 17+) a,
   por ejemplo, `C:\AgenteImpresion`.
2. Copiar ahí `agente.properties.example` como `agente.properties` y completar: la URL del
   backend, el token y la IP de la ticketera.
3. Copiar `instalar-tarea.ps1` a la misma carpeta y correrlo en PowerShell **como administrador**:

   ```powershell
   powershell -ExecutionPolicy Bypass -File .\instalar-tarea.ps1
   ```

   Queda como tarea programada: arranca sola al prender la PC (sin iniciar sesión) y Windows la
   vuelve a levantar si se cae.

## Verificar

- En la tablet, al cobrar con Tarjeta debería quedar habilitado "Imprimir factura". Si dice
  "La ticketera no está conectada", el agente no llega al backend.
- `agente0.log` en la carpeta del agente: tiene que decir `Conectado al backend`. Si dice
  `rechazó el token`, el `agente.token` no coincide con `IMPRESION_AGENTE_TOKEN` del servidor.
- Si la factura sale pero el ticket dice "No se pudo imprimir", el agente no llega a la
  impresora: revisar la IP, que esté prendida y en la misma red.
