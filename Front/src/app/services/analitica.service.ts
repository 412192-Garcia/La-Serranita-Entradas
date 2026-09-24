import { Injectable } from '@angular/core';
import { environment } from '../../environments/environment';

export interface CompraConfirmada {
  codigoReserva: string;
  montoTotal: number;
}

/**
 * Al confirmarse un pago, la compra termina en la página de gracias del sitio del parque,
 * donde el Google Tag Manager del sitio registra la conversión leyendo los parámetros de la
 * URL (el mismo esquema que usaba Turitop, así esa configuración sigue sirviendo).
 *
 * Embebido en el iframe, el navegador no deja que un iframe de otro dominio navegue la página
 * padre sin un clic del usuario (y la confirmación llega por polling, sin clic): por eso se le
 * avisa al sitio por postMessage y es el sitio el que redirige (snippet en el README). Abierto
 * directo, sin iframe, redirige la propia app. Nunca viajan nombre ni email: todo lo que va
 * en esa URL termina en Analytics, Facebook y los logs del servidor.
 */
@Injectable({ providedIn: 'root' })
export class AnaliticaService {
  /** El sitio escucha este "type" (ver README, "Página de gracias y conversiones"). */
  static readonly MENSAJE_COMPRA = 'la-serranita-compra';
  private static readonly NOMBRE_PRODUCTO = 'La Serranita - Parque recreativo Entradas Online';
  private static readonly CLAVE_REGISTRADAS = 'la-serranita-conversiones';
  private static readonly MAX_RECORDADAS = 50;

  /** Respaldo en memoria para cuando localStorage no está disponible (modo privado, bloqueado). */
  private readonly registradas = new Set<string>();

  registrarCompra(compra: CompraConfirmada): void {
    if (this.yaRegistrada(compra.codigoReserva)) return;
    this.marcarRegistrada(compra.codigoReserva);

    const parametros = {
      booking_id: compra.codigoReserva,
      total: String(compra.montoTotal),
      currency: 'ARS',
      product_name: AnaliticaService.NOMBRE_PRODUCTO,
    };

    if (window.parent !== window) {
      // "*" como el aviso de altura: el origen del sitio varía (real, túnel de prueba) y no
      // viaja nada sensible. Quien tiene que validar el origen es el sitio al recibirlo.
      window.parent.postMessage({ type: AnaliticaService.MENSAJE_COMPRA, compra: parametros }, '*');
      return;
    }

    if (environment.urlGracias) {
      window.location.href = `${environment.urlGracias}?${new URLSearchParams(parametros)}`;
    }
  }

  // La pantalla de éxito puede volver a cargarse (recarga, botón "atrás" desde la página de
  // gracias): lo registrado tiene que sobrevivir a eso, no sólo a esta instancia de la app.
  private yaRegistrada(codigo: string): boolean {
    return this.registradas.has(codigo) || this.leerGuardadas().includes(codigo);
  }

  private marcarRegistrada(codigo: string): void {
    this.registradas.add(codigo);
    try {
      const guardadas = [...this.leerGuardadas(), codigo].slice(-AnaliticaService.MAX_RECORDADAS);
      localStorage.setItem(AnaliticaService.CLAVE_REGISTRADAS, JSON.stringify(guardadas));
    } catch {
      // Sin localStorage queda el Set en memoria: cubre esta carga, no una recarga.
    }
  }

  private leerGuardadas(): string[] {
    try {
      const valor = JSON.parse(localStorage.getItem(AnaliticaService.CLAVE_REGISTRADAS) ?? '[]');
      return Array.isArray(valor) ? valor : [];
    } catch {
      return [];
    }
  }
}
