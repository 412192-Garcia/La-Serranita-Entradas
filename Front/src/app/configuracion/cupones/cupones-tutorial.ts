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
      'Porcentaje: un % del total. Restar a la compra: un monto una sola vez. ' +
        'Restar a cada entrada: un monto por entrada. ' +
        'Precio fijo: cada entrada pasa a costar ese valor (si ya era más barata, no cambia).',
    ),
    paso(
      f,
      'valor',
      'Valor',
      'El porcentaje o el monto. Con "Precio fijo por entrada", el precio final de cada entrada.',
    ),
    paso(
      f,
      'usos',
      f === 'l' ? 'Usos máximos por cupón' : 'Usos máximos',
      (f === 'l'
        ? 'Cuántas veces se puede usar cada código. '
        : 'Cuántas veces se puede usar el cupón. ') + 'Vacío, sin límite.',
    ),
    paso(
      f,
      'expiracion',
      'Fecha de expiración',
      'Último día en que se puede comprar con el cupón. Vacío, no vence.',
    ),
    paso(
      f,
      'desde',
      'Válido desde',
      'Primer día en que se puede comprar con el cupón. Vacío, se puede usar desde ya.',
    ),
    paso(
      f,
      'min',
      'Mínimo de entradas',
      'Cuántas entradas tiene que tener la compra para usar el cupón. Opcional.',
    ),
    paso(
      f,
      'max',
      'Máximo de entradas con descuento',
      'A cuántas entradas se les aplica el descuento, empezando por las más caras. Opcional.',
    ),
    paso(
      f,
      'tope',
      'Tope de descuento',
      'Lo máximo que puede descontar el cupón en una compra. Opcional.',
    ),
  ];
  if (incluirTipos) {
    pasos.push(
      paso(
        f,
        'tipos',
        'Tipos de entrada',
        'A qué entradas se aplica el cupón. Destildá las que no.',
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
      'Lo que escribe el cliente para usar el descuento (ej. VERANO25). Si lo dejás vacío se genera uno.',
    ),
    ...pasosComunes('c', incluirTipos),
  ];
}

export function pasosLote(incluirTipos: boolean): TourStep[] {
  return [
    paso('l', 'nombre', 'Nombre del lote', 'Para reconocer el lote en el listado y en Reportes.'),
    paso('l', 'prefijo', 'Prefijo', 'Letras que van al principio de cada código generado. Con "INV", los códigos quedan como INV-AB12CD.'),
    paso('l', 'descripcion', 'Descripción', 'Una nota para vos. El cliente no la ve.'),
    paso('l', 'cantidad', 'Cantidad a generar', 'Cuántos códigos se crean, todos con las mismas reglas.'),
    ...pasosComunes('l', incluirTipos),
  ];
}
