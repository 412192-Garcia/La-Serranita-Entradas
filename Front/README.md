# Front — La Serranita

Aplicación Angular 21 del sistema de entradas. La documentación general del proyecto (requisitos,
configuración, cómo levantar el backend, usuarios de prueba) está en el
[README de la raíz](../README.md).

## Comandos

```bash
npm install     # dependencias
npm start       # servidor de desarrollo en http://localhost:4200
npm run build   # build de producción en dist/
npm test        # tests unitarios
```

## Estructura

```
src/
├─ environments/       Dirección del backend (único lugar donde se configura)
└─ app/
   ├─ Services/        Acceso HTTP a la API
   ├─ models/          Interfaces compartidas del dominio
   ├─ interceptors/    Adjunta el JWT y cierra sesión ante un 401
   ├─ guards/          Protección de rutas por rol
   ├─ shared/          Componentes usados por más de una pantalla
   │
   │  ── Módulo web (público, se embebe en el sitio del parque) ──
   ├─ entradas/        Contenedor del flujo de compra
   ├─ calendario/      Selección de fecha de visita
   ├─ seleccion-entradas/
   ├─ form-cliente/    Datos del comprador
   ├─ resumen/         Confirmación y forma de pago
   ├─ resultado-pago/  Pantallas de vuelta de Mercado Pago
   │
   │  ── Módulo interno (staff, se carga bajo demanda) ──
   ├─ login/
   ├─ boleteria/       Control de accesos por DNI
   └─ configuracion/   Días, cupones, tipos de entrada y precios por grupo
```

## Notas

- Los componentes son **standalone** y el estado se maneja con **signals**; no hay NgModules.
- Las plantillas usan la sintaxis de control de flujo `@if` / `@for` / `@switch`.
- El módulo público está pensado para verse dentro de un contenedor angosto (~760 px) en el sitio
  del parque, no a pantalla completa.
