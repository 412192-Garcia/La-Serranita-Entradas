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
    id, nombre, descripcion, precio, tipo, activo, obligatorio,
    fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion
) VALUES
      (1, 'Pase General', 'Acceso ilimitado a todos los juegos del parque (a partir de 4 años).', 34300.00, 'ENTRADA', true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin', 'admin'),
      (2, 'Pase Menor (0 a 3 años)', 'Ingreso gratuito para niños de 0 a 3 años cumplidos (presentando DNI).', 0.00, 'ENTRADA', true, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin', 'admin'),
      (3, 'Menú Almuerzo Parque', 'Vale gastronómico por plato principal + bebida + postre en el patio de comidas.', 12500.00, 'EXTRA', true, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin', 'admin');

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

-- Sin esto, el primer cliente que crea la app en tiempo real (DNI distinto a los ya sembrados)
-- choca contra un id ya insertado a mano: ese era el bug real del stacktrace.
ALTER TABLE clientes ALTER COLUMN id RESTART WITH 7;

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