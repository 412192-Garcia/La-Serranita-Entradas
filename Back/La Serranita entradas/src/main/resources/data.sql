-- Insert datos de prueba en la tabla USUARIOS
INSERT INTO usuarios (username, password, nombre, apellido, rol, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion)
VALUES
('admin', '$2a$10$slYQmyNdGzin7olVN3p/HenKbPXXtDXZgf0P3Vw/.GI0.C.D2gvbe', 'Admin', 'Sistema', 'ADMIN', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM'),
('boletero1', '$2a$10$slYQmyNdGzin7olVN3p/HenKbPXXtDXZgf0P3Vw/.GI0.C.D2gvbe', 'Juan', 'Pérez', 'BOLETERO', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM'),
('boletero2', '$2a$10$slYQmyNdGzin7olVN3p/HenKbPXXtDXZgf0P3Vw/.GI0.C.D2gvbe', 'María', 'García', 'BOLETERO', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM');

-- Insert datos de prueba en la tabla TIPOS_ENTRADA
INSERT INTO tipos_entrada (nombre, descripcion, precio, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion)
VALUES
('Entrada General', 'Acceso general al parque', 50.00, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM'),
('Entrada Niño', 'Para niños menores de 12 años', 30.00, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM'),
('Entrada Jubilado', 'Descuento para jubilados', 35.00, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM'),
('Entrada Grupal (10+)', 'Entrada para grupos de 10 o más personas', 45.00, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM');

-- Insert datos de prueba en la tabla CUPONES
INSERT INTO cupones (codigo, porcentaje_descuento, monto_descuento, fecha_expiracion, uso_maximo, uso_actual, activo, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion)
VALUES
('PROMO10', 10.00, NULL, '2026-12-31', 100, 5, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM'),
('VERANO20', 20.00, NULL, '2026-09-30', 50, 3, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM'),
('DESC5', NULL, 5.00, '2026-08-31', 200, 45, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM'),
('OPORTUNIDAD', 15.00, NULL, '2026-06-30', 30, 0, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM');

-- Insert datos de prueba en la tabla DIAS_APERTURA
INSERT INTO dias_apertura (fecha, abierto, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion)
VALUES
('2026-06-01', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM'),
('2026-05-02', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM'),
('2026-06-03', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM'),
('2026-06-04', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM'),
('2026-06-05', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM');

-- Insert datos de prueba en la tabla COMPRAS
INSERT INTO compras (dni, nombre, email, telefono, fecha_visita, monto_total, descuento_aplicado, estado, id_usuario_validador, id_cupon, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion)
VALUES
('12345678', 'Carlos López', 'carlos@example.com', '555-0001', '2026-06-01', 150.00, 0.00, 'PENDIENTE', NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM'),
('87654321', 'Ana Martínez', 'ana@example.com', '555-0002', '2026-06-01', 100.00, 10.00, 'PENDIENTE', NULL, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM'),
('11111111', 'Roberto García', 'roberto@example.com', '555-0003', '2026-06-02', 120.00, 0.00, 'USADO', 1, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM'),
('22222222', 'Sofía Rodríguez', 'sofia@example.com', '555-0004', '2026-06-03', 200.00, 20.00, 'PENDIENTE', NULL, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM'),
('33333333', 'Miguel Fernández', 'miguel@example.com', '555-0005', '2026-06-03', 90.00, 0.00, 'USADO', 2, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM');

-- Insert datos de prueba en la tabla COMPRAS_DETALLE
INSERT INTO compras_detalle (id_compra, id_tipo_entrada, cantidad, fecha_creacion, fecha_modificacion, usuario_creacion, usuario_modificacion)
VALUES
(1, 1, 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM'),
(2, 2, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM'),
(2, 3, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM'),
(3, 1, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM'),
(4, 4, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM'),
(4, 2, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM'),
(5, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM'),
(5, 3, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SYSTEM', 'SYSTEM');

