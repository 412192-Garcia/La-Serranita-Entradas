-- Los trabajos de impresión ahora pueden ser un voucher en vez de una factura, así que
-- trabajos_impresion.id_factura pasa a admitir null. ddl-auto=update no saca un NOT NULL existente:
-- sin esto, imprimir un voucher falla al guardar el trabajo.
-- Correr una vez en cada base que ya exista (local Docker y producción).
ALTER TABLE trabajos_impresion ALTER COLUMN id_factura DROP NOT NULL;
