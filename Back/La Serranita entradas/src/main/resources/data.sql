-- =============================================================================
-- SCRIPT DE CARGA INICIAL DE DATOS (data.sql)
-- Proyecto: Sistema de Entradas - Parque Recreativo La Serranita
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. USUARIOS (Empleados del Parque: Administradores y Boleteros)
-- Contraseñas de prueba (hash bcrypt real): admin/admin123, boletero.marta y
-- boletero.juan comparten boletero123.
-- -----------------------------------------------------------------------------
INSERT INTO usuarios (
    username, password, nombre, apellido, rol, activo,
    fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion
) VALUES
      ('admin', '$2a$10$3M7rO0WklkesAeyyf00aSeK08yXSQSwT.21ZXAIoo2PUsNCUxWHT.', 'Carlos', 'González', 'ADMIN', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM'),
      ('boletero.marta', '$2a$10$swVxIUB0PgsETN9ZpeccCOBIWETtSwOOczoTP7zCSFgRrZWT01h1y', 'Marta', 'Rodríguez', 'BOLETERO', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin', 'admin'),
      ('boletero.juan', '$2a$10$9BZeHMV5216iLDRCNNT5feeoakYM9ZWWDannrEx6/ywX32vIZeYfW', 'Juan', 'Pérez', 'BOLETERO', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin', 'admin');

-- -----------------------------------------------------------------------------
-- 2. TIPOS DE ENTRADA Y EXTRAS
-- ENTRADA: Pase General y Pase Menor
-- EXTRA: Almuerzo / Combo Gastronómico
-- -----------------------------------------------------------------------------
INSERT INTO tipos_entrada (
    id, nombre, descripcion, precio, tipo, activo, obligatorio, entrega_entrada,
    fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion
) VALUES
      (1, 'Pase General', 'Acceso ilimitado a todos los juegos del parque (a partir de 4 años).', 34300.00, 'ENTRADA', true, true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin', 'admin'),
      (2, 'Pase Menor (0 a 3 años)', 'Ingreso gratuito para niños de 0 a 3 años cumplidos (presentando DNI).', 0.00, 'ENTRADA', true, false, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin', 'admin'),
      (3, 'Menú Almuerzo Parque', 'Incluye una Hamburguesa con Jamon y Queso + Papas fritas + Bebida 500ml. Para canjear en la Cantina del Parque.', 12500.00, 'EXTRA', true, false, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin', 'admin');

-- IDs insertados a mano: hay que correr la identidad para que el próximo INSERT
-- generado por la app (POST /api/tipos-entrada) no choque con el id=3 ya usado.
ALTER TABLE tipos_entrada ALTER COLUMN id RESTART WITH 4;

-- -----------------------------------------------------------------------------
-- 3. DESCUENTOS EN EFECTIVO / PROMOCIONES FAMILIARES
-- Aplicables sobre el Pase General (id_tipo_entrada = 1) abonando en Boletería
-- -----------------------------------------------------------------------------
INSERT INTO descuentos_efectivo (
    id_tipo_entrada, cantidad_pases, precio_promocional_total
) VALUES
      (1, 3, 95700.00),
      (1, 4, 126900.00),
      (1, 5, 157800.00),
      (1, 6, 188300.00),
      (1, 7, 218500.00),
      (1, 8, 248300.00),
      (1, 9, 277800.00),
      (1, 10, 307000.00);

-- -----------------------------------------------------------------------------
-- 4. DÍAS DE APERTURA Y AFORO
-- -----------------------------------------------------------------------------
INSERT INTO dias_apertura (
    fecha, abierto,
    fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion
) VALUES
      ('2026-07-25', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin', 'admin'),
      ('2026-07-26', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin', 'admin'),
      ('2026-08-01', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin', 'admin'),
      ('2026-08-02', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin', 'admin'),
      ('2026-08-08', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin', 'admin'),
      ('2026-08-09', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin', 'admin');

-- -----------------------------------------------------------------------------
-- 4b. MAS DIAS DE APERTURA (fines de semana de julio, dentro de los ultimos 30 dias
-- que muestra Reportes por defecto) para tener mas volumen de ventas de muestra.
-- -----------------------------------------------------------------------------
INSERT INTO dias_apertura (
    fecha, abierto,
    fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion
) VALUES
      ('2026-07-04', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin', 'admin'),
      ('2026-07-05', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin', 'admin'),
      ('2026-07-11', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin', 'admin'),
      ('2026-07-12', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin', 'admin'),
      ('2026-07-18', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin', 'admin'),
      ('2026-07-19', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin', 'admin');

-- -----------------------------------------------------------------------------
-- 5. FAMILIAS DE CUPONES Y CUPONES DE PRUEBA
-- -----------------------------------------------------------------------------
INSERT INTO familias_cupones (
    id, nombre, prefijo, descripcion,
    fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion
) VALUES
    (1, 'Promociones Temporada Invierno', 'INV', 'Cupones promocionales de invierno', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin', 'admin');

ALTER TABLE familias_cupones ALTER COLUMN id RESTART WITH 2;

INSERT INTO cupones (
    codigo, porcentaje_descuento, monto_descuento, fecha_expiracion, usos_maximos, usos_actuales, activo,
    fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion
) VALUES
      ('SERRANITA10', 10.00, NULL, '2026-12-31', 100, 0, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin', 'admin'),
      ('VACACIONES15', 15.00, NULL, '2026-08-31', 50, 0, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin', 'admin');

-- -----------------------------------------------------------------------------
-- 6. CLIENTES Y COMPRAS DE PRUEBA
-- -----------------------------------------------------------------------------
INSERT INTO clientes (
    id, dni, nombre, apellido,
    fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion
) VALUES
    (1, '35123456', 'Mariano', 'López', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'WEB_GUEST', 'WEB_GUEST'),
    (2, '30111222', 'Laura', 'Fernández', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'WEB_GUEST', 'WEB_GUEST'),
    (3, '28999888', 'Diego', 'Martínez', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'WEB_GUEST', 'WEB_GUEST'),
    (4, '40555666', 'Sofía', 'Gómez', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'WEB_GUEST', 'WEB_GUEST'),
    (5, '33222111', 'Pedro', 'Ramírez', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'WEB_GUEST', 'WEB_GUEST'),
    (6, '25444333', 'Valentina', 'Suárez', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'WEB_GUEST', 'WEB_GUEST');

-- -----------------------------------------------------------------------------
-- 6b. MAS CLIENTES, para tener variedad de compradores en las nuevas compras
-- -----------------------------------------------------------------------------
INSERT INTO clientes (
    id, dni, nombre, apellido,
    fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion
) VALUES
    (7, '21123444', 'Julieta', 'Acosta', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'WEB_GUEST', 'WEB_GUEST'),
    (8, '27888555', 'Nicolás', 'Ibarra', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'WEB_GUEST', 'WEB_GUEST'),
    (9, '32777222', 'Camila', 'Torres', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'WEB_GUEST', 'WEB_GUEST'),
    (10, '29666111', 'Federico', 'Molina', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'WEB_GUEST', 'WEB_GUEST'),
    (11, '24555999', 'Antonella', 'Rojas', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'WEB_GUEST', 'WEB_GUEST'),
    (12, '31444888', 'Gonzalo', 'Herrera', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'WEB_GUEST', 'WEB_GUEST'),
    (13, '26333777', 'Milagros', 'Castro', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'WEB_GUEST', 'WEB_GUEST'),
    (14, '38222666', 'Tomás', 'Vega', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'WEB_GUEST', 'WEB_GUEST'),
    (15, '22111555', 'Agostina', 'Paz', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'WEB_GUEST', 'WEB_GUEST'),
    (16, '34999333', 'Ezequiel', 'Luna', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'WEB_GUEST', 'WEB_GUEST'),
    (17, '23888222', 'Florencia', 'Ríos', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'WEB_GUEST', 'WEB_GUEST'),
    (18, '36777111', 'Ignacio', 'Campos', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'WEB_GUEST', 'WEB_GUEST'),
    (19, '28666444', 'Julieta', 'Sosa', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'WEB_GUEST', 'WEB_GUEST'),
    (20, '39555777', 'Bruno', 'Medina', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'WEB_GUEST', 'WEB_GUEST'),
    (21, '25444222', 'Martina', 'Ortiz', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'WEB_GUEST', 'WEB_GUEST'),
    (22, '30333999', 'Santino', 'Aguirre', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'WEB_GUEST', 'WEB_GUEST'),
    (23, '27222888', 'Delfina', 'Cabrera', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'WEB_GUEST', 'WEB_GUEST'),
    (24, '33111666', 'Lautaro', 'Silva', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'WEB_GUEST', 'WEB_GUEST');

-- Sin esto, el primer cliente que crea la app en tiempo real (DNI distinto a los ya sembrados)
-- choca contra un id ya insertado a mano: ese era el bug real del stacktrace.
ALTER TABLE clientes ALTER COLUMN id RESTART WITH 25;

-- Variedad de estados/fechas para poder probar Boletería y Configuración sin tener que
-- crear compras a mano: RESERVADO_EFECTIVO, APROBADO, USADO, PENDIENTE_PAGO, CANCELADO y un regalo (sin fecha).
INSERT INTO compras (
    id, id_cliente, contact_email, contact_phone, fecha_visita, codigo_reserva, monto_total, descuento_aplicado, estado, forma_pago,
    id_usuario_validador, fecha_validacion,
    fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion
) VALUES
    (1001, 1, 'marianolopez@gmail.com', '351-5551234', '2026-07-25', '260725-1', 120700.00, 7200.00, 'RESERVADO_EFECTIVO', 'EFECTIVO_BOLETERIA',
     NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'WEB_GUEST', 'WEB_GUEST'),
    (1002, 2, 'laura.fernandez@gmail.com', '351-5552345', '2026-07-25', '260725-2', 81100.00, 0.00, 'APROBADO', 'MERCADO_PAGO',
     NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'WEB_GUEST', 'WEB_GUEST'),
    (1003, 3, 'diego.martinez@gmail.com', '351-5553456', '2026-07-25', '260725-3', 46800.00, 0.00, 'USADO', 'EFECTIVO_BOLETERIA',
     2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'WEB_GUEST', 'WEB_GUEST'),
    (1004, 4, 'sofia.gomez@gmail.com', '351-5554567', '2026-07-26', '260726-1', 68600.00, 0.00, 'RESERVADO_EFECTIVO', 'EFECTIVO_BOLETERIA',
     NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'WEB_GUEST', 'WEB_GUEST'),
    (1005, 5, 'pedro.ramirez@gmail.com', '351-5555678', '2026-08-01', '260801-1', 34300.00, 0.00, 'PENDIENTE_PAGO', 'MERCADO_PAGO',
     NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'WEB_GUEST', 'WEB_GUEST'),
    (1006, 6, 'valentina.suarez@gmail.com', '351-5556789', '2026-08-02', '260802-1', 126900.00, 10300.00, 'CANCELADO', 'MERCADO_PAGO',
     NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'WEB_GUEST', 'WEB_GUEST'),
    (1007, 2, 'laura.fernandez@gmail.com', '351-5552345', NULL, 'REGALO-1', 68600.00, 0.00, 'APROBADO', 'MERCADO_PAGO',
     NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'WEB_GUEST', 'WEB_GUEST');

INSERT INTO compras_detalle (
    id_compra, id_tipo_entrada, cantidad,
    fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion
) VALUES
      (1001, 1, 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'WEB_GUEST', 'WEB_GUEST'),
      (1001, 3, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'WEB_GUEST', 'WEB_GUEST'),
      (1002, 1, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'WEB_GUEST', 'WEB_GUEST'),
      (1002, 3, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'WEB_GUEST', 'WEB_GUEST'),
      (1003, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'WEB_GUEST', 'WEB_GUEST'),
      (1003, 2, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'WEB_GUEST', 'WEB_GUEST'),
      (1003, 3, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'WEB_GUEST', 'WEB_GUEST'),
      (1004, 1, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'WEB_GUEST', 'WEB_GUEST'),
      (1005, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'WEB_GUEST', 'WEB_GUEST'),
      (1005, 2, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'WEB_GUEST', 'WEB_GUEST'),
      (1006, 1, 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'WEB_GUEST', 'WEB_GUEST'),
      (1007, 1, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'WEB_GUEST', 'WEB_GUEST');

-- -----------------------------------------------------------------------------
-- 7. CAJAS (turnos de boletería) YA CERRADAS, con sus retiros, para poblar el
-- reporte de "Cajas cerradas". Un turno por día de venta en puerta, alternando
-- entre las dos boleteras/os (usuario 2 = Marta, usuario 3 = Juan).
-- -----------------------------------------------------------------------------
INSERT INTO cajas (
    id, id_usuario, fecha_apertura, monto_inicial, fecha_cierre, monto_contado, monto_esperado, diferencia,
    fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion
) VALUES
    (1, 2, '2026-07-04 08:30:00', 5000.00, '2026-07-04 18:20:00', 63100.00, 63600.00, -500.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin', 'admin'),
    (2, 3, '2026-07-05 08:30:00', 5000.00, '2026-07-05 18:20:00', 58600.00, 58600.00, 0.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin', 'admin'),
    (3, 2, '2026-07-11 08:30:00', 5000.00, '2026-07-11 18:20:00', 66800.00, 65600.00, 1200.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin', 'admin'),
    (4, 3, '2026-07-12 08:30:00', 5000.00, '2026-07-12 18:20:00', 50600.00, 53600.00, -3000.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin', 'admin'),
    (5, 2, '2026-07-18 08:30:00', 5000.00, '2026-07-18 18:20:00', 68600.00, 68600.00, 0.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin', 'admin'),
    (6, 3, '2026-07-19 08:30:00', 5000.00, '2026-07-19 18:20:00', 62000.00, 61600.00, 400.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin', 'admin'),
    (7, 2, '2026-07-25 08:30:00', 5000.00, '2026-07-25 18:20:00', 61400.00, 63600.00, -2200.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin', 'admin'),
    (8, 3, '2026-07-26 08:30:00', 5000.00, '2026-07-26 18:20:00', 56400.00, 55600.00, 800.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin', 'admin');

ALTER TABLE cajas ALTER COLUMN id RESTART WITH 9;

INSERT INTO retiros_caja (
    id, id_caja, monto, motivo, fecha,
    fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion
) VALUES
    (1, 1, 10000.00, 'Resguardo en caja fuerte', '2026-07-04 16:00:00', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin', 'admin'),
    (2, 2, 15000.00, 'Resguardo en caja fuerte', '2026-07-05 16:00:00', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin', 'admin'),
    (3, 3, 8000.00, 'Resguardo en caja fuerte', '2026-07-11 16:00:00', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin', 'admin'),
    (4, 4, 20000.00, 'Resguardo en caja fuerte', '2026-07-12 16:00:00', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin', 'admin'),
    (5, 5, 5000.00, 'Resguardo en caja fuerte', '2026-07-18 16:00:00', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin', 'admin'),
    (6, 6, 12000.00, 'Resguardo en caja fuerte', '2026-07-19 16:00:00', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin', 'admin'),
    (7, 7, 10000.00, 'Resguardo en caja fuerte', '2026-07-25 16:00:00', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin', 'admin'),
    (8, 8, 18000.00, 'Resguardo en caja fuerte', '2026-07-26 16:00:00', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin', 'admin');

ALTER TABLE retiros_caja ALTER COLUMN id RESTART WITH 9;

-- -----------------------------------------------------------------------------
-- 8. MAS COMPRAS ANTICIPADAS (USADO / APROBADO-sin-validar / CANCELADO-REEMBOLSADA
-- por cada uno de los 6 nuevos días abiertos), para que los gráficos de Reportes
-- tengan volumen real y separable por origen.
-- -----------------------------------------------------------------------------
INSERT INTO compras (
    id, id_cliente, contact_email, contact_phone, fecha_visita, codigo_reserva, monto_total, descuento_aplicado, estado, forma_pago,
    id_usuario_validador, fecha_validacion,
    fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion
) VALUES
    (1008, 7, 'cliente7@gmail.com', '351-5000007', '2026-07-04', '260704-1', 81100.00, 0.00, 'USADO', 'MERCADO_PAGO', 2, '2026-07-04 10:40:00', '2026-06-29 10:15:00', '2026-07-04 10:40:00', 'WEB_GUEST', 'WEB_GUEST'),
    (1009, 8, 'cliente8@gmail.com', '351-5000008', '2026-07-04', '260704-2', 34300.00, 0.00, 'APROBADO', 'MERCADO_PAGO', NULL, NULL, '2026-06-27 20:15:00', '2026-06-27 20:15:00', 'WEB_GUEST', 'WEB_GUEST'),
    (1010, 9, 'cliente9@gmail.com', '351-5000009', '2026-07-04', '260704-3', 137200.00, 0.00, 'REEMBOLSADA', 'MERCADO_PAGO', NULL, NULL, '2026-06-24 14:30:00', '2026-06-24 14:30:00', 'WEB_GUEST', 'WEB_GUEST'),
    (1011, 10, 'cliente10@gmail.com', '351-5000010', '2026-07-05', '260705-1', 81100.00, 0.00, 'USADO', 'MERCADO_PAGO', 2, '2026-07-05 10:40:00', '2026-06-30 10:15:00', '2026-07-05 10:40:00', 'WEB_GUEST', 'WEB_GUEST'),
    (1012, 11, 'cliente11@gmail.com', '351-5000011', '2026-07-05', '260705-2', 34300.00, 0.00, 'APROBADO', 'MERCADO_PAGO', NULL, NULL, '2026-06-28 20:15:00', '2026-06-28 20:15:00', 'WEB_GUEST', 'WEB_GUEST'),
    (1013, 12, 'cliente12@gmail.com', '351-5000012', '2026-07-05', '260705-3', 137200.00, 0.00, 'CANCELADO', 'MERCADO_PAGO', NULL, NULL, '2026-06-25 14:30:00', '2026-06-25 14:30:00', 'WEB_GUEST', 'WEB_GUEST'),
    (1014, 13, 'cliente13@gmail.com', '351-5000013', '2026-07-11', '260711-1', 81100.00, 0.00, 'USADO', 'MERCADO_PAGO', 2, '2026-07-11 10:40:00', '2026-07-06 10:15:00', '2026-07-11 10:40:00', 'WEB_GUEST', 'WEB_GUEST'),
    (1015, 14, 'cliente14@gmail.com', '351-5000014', '2026-07-11', '260711-2', 34300.00, 0.00, 'APROBADO', 'MERCADO_PAGO', NULL, NULL, '2026-07-04 20:15:00', '2026-07-04 20:15:00', 'WEB_GUEST', 'WEB_GUEST'),
    (1016, 15, 'cliente15@gmail.com', '351-5000015', '2026-07-11', '260711-3', 137200.00, 0.00, 'REEMBOLSADA', 'MERCADO_PAGO', NULL, NULL, '2026-07-01 14:30:00', '2026-07-01 14:30:00', 'WEB_GUEST', 'WEB_GUEST'),
    (1017, 16, 'cliente16@gmail.com', '351-5000016', '2026-07-12', '260712-1', 81100.00, 0.00, 'USADO', 'MERCADO_PAGO', 2, '2026-07-12 10:40:00', '2026-07-07 10:15:00', '2026-07-12 10:40:00', 'WEB_GUEST', 'WEB_GUEST'),
    (1018, 17, 'cliente17@gmail.com', '351-5000017', '2026-07-12', '260712-2', 34300.00, 0.00, 'APROBADO', 'MERCADO_PAGO', NULL, NULL, '2026-07-05 20:15:00', '2026-07-05 20:15:00', 'WEB_GUEST', 'WEB_GUEST'),
    (1019, 18, 'cliente18@gmail.com', '351-5000018', '2026-07-12', '260712-3', 137200.00, 0.00, 'CANCELADO', 'MERCADO_PAGO', NULL, NULL, '2026-07-02 14:30:00', '2026-07-02 14:30:00', 'WEB_GUEST', 'WEB_GUEST'),
    (1020, 19, 'cliente19@gmail.com', '351-5000019', '2026-07-18', '260718-1', 81100.00, 0.00, 'USADO', 'MERCADO_PAGO', 2, '2026-07-18 10:40:00', '2026-07-13 10:15:00', '2026-07-18 10:40:00', 'WEB_GUEST', 'WEB_GUEST'),
    (1021, 20, 'cliente20@gmail.com', '351-5000020', '2026-07-18', '260718-2', 34300.00, 0.00, 'APROBADO', 'MERCADO_PAGO', NULL, NULL, '2026-07-11 20:15:00', '2026-07-11 20:15:00', 'WEB_GUEST', 'WEB_GUEST'),
    (1022, 21, 'cliente21@gmail.com', '351-5000021', '2026-07-18', '260718-3', 137200.00, 0.00, 'REEMBOLSADA', 'MERCADO_PAGO', NULL, NULL, '2026-07-08 14:30:00', '2026-07-08 14:30:00', 'WEB_GUEST', 'WEB_GUEST'),
    (1023, 22, 'cliente22@gmail.com', '351-5000022', '2026-07-19', '260719-1', 81100.00, 0.00, 'USADO', 'MERCADO_PAGO', 2, '2026-07-19 10:40:00', '2026-07-14 10:15:00', '2026-07-19 10:40:00', 'WEB_GUEST', 'WEB_GUEST'),
    (1024, 23, 'cliente23@gmail.com', '351-5000023', '2026-07-19', '260719-2', 34300.00, 0.00, 'APROBADO', 'MERCADO_PAGO', NULL, NULL, '2026-07-12 20:15:00', '2026-07-12 20:15:00', 'WEB_GUEST', 'WEB_GUEST'),
    (1025, 24, 'cliente24@gmail.com', '351-5000024', '2026-07-19', '260719-3', 137200.00, 0.00, 'CANCELADO', 'MERCADO_PAGO', NULL, NULL, '2026-07-09 14:30:00', '2026-07-09 14:30:00', 'WEB_GUEST', 'WEB_GUEST');

-- -----------------------------------------------------------------------------
-- 9. MAS COMPRAS DE VENTA EN PUERTA (POS), 3 por día (efectivo/tarjeta/QR),
-- vinculadas a la caja de esa jornada mediante id_caja.
-- -----------------------------------------------------------------------------
INSERT INTO compras (
    id, id_cliente, contact_email, contact_phone, fecha_visita, codigo_reserva, monto_total, descuento_aplicado, estado, forma_pago,
    id_usuario_validador, fecha_validacion,
    fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion, id_caja
) VALUES
    (1026, 7, 'cliente7@gmail.com', '351-5000007', '2026-07-04', '260704-4', 68600.00, 0.00, 'VENDIDO_EN_PUERTA', 'EFECTIVO_BOLETERIA', 2, '2026-07-04 09:15:00', '2026-07-04 09:15:00', '2026-07-04 09:15:00', 'WEB_GUEST', 'WEB_GUEST', 1),
    (1027, 8, 'cliente8@gmail.com', '351-5000008', '2026-07-04', '260704-5', 102900.00, 0.00, 'VENDIDO_EN_PUERTA', 'TARJETA', 2, '2026-07-04 11:40:00', '2026-07-04 11:40:00', '2026-07-04 11:40:00', 'WEB_GUEST', 'WEB_GUEST', 1),
    (1028, 9, 'cliente9@gmail.com', '351-5000009', '2026-07-04', '260704-6', 34300.00, 0.00, 'VENDIDO_EN_PUERTA', 'MERCADO_PAGO_QR', 2, '2026-07-04 16:05:00', '2026-07-04 16:05:00', '2026-07-04 16:05:00', 'WEB_GUEST', 'WEB_GUEST', 1),
    (1029, 10, 'cliente10@gmail.com', '351-5000010', '2026-07-05', '260705-4', 68600.00, 0.00, 'VENDIDO_EN_PUERTA', 'EFECTIVO_BOLETERIA', 3, '2026-07-05 09:15:00', '2026-07-05 09:15:00', '2026-07-05 09:15:00', 'WEB_GUEST', 'WEB_GUEST', 2),
    (1030, 11, 'cliente11@gmail.com', '351-5000011', '2026-07-05', '260705-5', 102900.00, 0.00, 'VENDIDO_EN_PUERTA', 'TARJETA', 3, '2026-07-05 11:40:00', '2026-07-05 11:40:00', '2026-07-05 11:40:00', 'WEB_GUEST', 'WEB_GUEST', 2),
    (1031, 12, 'cliente12@gmail.com', '351-5000012', '2026-07-05', '260705-6', 34300.00, 0.00, 'VENDIDO_EN_PUERTA', 'MERCADO_PAGO_QR', 3, '2026-07-05 16:05:00', '2026-07-05 16:05:00', '2026-07-05 16:05:00', 'WEB_GUEST', 'WEB_GUEST', 2),
    (1032, 13, 'cliente13@gmail.com', '351-5000013', '2026-07-11', '260711-4', 68600.00, 0.00, 'VENDIDO_EN_PUERTA', 'EFECTIVO_BOLETERIA', 2, '2026-07-11 09:15:00', '2026-07-11 09:15:00', '2026-07-11 09:15:00', 'WEB_GUEST', 'WEB_GUEST', 3),
    (1033, 14, 'cliente14@gmail.com', '351-5000014', '2026-07-11', '260711-5', 102900.00, 0.00, 'VENDIDO_EN_PUERTA', 'TARJETA', 2, '2026-07-11 11:40:00', '2026-07-11 11:40:00', '2026-07-11 11:40:00', 'WEB_GUEST', 'WEB_GUEST', 3),
    (1034, 15, 'cliente15@gmail.com', '351-5000015', '2026-07-11', '260711-6', 34300.00, 0.00, 'VENDIDO_EN_PUERTA', 'MERCADO_PAGO_QR', 2, '2026-07-11 16:05:00', '2026-07-11 16:05:00', '2026-07-11 16:05:00', 'WEB_GUEST', 'WEB_GUEST', 3),
    (1035, 16, 'cliente16@gmail.com', '351-5000016', '2026-07-12', '260712-4', 68600.00, 0.00, 'VENDIDO_EN_PUERTA', 'EFECTIVO_BOLETERIA', 3, '2026-07-12 09:15:00', '2026-07-12 09:15:00', '2026-07-12 09:15:00', 'WEB_GUEST', 'WEB_GUEST', 4),
    (1036, 17, 'cliente17@gmail.com', '351-5000017', '2026-07-12', '260712-5', 102900.00, 0.00, 'VENDIDO_EN_PUERTA', 'TARJETA', 3, '2026-07-12 11:40:00', '2026-07-12 11:40:00', '2026-07-12 11:40:00', 'WEB_GUEST', 'WEB_GUEST', 4),
    (1037, 18, 'cliente18@gmail.com', '351-5000018', '2026-07-12', '260712-6', 34300.00, 0.00, 'VENDIDO_EN_PUERTA', 'MERCADO_PAGO_QR', 3, '2026-07-12 16:05:00', '2026-07-12 16:05:00', '2026-07-12 16:05:00', 'WEB_GUEST', 'WEB_GUEST', 4),
    (1038, 19, 'cliente19@gmail.com', '351-5000019', '2026-07-18', '260718-4', 68600.00, 0.00, 'VENDIDO_EN_PUERTA', 'EFECTIVO_BOLETERIA', 2, '2026-07-18 09:15:00', '2026-07-18 09:15:00', '2026-07-18 09:15:00', 'WEB_GUEST', 'WEB_GUEST', 5),
    (1039, 20, 'cliente20@gmail.com', '351-5000020', '2026-07-18', '260718-5', 102900.00, 0.00, 'VENDIDO_EN_PUERTA', 'TARJETA', 2, '2026-07-18 11:40:00', '2026-07-18 11:40:00', '2026-07-18 11:40:00', 'WEB_GUEST', 'WEB_GUEST', 5),
    (1040, 21, 'cliente21@gmail.com', '351-5000021', '2026-07-18', '260718-6', 34300.00, 0.00, 'VENDIDO_EN_PUERTA', 'MERCADO_PAGO_QR', 2, '2026-07-18 16:05:00', '2026-07-18 16:05:00', '2026-07-18 16:05:00', 'WEB_GUEST', 'WEB_GUEST', 5),
    (1041, 22, 'cliente22@gmail.com', '351-5000022', '2026-07-19', '260719-4', 68600.00, 0.00, 'VENDIDO_EN_PUERTA', 'EFECTIVO_BOLETERIA', 3, '2026-07-19 09:15:00', '2026-07-19 09:15:00', '2026-07-19 09:15:00', 'WEB_GUEST', 'WEB_GUEST', 6),
    (1042, 23, 'cliente23@gmail.com', '351-5000023', '2026-07-19', '260719-5', 102900.00, 0.00, 'VENDIDO_EN_PUERTA', 'TARJETA', 3, '2026-07-19 11:40:00', '2026-07-19 11:40:00', '2026-07-19 11:40:00', 'WEB_GUEST', 'WEB_GUEST', 6),
    (1043, 24, 'cliente24@gmail.com', '351-5000024', '2026-07-19', '260719-6', 34300.00, 0.00, 'VENDIDO_EN_PUERTA', 'MERCADO_PAGO_QR', 3, '2026-07-19 16:05:00', '2026-07-19 16:05:00', '2026-07-19 16:05:00', 'WEB_GUEST', 'WEB_GUEST', 6),
    (1044, 7, 'cliente7@gmail.com', '351-5000007', '2026-07-25', '260725-4', 68600.00, 0.00, 'VENDIDO_EN_PUERTA', 'EFECTIVO_BOLETERIA', 2, '2026-07-25 09:15:00', '2026-07-25 09:15:00', '2026-07-25 09:15:00', 'WEB_GUEST', 'WEB_GUEST', 7),
    (1045, 8, 'cliente8@gmail.com', '351-5000008', '2026-07-25', '260725-5', 102900.00, 0.00, 'VENDIDO_EN_PUERTA', 'TARJETA', 2, '2026-07-25 11:40:00', '2026-07-25 11:40:00', '2026-07-25 11:40:00', 'WEB_GUEST', 'WEB_GUEST', 7),
    (1046, 9, 'cliente9@gmail.com', '351-5000009', '2026-07-25', '260725-6', 34300.00, 0.00, 'VENDIDO_EN_PUERTA', 'MERCADO_PAGO_QR', 2, '2026-07-25 16:05:00', '2026-07-25 16:05:00', '2026-07-25 16:05:00', 'WEB_GUEST', 'WEB_GUEST', 7),
    (1047, 10, 'cliente10@gmail.com', '351-5000010', '2026-07-26', '260726-2', 68600.00, 0.00, 'VENDIDO_EN_PUERTA', 'EFECTIVO_BOLETERIA', 3, '2026-07-26 09:15:00', '2026-07-26 09:15:00', '2026-07-26 09:15:00', 'WEB_GUEST', 'WEB_GUEST', 8),
    (1048, 11, 'cliente11@gmail.com', '351-5000011', '2026-07-26', '260726-3', 102900.00, 0.00, 'VENDIDO_EN_PUERTA', 'TARJETA', 3, '2026-07-26 11:40:00', '2026-07-26 11:40:00', '2026-07-26 11:40:00', 'WEB_GUEST', 'WEB_GUEST', 8),
    (1049, 12, 'cliente12@gmail.com', '351-5000012', '2026-07-26', '260726-4', 34300.00, 0.00, 'VENDIDO_EN_PUERTA', 'MERCADO_PAGO_QR', 3, '2026-07-26 16:05:00', '2026-07-26 16:05:00', '2026-07-26 16:05:00', 'WEB_GUEST', 'WEB_GUEST', 8);

-- Sin esto, cuando la app crea una compra en tiempo real (POS o reserva online) podría
-- chocar contra alguno de los ids insertados a mano de esta tanda de datos de muestra.
ALTER TABLE compras ALTER COLUMN id RESTART WITH 1050;

INSERT INTO compras_detalle (
    id_compra, id_tipo_entrada, cantidad,
    fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion
) VALUES
      (1008, 1, 2, '2026-06-29 10:15:00', '2026-06-29 10:15:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1008, 3, 1, '2026-06-29 10:15:00', '2026-06-29 10:15:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1009, 1, 1, '2026-06-27 20:15:00', '2026-06-27 20:15:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1009, 2, 1, '2026-06-27 20:15:00', '2026-06-27 20:15:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1010, 1, 4, '2026-06-24 14:30:00', '2026-06-24 14:30:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1011, 1, 2, '2026-06-30 10:15:00', '2026-06-30 10:15:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1011, 3, 1, '2026-06-30 10:15:00', '2026-06-30 10:15:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1012, 1, 1, '2026-06-28 20:15:00', '2026-06-28 20:15:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1012, 2, 1, '2026-06-28 20:15:00', '2026-06-28 20:15:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1013, 1, 4, '2026-06-25 14:30:00', '2026-06-25 14:30:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1014, 1, 2, '2026-07-06 10:15:00', '2026-07-06 10:15:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1014, 3, 1, '2026-07-06 10:15:00', '2026-07-06 10:15:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1015, 1, 1, '2026-07-04 20:15:00', '2026-07-04 20:15:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1015, 2, 1, '2026-07-04 20:15:00', '2026-07-04 20:15:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1016, 1, 4, '2026-07-01 14:30:00', '2026-07-01 14:30:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1017, 1, 2, '2026-07-07 10:15:00', '2026-07-07 10:15:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1017, 3, 1, '2026-07-07 10:15:00', '2026-07-07 10:15:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1018, 1, 1, '2026-07-05 20:15:00', '2026-07-05 20:15:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1018, 2, 1, '2026-07-05 20:15:00', '2026-07-05 20:15:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1019, 1, 4, '2026-07-02 14:30:00', '2026-07-02 14:30:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1020, 1, 2, '2026-07-13 10:15:00', '2026-07-13 10:15:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1020, 3, 1, '2026-07-13 10:15:00', '2026-07-13 10:15:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1021, 1, 1, '2026-07-11 20:15:00', '2026-07-11 20:15:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1021, 2, 1, '2026-07-11 20:15:00', '2026-07-11 20:15:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1022, 1, 4, '2026-07-08 14:30:00', '2026-07-08 14:30:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1023, 1, 2, '2026-07-14 10:15:00', '2026-07-14 10:15:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1023, 3, 1, '2026-07-14 10:15:00', '2026-07-14 10:15:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1024, 1, 1, '2026-07-12 20:15:00', '2026-07-12 20:15:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1024, 2, 1, '2026-07-12 20:15:00', '2026-07-12 20:15:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1025, 1, 4, '2026-07-09 14:30:00', '2026-07-09 14:30:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1026, 1, 2, '2026-07-04 09:15:00', '2026-07-04 09:15:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1027, 1, 3, '2026-07-04 11:40:00', '2026-07-04 11:40:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1028, 1, 1, '2026-07-04 16:05:00', '2026-07-04 16:05:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1028, 2, 1, '2026-07-04 16:05:00', '2026-07-04 16:05:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1029, 1, 2, '2026-07-05 09:15:00', '2026-07-05 09:15:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1030, 1, 3, '2026-07-05 11:40:00', '2026-07-05 11:40:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1031, 1, 1, '2026-07-05 16:05:00', '2026-07-05 16:05:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1031, 2, 1, '2026-07-05 16:05:00', '2026-07-05 16:05:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1032, 1, 2, '2026-07-11 09:15:00', '2026-07-11 09:15:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1033, 1, 3, '2026-07-11 11:40:00', '2026-07-11 11:40:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1034, 1, 1, '2026-07-11 16:05:00', '2026-07-11 16:05:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1034, 2, 1, '2026-07-11 16:05:00', '2026-07-11 16:05:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1035, 1, 2, '2026-07-12 09:15:00', '2026-07-12 09:15:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1036, 1, 3, '2026-07-12 11:40:00', '2026-07-12 11:40:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1037, 1, 1, '2026-07-12 16:05:00', '2026-07-12 16:05:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1037, 2, 1, '2026-07-12 16:05:00', '2026-07-12 16:05:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1038, 1, 2, '2026-07-18 09:15:00', '2026-07-18 09:15:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1039, 1, 3, '2026-07-18 11:40:00', '2026-07-18 11:40:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1040, 1, 1, '2026-07-18 16:05:00', '2026-07-18 16:05:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1040, 2, 1, '2026-07-18 16:05:00', '2026-07-18 16:05:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1041, 1, 2, '2026-07-19 09:15:00', '2026-07-19 09:15:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1042, 1, 3, '2026-07-19 11:40:00', '2026-07-19 11:40:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1043, 1, 1, '2026-07-19 16:05:00', '2026-07-19 16:05:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1043, 2, 1, '2026-07-19 16:05:00', '2026-07-19 16:05:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1044, 1, 2, '2026-07-25 09:15:00', '2026-07-25 09:15:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1045, 1, 3, '2026-07-25 11:40:00', '2026-07-25 11:40:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1046, 1, 1, '2026-07-25 16:05:00', '2026-07-25 16:05:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1046, 2, 1, '2026-07-25 16:05:00', '2026-07-25 16:05:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1047, 1, 2, '2026-07-26 09:15:00', '2026-07-26 09:15:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1048, 1, 3, '2026-07-26 11:40:00', '2026-07-26 11:40:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1049, 1, 1, '2026-07-26 16:05:00', '2026-07-26 16:05:00', 'WEB_GUEST', 'WEB_GUEST'),
      (1049, 2, 1, '2026-07-26 16:05:00', '2026-07-26 16:05:00', 'WEB_GUEST', 'WEB_GUEST');