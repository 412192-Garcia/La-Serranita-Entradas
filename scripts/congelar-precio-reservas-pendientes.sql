-- Congela el precio de lista actual en las reservas "a cobrar en boletería" que siguen pendientes.
-- CORRER ANTES de subir el precio de un tipo de entrada: las reservas creadas con la versión
-- anterior a este cambio no guardaron con qué precio se reservó, y sin este paso se cobrarían
-- al precio nuevo si el cliente paga con tarjeta/QR o cambia la cantidad (en efectivo y sin
-- cambios siempre se cobra el monto de la reserva, con o sin este paso).
-- Las reservas nuevas ya lo guardan solas. Es idempotente: sólo toca líneas sin precio.
--
-- Uso:  psql -U $POSTGRES_USER -d $POSTGRES_DB -f congelar-precio-reservas-pendientes.sql

UPDATE compras_detalle d
SET precio_unitario = t.precio
FROM compras c, tipos_entrada t
WHERE d.id_compra = c.id
  AND d.id_tipo_entrada = t.id
  AND c.estado = 'RESERVADO_EFECTIVO'
  AND d.precio_unitario IS NULL;
