-- Quien compra un regalo ya no carga DNI (entra el receptor, con el suyo), así que
-- clientes.dni pasa a admitir null. ddl-auto=update no saca un NOT NULL existente:
-- sin esto, toda compra-regalo falla al guardar el cliente.
-- Correr una vez en cada base que ya exista (local Docker y producción).
ALTER TABLE clientes ALTER COLUMN dni DROP NOT NULL;
