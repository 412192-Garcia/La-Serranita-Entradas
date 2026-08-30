import { DescuentoEfectivo } from '../services/configuracion.service';
import { CotizacionResponse, DescuentoPos, LineaArticuloPos, LineaVentaPos } from '../services/boleteria.service';
import { TipoEntrada } from '../models/tipo-entrada';
import { LineaEntradaFija } from '../models/venta-pos';
import { Promocion } from '../models/promocion';
import { FormaPagoPos } from '../models/compra';

/**
 * Cotización del carrito calculada en el navegador, para poder seguir vendiendo sin conexión.
 *
 * Es un port 1:1 de lo que hace el backend (CalculoPrecioServiceImpl.calcularTotal +
 * CompraServiceImpl.calcularDescuentoPos): si alguna de esas reglas cambia, hay que cambiarla
 * acá también. Se acepta esa duplicación a propósito — la alternativa era que un corte de
 * señal deje al boletero sin poder cobrar, que es peor. El precio definitivo lo sigue
 * calculando el backend cuando la venta se sincroniza; esto es lo que se le muestra al
 * cliente en el momento, y tiene que dar igual.
 */
export function cotizarLocalmente(
  formaPago: FormaPagoPos,
  entradas: LineaVentaPos[],
  articulos: LineaArticuloPos[],
  descuento: DescuentoPos,
  tiposEntrada: TipoEntrada[],
  descuentosEfectivo: DescuentoEfectivo[],
  promociones: Promocion[],
  entradasFijas: LineaEntradaFija[] = []
): CotizacionResponse {
  let totalLista = 0;
  let totalConPromo = 0;

  for (const linea of entradas) {
    const tipo = tiposEntrada.find((t) => t.id === linea.tipoEntradaId);
    if (!tipo) continue;
    totalLista += tipo.precio * linea.cantidad;
    totalConPromo += totalPorTipo(tipo, linea.cantidad, formaPago, descuentosEfectivo);
  }

  // Las líneas fijas de una reserva (extras, tipos fuera del catálogo) van a precio de lista:
  // no tienen escalón de grupo y el boletero no puede cambiarlas.
  for (const fija of entradasFijas) {
    const monto = fija.precioUnitario * fija.cantidad;
    totalLista += monto;
    totalConPromo += monto;
  }

  // Los artículos varios no tienen precio de grupo: van siempre a precio unitario × cantidad.
  const totalArticulos = articulos.reduce((acc, a) => acc + a.precioUnitario * a.cantidad, 0);
  totalLista += totalArticulos;
  totalConPromo += totalArticulos;

  const montoDescuento = calcularDescuento(totalConPromo, descuento, promociones);

  return {
    subtotal: redondear(totalConPromo - montoDescuento),
    ahorro: redondear(totalLista - totalConPromo),
  };
}

/**
 * Precio de una línea de entradas. El precio promocional por grupo sólo existe pagando en
 * efectivo; los grupos más grandes que el escalón máximo configurado pagan el precio por
 * persona de ese último escalón (no vuelven a precio de lista completo).
 */
function totalPorTipo(
  tipo: TipoEntrada,
  cantidad: number,
  formaPago: FormaPagoPos,
  descuentosEfectivo: DescuentoEfectivo[]
): number {
  const precioLista = tipo.precio * cantidad;
  if (formaPago !== 'EFECTIVO_BOLETERIA') return precioLista;

  const escalonesDelTipo = descuentosEfectivo.filter((d) => d.tipoEntradaId === tipo.id);
  if (escalonesDelTipo.length === 0) return precioLista;

  const exacto = escalonesDelTipo.find((d) => d.cantidadPases === cantidad);
  if (exacto) return exacto.precioPromocionalTotal;

  const masAlto = escalonesDelTipo.reduce((a, b) => (b.cantidadPases > a.cantidadPases ? b : a));
  if (cantidad > masAlto.cantidadPases) {
    const precioPorPersona = masAlto.precioPromocionalTotal / masAlto.cantidadPases;
    return redondear(precioPorPersona * cantidad);
  }

  return precioLista;
}

/** Promo con nombre o descuento manual (%/$), excluyentes entre sí, topeado al monto bruto. */
function calcularDescuento(montoBruto: number, descuento: DescuentoPos, promociones: Promocion[]): number {
  let valor = 0;

  if (descuento.promocionId != null) {
    const promo = promociones.find((p) => p.id === descuento.promocionId);
    if (!promo || !promo.activo) return 0;
    valor = promo.porcentajeDescuento != null
      ? redondear((montoBruto * promo.porcentajeDescuento) / 100)
      : (promo.montoDescuento ?? 0);
  } else if (descuento.descuentoManualPorcentaje != null) {
    valor = redondear((montoBruto * descuento.descuentoManualPorcentaje) / 100);
  } else if (descuento.descuentoManualMonto != null) {
    valor = descuento.descuentoManualMonto;
  }

  if (valor < 0) return 0;
  if (valor > montoBruto) return montoBruto;
  return valor;
}

/** Dos decimales, igual que el HALF_UP del backend: evita totales con colas de coma flotante. */
function redondear(valor: number): number {
  return Math.round(valor * 100) / 100;
}
