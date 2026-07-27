# La Serranita — Sistema de Entradas

Sistema de venta de entradas y control de accesos por DNI para el **Parque Recreativo La Serranita**
(Córdoba, Argentina). Reemplaza la plataforma de ticketing de terceros que usaba el parque, para
eliminar comisiones y agilizar el ingreso en boletería.

Trabajo final de carrera — Tomás Jeremías García Tini (legajo 412192).

## Cómo está organizado

El repositorio tiene dos aplicaciones:

| Carpeta | Qué es | Stack |
| --- | --- | --- |
| `Back/La Serranita entradas/` | API REST | Java 17 · Spring Boot 3.5.8 · Spring Data JPA · H2 |
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

La dirección del backend que usa el frontend se define en `Front/src/environments/environment.ts`.

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

H2 en memoria: el esquema se crea y se destruye en cada arranque (`ddl-auto=create-drop`) y se
puebla desde `src/main/resources/data.sql`. Es decir, **los datos no sobreviven a un reinicio**.

- Consola H2: `http://localhost:8080/h2-console` — JDBC URL `jdbc:h2:mem:testdb`, usuario `sa`, sin contraseña.
- Documentación de la API: `http://localhost:8080/swagger-ui.html`

### Usuarios de prueba

Vienen cargados desde `data.sql` y sirven para entrar al módulo interno:

| Usuario | Contraseña | Rol | Acceso |
| --- | --- | --- | --- |
| `admin` | `admin123` | ADMIN | Boletería + Configuración |
| `boletero.marta` | `boletero123` | BOLETERO | Sólo Boletería |
| `boletero.juan` | `boletero123` | BOLETERO | Sólo Boletería |

## Seguridad

El módulo interno usa JWT: `POST /api/usuarios/login` devuelve un token (válido 8 h) que el
frontend guarda y adjunta en cada request mediante un interceptor. En el backend, un filtro lo
valida y `SecurityConfig` autoriza por rol:

- **Público:** catálogo de entradas, calendario de días abiertos, validación de cupones, alta de
  compras y webhook de Mercado Pago.
- **BOLETERO o ADMIN:** búsqueda de reservas, validación de ingreso y cobro en efectivo.
- **Sólo ADMIN:** días y horarios, cupones, tipos de entrada, precios por grupo y usuarios.
