-- Completa el motivo de las compras que se cancelaron antes de que se guardara (columnas
-- motivo_cancelacion y compañía, las crea ddl-auto al arrancar el backend nuevo: correr esto
-- DESPUÉS de ese primer arranque). Se deduce igual que en consultar-motivos-cancelacion.sql.
-- No pisa nada que ya tenga motivo; se puede correr más de una vez.

-- Anuladas desde Cajas: quién y cuándo, de la auditoría si quedó (existe desde el 2026-10-05).
UPDATE compras c
SET motivo_cancelacion = 'ANULADA_EN_CAJA',
    cancelada_por = a.usuario,
    fecha_cancelacion = a.fecha
FROM (SELECT DISTINCT ON (entidad_id) entidad_id, usuario, fecha
      FROM auditoria
      WHERE accion = 'VENTA_CANCELADA' AND resultado < 400
      ORDER BY entidad_id, fecha DESC) a
WHERE c.estado = 'CANCELADO' AND c.motivo_cancelacion IS NULL AND c.id_caja IS NOT NULL
  AND a.entidad_id = '#' || c.codigo_reserva;

UPDATE compras
SET motivo_cancelacion = 'ANULADA_EN_CAJA'
WHERE estado = 'CANCELADO' AND motivo_cancelacion IS NULL AND id_caja IS NOT NULL;

-- Online sin caja: checkout de Mercado Pago que no se pagó. De estas no se guardó qué contestó
-- Mercado Pago, así que no se sabe si intentó pagar y se lo rechazaron o si ni lo intentó.
UPDATE compras
SET motivo_cancelacion = 'CHECKOUT_ABANDONADO',
    detalle_cancelacion = 'Cancelada antes de que se anotara el detalle: no se sabe si intentó pagar.',
    cancelada_por = 'SISTEMA'
WHERE estado = 'CANCELADO' AND motivo_cancelacion IS NULL AND id_caja IS NULL
  AND forma_pago = 'MERCADO_PAGO';
