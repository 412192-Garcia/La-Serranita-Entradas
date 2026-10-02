import { Component, ElementRef, HostListener, computed, inject, output, signal } from '@angular/core';
import { LucideArrowLeftRight, LucideChevronDown, LucideReceiptText, LucideTicket, LucideWallet } from '@lucide/angular';
import { FacturaService } from '../../services/factura.service';
import { ConectividadService } from '../../services/conectividad.service';

/**
 * Botón "Caja" de la barra de la pantalla de venta: agrupa Retiro/Aporte y Reponer talonario, que
 * antes eran dos botones sueltos y ya no entraban cómodos junto a Anticipadas y Ticketera.
 *
 * Sigue visible sin conexión a propósito: las dos operaciones pasan por la cola offline y se
 * sincronizan solas cuando vuelve la señal (ver OperacionesPendientesService). "Ventas y facturas"
 * sí necesita conexión, así que esa opción sola se oculta sin señal (y sin facturación configurada).
 */
@Component({
  selector: 'app-menu-caja',
  imports: [LucideWallet, LucideChevronDown, LucideArrowLeftRight, LucideTicket, LucideReceiptText],
  templateUrl: './menu-caja.html',
  styleUrl: './menu-caja.css',
})
export class MenuCaja {
  private host = inject(ElementRef<HTMLElement>);

  retiro = output<void>();
  talonario = output<void>();
  ventas = output<void>();

  abierto = signal(false);

  private facturacion = inject(FacturaService).habilitada;
  private enLinea = inject(ConectividadService).enLinea;
  readonly mostrarVentas = computed(() => this.facturacion() && this.enLinea());

  elegir(accion: 'retiro' | 'talonario' | 'ventas'): void {
    this.abierto.set(false);
    if (accion === 'retiro') this.retiro.emit();
    else if (accion === 'talonario') this.talonario.emit();
    else this.ventas.emit();
  }

  @HostListener('document:click', ['$event'])
  alHacerClick(evento: MouseEvent): void {
    if (this.abierto() && !this.host.nativeElement.contains(evento.target as Node)) {
      this.abierto.set(false);
    }
  }

  @HostListener('document:keydown.escape')
  alApretarEscape(): void {
    this.abierto.set(false);
  }
}
