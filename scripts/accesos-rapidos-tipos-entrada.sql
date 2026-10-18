-- Los accesos rápidos de cantidad del POS (1 a 10 y "10+") pasan a ser un campo de cada tipo
-- de entrada (tipos_entrada.accesos_rapidos, editable en Configuración > Tipos de entrada).
-- Antes se mostraban para los tipos con escalones de precio por grupo cargados (Descuentos en
-- efectivo); esto deja activado el campo justo en esos tipos, para que el POS siga igual.
-- Correr una vez en cada base que ya exista (local Docker y producción), después de levantar
-- el back nuevo (ddl-auto=update crea la columna).
UPDATE tipos_entrada t
SET accesos_rapidos = EXISTS (
    SELECT 1 FROM descuentos_efectivo d WHERE d.id_tipo_entrada = t.id
)
WHERE t.accesos_rapidos IS NULL;
