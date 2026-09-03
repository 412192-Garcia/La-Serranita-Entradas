import { Component, computed, inject, input, output, signal, viewChild } from '@angular/core';
import { CajaService, Caja } from '../../services/caja.service';
import { RetiroEfectivoModal } from '../../shared/retiro-efectivo-modal/retiro-efectivo-modal';
import { Modal } from '../../shared/modal/modal';
import { PesosPipe } from '../../shared/pesos.pipe';
import { ConteoCierre } from '../conteo-cierre/conteo-cierre';

/**
 * Vive en Cajas (admin), no en POS: cerrar caja dejó de ser self-service — un ADMIN es quien
 * la cierra, sin importar de qué boletero, por eso todo acá se resuelve por id explícito
 * (cajaId) en vez de "mi propia caja abierta". Corregir un cierre ya hecho NO pasa por acá:
 * eso lo hace "Corregir caja" en el resumen de cierre, con el mismo formulario (ConteoCierre).
 */
@Component({
  selector: 'app-cierre-caja-modal',
  imports: [RetiroEfectivoModal, Modal, PesosPipe, ConteoCierre],
  templateUrl: './cierre-caja-modal.html',
  styleUrl: './cierre-caja-modal.css',
})
export class CierreCajaModal {
  private cajaService = inject(CajaService);

  /** Id de la caja abierta que se está por cerrar. */
  cajaId = input<number | null>(null);
  /** Detalle de esa misma caja abierta (vía obtenerDetalle): para saber si hubo ventas en dólares y qué movimientos ya tiene cargados. */
  cajaAbierta = input<Caja | null>(null);

  cajaCerrada = output<Caja>();
  cerrar = output<void>();
  /** Se emite cuando se agrega un retiro/aporte sin llegar a cerrar: el padre tiene que refrescar cajaAbierta para que el listado de movimientos y el total se vean al día. */
  cajaActualizada = output<Caja>();

  private conteo = viewChild.required(ConteoCierre);

  huboVentaDolares = computed(() => this.cajaAbierta()?.huboVentaDolares ?? false);
  retirosMostrados = computed(() => this.cajaAbierta()?.retiros ?? []);
  mostrarMovimiento = signal(false);

  cerrandoCaja = signal(false);
  errorCierre = signal<string | null>(null);

  /** El monto/conteo/posnet/entradas que ya estaban cargados no se tocan: sólo se refresca la lista de movimientos y el total de retiros vía cajaActualizada. */
  onMovimientoRegistrado(caja: Caja): void {
    this.mostrarMovimiento.set(false);
    this.cajaActualizada.emit(caja);
  }

  confirmarCierre(): void {
    const error = this.conteo().validar();
    if (error) {
      this.errorCierre.set(error);
      return;
    }
    const valor = this.conteo().valor();

    this.cerrandoCaja.set(true);
    this.errorCierre.set(null);

    this.cajaService
      .cerrarComoAdmin(
        this.cajaId()!,
        valor.conteoEfectivo,
        valor.cierresPosnet,
        valor.entradasFisicasRestantes!,
        valor.cambioContado,
        valor.dolaresContado
      )
      .subscribe({
        next: (c) => {
          this.cerrandoCaja.set(false);
          // Ya se guardó: si más adelante se cierra OTRA caja con el mismo modal
          // (nunca se destruye entre usos), no tiene que arrancar con estos datos puestos.
          this.conteo().reset();
          this.cajaCerrada.emit(c);
        },
        error: (err) => {
          this.errorCierre.set(typeof err?.error === 'string' ? err.error : 'No se pudo cerrar la caja. Reintentá.');
          this.cerrandoCaja.set(false);
        },
      });
  }
}
