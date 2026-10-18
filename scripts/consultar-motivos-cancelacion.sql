-- Sólo lectura. Por qué se cancelaron las compras que ya estaban canceladas antes de que se
-- guardara el motivo: se deduce de cómo quedó la compra.
--  - Tiene caja: la anuló alguien desde Cajas (la auditoría dice quién, desde el 2026-10-05).
--  - Mercado Pago sin caja: el checkout quedó sin pagar y el barrido la canceló a las 3 h
--    (o falló al iniciar el pago, que se ve igual).

-- Resumen por origen
SELECT CASE WHEN c.id_caja IS NOT NULL THEN 'Anulada en caja'
            WHEN c.forma_pago = 'MERCADO_PAGO' THEN 'Checkout de Mercado Pago sin pagar'
            ELSE 'Otro (' || c.forma_pago || ')' END AS origen,
       count(*)                         AS cantidad,
       sum(c.monto_total)               AS monto,
       min(c.fecha_creacion)::date      AS desde,
       max(c.fecha_creacion)::date      AS hasta
FROM compras c
WHERE c.estado = 'CANCELADO'
GROUP BY 1
ORDER BY 2 DESC;

-- Detalle de las anuladas en caja, con quién la anuló si quedó en la auditoría
SELECT c.codigo_reserva, c.fecha_creacion, c.forma_pago, c.monto_total, c.id_caja,
       a.usuario AS anulo, a.fecha AS cuando
FROM compras c
LEFT JOIN auditoria a ON a.accion = 'VENTA_CANCELADA' AND a.entidad_id = '#' || c.codigo_reserva AND a.resultado < 400
WHERE c.estado = 'CANCELADO' AND c.id_caja IS NOT NULL
ORDER BY c.fecha_creacion DESC;
