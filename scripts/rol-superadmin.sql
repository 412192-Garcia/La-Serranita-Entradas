-- Rol SUPERADMIN (soporte de la app, el único que ve Sistema).
--
-- Correr UNA VEZ contra la base de producción ANTES de desplegar la versión con el rol nuevo.
-- Hibernate (ddl-auto=update) creó la columna usuarios.rol con un CHECK que lista sólo los roles
-- de ese momento ('ADMIN', 'BOLETERO') y nunca lo actualiza: sin esto, guardar un SUPERADMIN
-- (el bootstrap con BOOTSTRAP_SUPERADMIN_USERNAME) falla con "violates check constraint".
--
--   docker compose exec -T db psql -U serranita -d serranita < scripts/rol-superadmin.sql

BEGIN;

ALTER TABLE usuarios DROP CONSTRAINT IF EXISTS usuarios_rol_check;
ALTER TABLE usuarios ADD CONSTRAINT usuarios_rol_check CHECK (rol IN ('SUPERADMIN', 'ADMIN', 'BOLETERO'));

COMMIT;
