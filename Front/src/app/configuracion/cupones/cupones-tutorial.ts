import { TourStep } from '../../shared/tour/tour';

/**
 * Textos del tutorial de los formularios de cupones. El cupón individual y el lote comparten casi
 * todos los campos, así que los pasos comunes se escriben una vez y cada formulario los apunta con
 * su propio prefijo de `data-tour` ("c-" el cupón individual, "l-" el lote).
 */
type Formulario = 'c' | 'l';

const paso = (f: Formulario, campo: string, titulo: string, texto: string): TourStep => ({
  selector: `[data-tour="${f}-${campo}"]`,
  titulo,
  texto,
});

function pasosComunes(f: Formulario, incluirTipos: boolean): TourStep[] {
  const pasos: TourStep[] = [
    paso(
      f,
      'tipo',
      'Tipo de descuento',
      'Porcentaje: descuenta un % del total (20% sobre $100.000 = $20.000). ' +
        'Restar un monto a la compra: resta esos pesos una sola vez a toda la compra. ' +
        'Restar un monto a cada entrada: resta esos pesos a cada entrada ($5.000 en 4 entradas = $20.000). ' +
        'Precio fijo por entrada: cada entrada pasa a costar ese valor; con $20.000, una entrada de $34.300 baja $14.300 y una de $10.000 no cambia (nunca se encarece).',
    ),
    paso(
      f,
      'valor',
      'Valor',
      'El porcentaje o los pesos, según el tipo de descuento elegido. Con "Precio fijo por entrada" es el precio final que va a pagar cada entrada.',
    ),
    paso(
      f,
      'usos',
      f === 'l' ? 'Usos máximos por cupón' : 'Usos máximos',
      (f === 'l'
        ? 'Cuántas compras puede hacer cada código del lote. Con 1, cada código sirve una sola vez. '
        : 'Cuántas compras pueden usar este cupón en total. ') +
        'Opcional: si lo dejás vacío no tiene límite. Un cupón se apaga solo cuando llega al máximo.',
    ),
    paso(
      f,
      'expiracion',
      'Fecha de expiración',
      'Último día de compra en que el cupón sirve. Opcional: vacío significa que no vence. Es la fecha en que se compra, no la de la visita.',
    ),
    paso(
      f,
      'desde',
      'Válido desde',
      'Primer día de compra en que el cupón sirve. Opcional: vacío significa que ya se puede usar. Junto con la expiración arma una ventana de fechas de compra (ej. una promo de fin de semana largo).',
    ),
    paso(
      f,
      'min',
      'Mínimo de entradas',
      'Cantidad mínima de entradas, de las que el cupón alcanza, para poder usarlo: sirve para descuentos de grupo. Si la compra no llega, se rechaza con un aviso. Opcional.',
    ),
    paso(
      f,
      'max',
      'Máximo de entradas con descuento',
      'A cuántas entradas se les aplica el descuento; el resto paga normal, y se descuentan primero las más caras. Ej.: máximo 2 con "restar $5.000 a cada entrada" y 4 entradas en la compra = $10.000 de descuento. Opcional.',
    ),
    paso(
      f,
      'tope',
      'Tope de descuento',
      'Lo máximo que puede descontar el cupón en una compra, en pesos. Sirve sobre todo con porcentajes: un 50% con tope de $10.000 nunca descuenta más de $10.000. Opcional.',
    ),
  ];
  if (incluirTipos) {
    pasos.push(
      paso(
        f,
        'tipos',
        'Tipos de entrada',
        'A qué tipos de entrada alcanza el cupón. Vienen todos tildados (= todas las entradas). Destildá las que no deben entrar: el mínimo, el máximo, el tope y el descuento cuentan solamente las tildadas.',
      ),
    );
  }
  return pasos;
}

export function pasosCuponIndividual(incluirTipos: boolean): TourStep[] {
  return [
    paso(
      'c',
      'codigo',
      'Código',
      'Lo que escribe el cliente para usar el descuento (ej. VERANO25). Opcional: si lo dejás vacío se genera uno al azar. No puede repetirse.',
    ),
    ...pasosComunes('c', incluirTipos),
  ];
}

export function pasosLote(incluirTipos: boolean): TourStep[] {
  return [
    paso('l', 'nombre', 'Nombre del lote', 'Cómo vas a reconocer este lote en el listado y en Reportes (ej. "Promociones Invierno"). Los reportes agrupan por este nombre.'),
    paso('l', 'prefijo', 'Prefijo', 'Letras que van al principio de cada código generado. Con "INV", los códigos quedan como INV-AB12CD.'),
    paso('l', 'descripcion', 'Descripción', 'Una nota para vos (para qué es el lote, a quién se entregó). No la ve el cliente. Opcional.'),
    paso('l', 'cantidad', 'Cantidad a generar', 'Cuántos códigos distintos se crean, todos con las mismas reglas. Por ejemplo, 100 códigos de un solo uso para repartir.'),
    ...pasosComunes('l', incluirTipos),
  ];
}
