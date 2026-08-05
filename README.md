# La Serranita — Sistema de Entradas

Sistema de venta de entradas y control de accesos por DNI para el **Parque Recreativo La Serranita**
(Córdoba, Argentina). Reemplaza la plataforma de ticketing de terceros que usaba el parque, para
eliminar comisiones y agilizar el ingreso en boletería.

Trabajo final de carrera — Tomás Jeremías García Tini (legajo 412192).

## Cómo está organizado

El repositorio tiene dos aplicaciones:

| Carpeta | Qué es | Stack |
| --- | --- | --- |
| `Back/La Serranita entradas/` | API REST | Java 17 · Spring Boot 3.5.8 · Spring Data JPA · H2 (dev) / Postgres (prod) |
| `Front/` | Aplicación web | Angular 21 (componentes standalone + signals) |

El frontend se divide en dos módulos que comparten el mismo proyecto:

- **Módulo web (público)** — `/entradas`: el flujo de compra que se embebe en el sitio del parque
  (calendario → selección de entradas → datos del comprador → resumen y pago). Va en el bundle inicial.
- **Módulo interno (staff)** — `/login`, `/boleteria`, `/configuracion`: control de accesos y
  administración. Se carga bajo demanda para que no pese sobre el visitante que sólo viene a comprar.

## Decisiones de diseño que conviene conocer

- **Validación por DNI, sin QR.** El comprador no descarga ni presenta ningún código: en boletería se
  tipea (o se escanea con lector PDF417) el DNI del titular y aparece la reserva.
- **Una compra por grupo.** Una sola transacción asocia todos los pases al DNI del titular, y el
  check-in habilita al grupo completo de una vez; no hay ingreso parcial persona por persona.
- **Dos formas de pago.** Mercado Pago (se acredita por webhook) y efectivo en boletería (queda
  reservado y se cobra al llegar, con precios promocionales por cantidad de pases).

## Requisitos

- JDK 17
- Node.js 20+
- No hace falta instalar Maven: el proyecto trae el wrapper (`mvnw` / `mvnw.cmd`).

## Configuración

El backend lee las credenciales de un archivo `.env` en `Back/La Serranita entradas/`, que está
fuera del control de versiones. Hay que crearlo con estas claves:

```
MP_ACCESS_TOKEN=<access token de Mercado Pago>
MP_NOTIFICATION_URL=<URL pública del webhook, opcional en local>
MAIL_USERNAME=<casilla que envía los comprobantes>
MAIL_PASSWORD=<contraseña de aplicación de esa casilla>
JWT_SECRET=<cadena larga y aleatoria para firmar los tokens>
```

Ninguna tiene valor por defecto a propósito: si falta una, la aplicación no arranca en vez de
levantar con una configuración insegura. Para generar el `JWT_SECRET`:

```bash
openssl rand -base64 48
```

La dirección del backend que usa el frontend se define en `Front/src/environments/environment.ts`
(dev) y `environment.prod.ts` (producción — ver más abajo).

## Cómo levantarlo

Backend (queda en `http://localhost:8080`):

```bash
cd "Back/La Serranita entradas" && ./mvnw spring-boot:run
```

Frontend (queda en `http://localhost:4200`):

```bash
cd Front && npm install && npm start
```

## Base de datos

En **dev** (el perfil que se activa localmente por defecto): H2 en memoria, el esquema se crea y se
destruye en cada arranque (`ddl-auto=create-drop`) y se puebla desde `src/main/resources/data.sql`.
Es decir, **los datos no sobreviven a un reinicio** — a propósito, para no tener que limpiar nada
entre pruebas. En **producción** es Postgres real y persistente (ver "Despliegue en producción").

- Consola H2: `http://localhost:8080/h2-console` — JDBC URL `jdbc:h2:mem:testdb`, usuario `sa`, sin contraseña.
- Documentación de la API: `http://localhost:8080/swagger-ui.html`

### Usuarios de prueba

Vienen cargados desde `data.sql` y sirven para entrar al módulo interno:

| Usuario | Contraseña | Rol | Acceso |
| --- | --- | --- | --- |
| `admin` | `admin123` | ADMIN | Boletería + Configuración |
| `boletero.marta` | `boletero123` | BOLETERO | Sólo Boletería |
| `boletero.juan` | `boletero123` | BOLETERO | Sólo Boletería |

## Despliegue en producción

El proyecto corre en dos perfiles de Spring (`dev` por defecto, activado localmente; `prod` para
todo lo demás) y viene con Docker + docker-compose para levantar los tres servicios juntos
(Postgres, backend, frontend con nginx delante).

**Diferencias del perfil `prod` respecto de `dev`:**
- Base de datos: Postgres real en vez de H2 en memoria — los datos sobreviven a un reinicio.
- `data.sql` no se ejecuta (son usuarios y datos de prueba). La base arranca vacía.
- La consola H2 (`/h2-console`) queda deshabilitada.
- CORS ya no está fijo a `localhost:4200`: se configura por variable de entorno.

**Primer arranque contra una base vacía:** sin `data.sql`, no hay ningún usuario para loguearse.
Seteando `BOOTSTRAP_ADMIN_USERNAME` y `BOOTSTRAP_ADMIN_PASSWORD`, el backend crea un único ADMIN
inicial la primera vez que arranca contra una base sin usuarios (`BootstrapAdminRunner`). Después
de loguearse con ese usuario, crear los usuarios reales desde Configuración → Usuarios y sacar esas
dos variables del hosting.

### Con Docker Compose

```bash
cp .env.example .env   # completar los valores (ver el archivo para el detalle de cada uno)
docker compose up -d --build
```

Esto levanta `db` (Postgres), `backend` (puerto 8080), `frontend` (nginx interno, que sirve el
build de Angular y reenvía `/api/*` al backend — por eso `environment.prod.ts` usa un `apiBase`
relativo en vez de una URL absoluta) y `caddy` (puertos 80/443, HTTPS delante de todo). Los datos
de Postgres quedan en un volumen (`db_data`) que sobrevive a `docker compose down` (usar `down -v`
si realmente se quiere borrar todo).

**HTTPS (obligatorio para el webhook de Mercado Pago en producción):** con `DOMAIN` seteado en el
`.env` a tu dominio real (y ese dominio ya apuntando por DNS a este servidor), Caddy pide y renueva
el certificado de Let's Encrypt solo — no hay nada más que configurar. Sin `DOMAIN` (o en
`localhost`), Caddy sirve igual pero con un certificado local, útil para probar el stack completo
sin dominio todavía. El puerto 4200 (`frontend` directo, sin pasar por Caddy) sigue disponible para
pruebas rápidas en HTTP plano.

### Variables de entorno de producción (además de las de dev)

| Variable | Para qué |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod` para activar Postgres + el resto de lo de arriba |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | Conexión a Postgres (formato JDBC: `jdbc:postgresql://host:5432/db`) |
| `CORS_ALLOWED_ORIGINS` | Dominio(s) del frontend que puede llamar a la API, separados por coma |
| `MP_WEBHOOK_SECRET` | Firma del webhook de Mercado Pago (Panel de MP → tu aplicación → Webhooks). **Obligatoria en producción**: sin ella el webhook acepta notificaciones sin validar quién las mandó |
| `BOOTSTRAP_ADMIN_USERNAME` / `BOOTSTRAP_ADMIN_PASSWORD` | Ver arriba |
| `DOMAIN` | Tu dominio real (ej. `parquelaserranita.com.ar`), apuntado por DNS a este servidor. Con esto, Caddy pide HTTPS solo |
| `CADDY_EMAIL` | Opcional — Let's Encrypt lo usa sólo para avisar si un certificado está por vencer sin renovarse |

### Sin Docker

También se puede compilar y correr directo:

```bash
cd "Back/La Serranita entradas" && ./mvnw -DskipTests package
SPRING_PROFILES_ACTIVE=prod java -jar target/*.jar
```

```bash
cd Front && npm ci && npx ng build --configuration production
# servir Front/dist/Front/browser con cualquier servidor de estáticos que sepa hacer
# fallback de rutas a index.html (SPA), reenviando /api al backend (ver Front/nginx.conf
# como referencia si no se usa el contenedor de nginx que trae el repo)
```

### Limitación conocida

El esquema de Postgres se crea con `ddl-auto=update` (Hibernate genera las tablas a partir de las
entidades): cómodo para arrancar, pero sin el control de una herramienta de migraciones versionada
(Flyway/Liquibase). Si el proyecto sigue creciendo, migrar a eso es lo recomendable.

## Tests

```bash
cd "Back/La Serranita entradas" && ./mvnw test
```

Cubren las reglas de negocio menos obvias: precio por grupo (escalón exacto, sin descuento por
debajo del mínimo, extrapolación por encima del máximo), el cálculo de caja (qué compras cuentan
como efectivo real), la idempotencia de la confirmación de pago, y que sólo se reembolsa lo pagado
online y no usado. `.github/workflows/ci.yml` corre esto (y el build de Angular) en cada push/PR.

## Embeber el módulo `/entradas` en el sitio del parque

El sitio del parque lo pone en un `<iframe>`. Como el iframe queda en otro origen, el sitio no
puede leer la altura del documento embebido por política de mismo origen del navegador — por eso
el módulo avisa su altura por `postMessage` cada vez que cambia (cambio de paso del flujo, error
mostrado, etc.), y el sitio la escucha para ajustar el iframe y que no le quede scroll propio.

Snippet a pegar en la página del sitio:

```html
<iframe
  id="serranita-entradas"
  src="https://<dominio-de-producción>/entradas"
  title="Módulo de compra de entradas"
  scrolling="no"
  style="width: 100%; border: none; display: block; height: 780px;"
></iframe>

<script>
  window.addEventListener('message', (event) => {
    if (event.data?.type === 'la-serranita-alto') {
      document.getElementById('serranita-entradas').style.height = event.data.alto + 'px';
    }
  });
</script>
```

El `height: 780px` inicial es sólo un valor de arranque razonable hasta que llega el primer aviso
de altura (llega casi de inmediato). `preview-iframe.html` en la raíz del repo tiene una página de
ejemplo completa con este mismo patrón, útil para probar el embed sin tocar el sitio real.

## Seguridad

El módulo interno usa JWT: `POST /api/usuarios/login` devuelve un token (válido 8 h) que el
frontend guarda y adjunta en cada request mediante un interceptor. En el backend, un filtro lo
valida y `SecurityConfig` autoriza por rol:

- **Público:** catálogo de entradas, calendario de días abiertos, validación de cupones, alta de
  compras y webhook de Mercado Pago.
- **BOLETERO o ADMIN:** búsqueda de reservas, validación de ingreso y cobro en efectivo.
- **Sólo ADMIN:** días y horarios, cupones, tipos de entrada, precios por grupo y usuarios.
