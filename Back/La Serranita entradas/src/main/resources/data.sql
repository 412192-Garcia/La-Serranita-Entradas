-- =============================================================================
-- 1. USUARIOS (ADMIN, BOLETERO)
-- Contraseñas de prueba (en producción usar Bcrypt)
-- =============================================================================
INSERT INTO usuarios (username, password, nombre, apellido, rol, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion)
VALUES
    ('admin.serrana', 'admin2026', 'Laura', 'Mendoza', 'ADMIN', true, NOW(), NOW(), 'system', 'system'),
    ('admin.central', 'root1234', 'Sandro', 'Gómez', 'ADMIN', true, NOW(), NOW(), 'system', 'system'),
    ('boletero.turnom', 'passm123', 'Juan Pablo', 'Pérez', 'BOLETERO', true, NOW(), NOW(), 'system', 'system'),
    ('boletero.turnot', 'passt123', 'Milagros', 'Benítez', 'BOLETERO', true, NOW(), NOW(), 'system', 'system'),
    ('boletero.franco', 'passf123', 'Lucas', 'Díaz', 'BOLETERO', false, NOW(), NOW(), 'system', 'system'); -- Inactivo de momento

-- =============================================================================
-- 2. CLIENTES
-- =============================================================================
INSERT INTO clientes (dni, nombre, apellido, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion)
VALUES
    ('11111111', 'Carlos', 'Gómez', NOW(), NOW(), 'system', 'system'),
    ('22222222', 'Ana Maria', 'Rodríguez', NOW(), NOW(), 'system', 'system'),
    ('33333333', 'Mariana', 'López', NOW(), NOW(), 'system', 'system'),
    ('44444444', 'Diego', 'Maradona', NOW(), NOW(), 'system', 'system'),
    ('55555555', 'Florencia', 'Fernández', NOW(), NOW(), 'system', 'system'),
    ('66666666', 'Javier', 'Milei', NOW(), NOW(), 'system', 'system'),
    ('77777777', 'Sofía', 'Martínez', NOW(), NOW(), 'system', 'system'),
    ('88888888', 'Bautista', 'González', NOW(), NOW(), 'system', 'system'),
    ('99999999', 'Valentina', 'Romero', NOW(), NOW(), 'system', 'system'),
    ('12345678', 'Esteban', 'Quito', NOW(), NOW(), 'system', 'system');

-- =============================================================================
-- 3. TIPOS DE ENTRADA
-- =============================================================================
INSERT INTO tipos_entrada (nombre, descripcion, precio, activo, tipo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion)
VALUES
    ('General Adulto', 'Entrada estándar para mayores de 12 años', 1500.00, true, 'entrada', NOW(), NOW(), 'system', 'system'),
    ('General Menor', 'Entrada reducida para niños de 4 a 12 años', 800.00, true, 'entrada', NOW(), NOW(), 'system', 'system'),
    ('Jubilados / Pensionados', 'Descuento para la tercera edad presentando acreditación', 1000.00, true, 'entrada', NOW(), NOW(), 'system', 'system'),
    ('Pase Familiar (4 pers)', 'Combo especial: 2 Adultos + 2 Menores', 4000.00, true, 'entrada', NOW(), NOW(), 'system', 'system'),
    ('Pase VIP Experiencia', 'Acceso preferencial sin filas + souvenir de La Ranita', 3500.00, true, 'entrada', NOW(), NOW(), 'system', 'system'),
    ('Promoción Vecinos', 'Tarifa para residentes locales (solo días de semana)', 900.00, true, 'entrada', NOW(), NOW(), 'system', 'system'),
    ('Pase Nocturno Especial', 'Entrada para eventos especiales de noche (Desactivada)', 2500.00, false, 'entrada', NOW(), NOW(), 'system', 'system'),
    ('Almuerzo Completo', 'Incluye entrada, plato principal y postre', 1200.00, true, 'extra', NOW(), NOW(), 'system', 'system'),
    ('Estacionamiento', 'Acceso al estacionamiento por un día', 500.00, true, 'extra', NOW(), NOW(), 'system', 'system');

-- =============================================================================
-- 4. CUPONES (Descuentos porcentuales o fijos)
-- =============================================================================
INSERT INTO cupones (codigo, porcentaje_descuento, monto_descuento, fecha_expiracion, usos_maximos, usos_actuales, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion)
VALUES
    ('RANITA20', 20.00, NULL, '2026-12-31', 200, 3, true, NOW(), NOW(), 'system', 'system'),
    ('BIENVENIDA500', NULL, 500.00, '2026-09-30', 100, 2, true, NOW(), NOW(), 'system', 'system'),
    ('SUPERPROMO50', 50.00, NULL, '2026-07-15', 20, 1, true, NOW(), NOW(), 'system', 'system'),
    ('CUPON_EXPIRADO', 15.00, NULL, '2025-12-31', 50, 50, false, NOW(), NOW(), 'system', 'system'),
    ('CORDOBA2026', NULL, 300.00, '2026-08-31', 500, 0, true, NOW(), NOW(), 'system', 'system');

-- =============================================================================
-- 5. DÍAS ABIERTOS
-- =============================================================================
INSERT INTO dias_apertura (fecha, abierto, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion)
VALUES
    ('2026-06-20', true, NOW(), NOW(), 'system', 'system'),
    ('2026-06-21', true, NOW(), NOW(), 'system', 'system'),
    ('2026-06-22', true, NOW(), NOW(), 'system', 'system'),
    ('2026-06-23', false, NOW(), NOW(), 'system', 'system'), -- Cerrado por limpieza profunda
    ('2026-06-24', true, NOW(), NOW(), 'system', 'system'),
    ('2026-06-25', true, NOW(), NOW(), 'system', 'system'),
    ('2026-06-26', true, NOW(), NOW(), 'system', 'system'),
    ('2026-06-27', true, NOW(), NOW(), 'system', 'system'),
    ('2026-06-28', true, NOW(), NOW(), 'system', 'system'),
    ('2026-07-04', true, NOW(), NOW(), 'system', 'system'),
    ('2026-07-05', true, NOW(), NOW(), 'system', 'system'),
    ('2026-07-09', true, NOW(), NOW(), 'system', 'system'), -- Feriado de Julio abierto
    ('2026-07-10', false, NOW(), NOW(), 'system', 'system'), -- Feriado puente cerrado
    ('2026-07-11', true, NOW(), NOW(), 'system', 'system'),
    ('2026-07-12', true, NOW(), NOW(), 'system', 'system'),
    ('2026-07-18', true, NOW(), NOW(), 'system', 'system'),
    ('2026-07-19', true, NOW(), NOW(), 'system', 'system'),
    ('2026-07-25', true, NOW(), NOW(), 'system', 'system'),
    ('2026-07-26', true, NOW(), NOW(), 'system', 'system'),
    ('2026-08-01', true, NOW(), NOW(), 'system', 'system'),
    ('2026-08-02', true, NOW(), NOW(), 'system', 'system'),
    ('2026-08-08', true, NOW(), NOW(), 'system', 'system'),
    ('2026-08-09', true, NOW(), NOW(), 'system', 'system'),
    ('2026-08-15', true, NOW(), NOW(), 'system', 'system'),
    ('2026-08-16', true, NOW(), NOW(), 'system', 'system'),
    ('2026-08-22', true, NOW(), NOW(), 'system', 'system'),
    ('2026-08-23', true, NOW(), NOW(), 'system', 'system'),
    ('2026-08-29', true, NOW(), NOW(), 'system', 'system'),
    ('2026-08-30', true, NOW(), NOW(), 'system', 'system'),
    ('2026-09-05', true, NOW(), NOW(), 'system', 'system'),
    ('2026-09-06', true, NOW(), NOW(), 'system', 'system'),
    ('2026-09-12', true, NOW(), NOW(), 'system', 'system'),
    ('2026-09-13', true, NOW(), NOW(), 'system', 'system'),
    ('2026-09-19', true, NOW(), NOW(), 'system', 'system'),
    ('2026-09-20', true, NOW(), NOW(), 'system', 'system'),
    ('2026-09-26', true, NOW(), NOW(), 'system', 'system'),
    ('2026-09-27', true, NOW(), NOW(), 'system', 'system'),
    ('2026-10-03', true, NOW(), NOW(), 'system', 'system'),
    ('2026-10-04', true, NOW(), NOW(), 'system', 'system'),
    ('2026-10-10', true, NOW(), NOW(), 'system', 'system'),
    ('2026-10-11', true, NOW(), NOW(), 'system', 'system'),
    ('2026-10-17', true, NOW(), NOW(), 'system', 'system'),
    ('2026-10-18', true, NOW(), NOW(), 'system', 'system'),
    ('2026-10-24', true, NOW(), NOW(), 'system', 'system'),
    ('2026-10-25', true, NOW(), NOW(), 'system', 'system'),
    ('2026-10-31', true, NOW(), NOW(), 'system', 'system'),
    ('2026-11-01', true, NOW(), NOW(), 'system', 'system'),
    ('2026-11-07', true, NOW(), NOW(), 'system', 'system'),
    ('2026-11-08', true, NOW(), NOW(), 'system', 'system'),
    ('2026-11-14', true, NOW(), NOW(), 'system', 'system'),
    ('2026-11-15', true, NOW(), NOW(), 'system', 'system'),
    ('2026-11-21', true, NOW(), NOW(), 'system', 'system'),
    ('2026-11-22', true, NOW(), NOW(), 'system', 'system'),
    ('2026-11-28', true, NOW(), NOW(), 'system', 'system'),
    ('2026-11-29', true, NOW(), NOW(), 'system', 'system');

-- =============================================================================
-- 6. COMPRAS (Historial variado)
-- IDs autoincrementales asumidos secuenciales (1 al 9)
-- =============================================================================

-- Compra 1: Carlos Gómez - Estado: USADO (Ya vino al parque el 20/06, validó boletero.turnom, usó RANITA20)
-- Subtotal original: 2 General Adulto (3000) -> 20% desc = 600. Total = 2400
INSERT INTO compras (id_cliente, contact_email, contact_phone, fecha_visita, monto_total, descuento_aplicado, estado, id_usuario_validador, id_cupon, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion)
VALUES (1, 'carlos@gmail.com', '3515555555', '2026-06-20', 2400.00, 600.00, 'USADO', 3, 1, NOW(), NOW(), 'admin.serrana', 'boletero.turnom');

-- Compra 2: Ana Rodríguez - Estado: PAGADO (Viene mañana 21/06, no usó cupón, pagó online)
-- Subtotal original: 1 Pase Familiar (4000) + 1 Jubilado (1000) = 5000. Total = 5000
INSERT INTO compras (id_cliente, contact_email, contact_phone, fecha_visita, monto_total, descuento_aplicado, estado, id_usuario_validador, id_cupon, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion)
VALUES (2, 'ana.rod@hotmail.com', '1144448888', '2026-06-21', 5000.00, 0.00, 'PAGADO', NULL, NULL, NOW(), NOW(), 'system', 'system');

-- Compra 3: Mariana López - Estado: PENDIENTE (Reserva web iniciada para el 24/06, sin pagar)
-- Subtotal original: 1 General Adulto (1500). Total = 1500
INSERT INTO compras (id_cliente, contact_email, contact_phone, fecha_visita, monto_total, descuento_aplicado, estado, id_usuario_validador, id_cupon, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion)
VALUES (3, 'mariana.lopez@outlook.com', NULL, '2026-06-24', 1500.00, 0.00, 'PENDIENTE', NULL, NULL, NOW(), NOW(), 'system', 'system');

-- Compra 4: Diego Maradona - Estado: CANCELADO (Canceló la visita del 20/06)
-- Subtotal original: 2 Pase VIP (7000). Total = 7000
INSERT INTO compras (id_cliente, contact_email, contact_phone, fecha_visita, monto_total, descuento_aplicado, estado, id_usuario_validador, id_cupon, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion)
VALUES (4, 'dieguito@diego.com', '341999999', '2026-06-20', 7000.00, 0.00, 'CANCELADO', NULL, NULL, NOW(), NOW(), 'system', 'admin.central');

-- Compra 5: Florencia Fernández - Estado: USADO (Vino el 20/06 por la tarde, validó boletero.turnot, usó BIENVENIDA500)
-- Subtotal original: 1 Pase Familiar (4000) -> Fijo desc = 500. Total = 3500
INSERT INTO compras (id_cliente, contact_email, contact_phone, fecha_visita, monto_total, descuento_aplicado, estado, id_usuario_validador, id_cupon, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion)
VALUES (5, 'flor.f@gmail.com', '261456789', '2026-06-20', 3500.00, 500.00, 'USADO', 4, 2, NOW(), NOW(), 'system', 'boletero.turnot');

-- Compra 6: Javier Milei - Estado: PAGADO (Viene el 22/06, usó SUPERPROMO50)
-- Subtotal original: 2 Pase VIP (7000) -> 50% desc = 3500. Total = 3500
INSERT INTO compras (id_cliente, contact_email, contact_phone, fecha_visita, monto_total, descuento_aplicado, estado, id_usuario_validador, id_cupon, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion)
VALUES (6, 'jmilei@presidencia.gob.ar', '1111111111', '2026-06-22', 3500.00, 3500.00, 'PAGADO', NULL, 3, NOW(), NOW(), 'system', 'system');

-- Compra 7: Sofía Martínez - Estado: PENDIENTE (Para las vacaciones de Julio 09/07, usó BIENVENIDA500)
-- Subtotal original: 2 General Adulto (3000) + 2 General Menor (1600) = 4600 -> Fijo desc = 500. Total = 4100
INSERT INTO compras (id_cliente, contact_email, contact_phone, fecha_visita, monto_total, descuento_aplicado, estado, id_usuario_validador, id_cupon, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion)
VALUES (7, 'sofia.mtz@fox.com', '1154321098', '2026-07-09', 4100.00, 500.00, 'PENDIENTE', NULL, 2, NOW(), NOW(), 'system', 'system');

-- Compra 8: Bautista González - Estado: PAGADO (Viene el 21/06, sacó tarifa Vecinos)
-- Subtotal original: 3 Promoción Vecinos (2700). Total = 2700
INSERT INTO compras (id_cliente, contact_email, contact_phone, fecha_visita, monto_total, descuento_aplicado, estado, id_usuario_validador, id_cupon, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion)
VALUES (8, 'bauti.gonzalez@gmail.com', NULL, '2026-06-21', 2700.00, 0.00, 'PAGADO', NULL, NULL, NOW(), NOW(), 'system', 'system');

-- Compra 9: Valentina Romero - Estado: USADO (Fue hoy por la mañana 20/06, validada en boletería física, usó RANITA20)
-- Subtotal original: 1 General Adulto (1500) + 1 General Menor (800) = 2300 -> 20% desc = 460. Total = 1840
INSERT INTO compras (id_cliente, contact_email, contact_phone, fecha_visita, monto_total, descuento_aplicado, estado, id_usuario_validador, id_cupon, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion)
VALUES (9, 'valen_romero@live.com.ar', '3512223334', '2026-06-20', 1840.00, 460.00, 'USADO', 3, 1, NOW(), NOW(), 'boletero.turnom', 'boletero.turnom');


-- =============================================================================
-- 7. DETALLES DE COMPRA (Vinculados mediante id_compra)
-- =============================================================================

-- Detalles Compra 1 (Carlos)
INSERT INTO compras_detalle (id_compra, id_tipo_entrada, cantidad, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion)
VALUES (1, 1, 2, NOW(), NOW(), 'system', 'system'); -- 2 General Adulto

-- Detalles Compra 2 (Ana)
INSERT INTO compras_detalle (id_compra, id_tipo_entrada, cantidad, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion)
VALUES
    (2, 4, 1, NOW(), NOW(), 'system', 'system'),  -- 1 Pase Familiar
    (2, 3, 1, NOW(), NOW(), 'system', 'system');  -- 1 Jubilado

-- Detalles Compra 3 (Mariana)
INSERT INTO compras_detalle (id_compra, id_tipo_entrada, cantidad, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion)
VALUES (3, 1, 1, NOW(), NOW(), 'system', 'system'); -- 1 General Adulto

-- Detalles Compra 4 (Diego)
INSERT INTO compras_detalle (id_compra, id_tipo_entrada, cantidad, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion)
VALUES (4, 5, 2, NOW(), NOW(), 'system', 'system'); -- 2 Pase VIP

-- Detalles Compra 5 (Florencia)
INSERT INTO compras_detalle (id_compra, id_tipo_entrada, cantidad, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion)
VALUES (5, 4, 1, NOW(), NOW(), 'system', 'system'); -- 1 Pase Familiar

-- Detalles Compra 6 (Javier)
INSERT INTO compras_detalle (id_compra, id_tipo_entrada, cantidad, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion)
VALUES (6, 5, 2, NOW(), NOW(), 'system', 'system'); -- 2 Pase VIP

-- Detalles Compra 7 (Sofía)
INSERT INTO compras_detalle (id_compra, id_tipo_entrada, cantidad, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion)
VALUES
    (7, 1, 2, NOW(), NOW(), 'system', 'system'),  -- 2 General Adulto
    (7, 2, 2, NOW(), NOW(), 'system', 'system');  -- 2 General Menor

-- Detalles Compra 8 (Bautista)
INSERT INTO compras_detalle (id_compra, id_tipo_entrada, cantidad, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion)
VALUES (8, 6, 3, NOW(), NOW(), 'system', 'system'); -- 3 Promoción Vecinos

-- Detalles Compra 9 (Valentina)
INSERT INTO compras_detalle (id_compra, id_tipo_entrada, cantidad, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion)
VALUES
    (9, 1, 1, NOW(), NOW(), 'system', 'system'),  -- 1 General Adulto
    (9, 2, 1, NOW(), NOW(), 'system', 'system');  -- 1 General Menor