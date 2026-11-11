-- Vouchers manuales (Acciones de admin > Vouchers): no tienen compra, así que vouchers.id_compra pasa a
-- admitir null. ddl-auto=update no saca un NOT NULL existente: sin esto, emitir uno falla al guardar.
-- Correr una vez en cada base que ya exista (local Docker y producción).
ALTER TABLE vouchers ALTER COLUMN id_compra DROP NOT NULL;
