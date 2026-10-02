import { Component, ElementRef, HostListener, inject, output, signal } from '@angular/core';
import { LucideArrowLeftRight, LucideChevronDown, LucideTicket, LucideWallet } from '@lucide/angular';

/**
 * Botón "Caja" de la barra de la pantalla de venta: agrupa Retiro/Aporte y Reponer talonario, que
 * antes eran dos botones sueltos y ya no entraban cómodos junto a Anticipadas y Ticketera.
 *
 * Sigue visible sin conexión a propósito: las dos operaciones pasan por la cola offline y se
 * sincronizan solas cuando vuelve la señal (ver OperacionesPendientesService).
 */
@Component({
  selector: 'app-menu-caja',
  imports: [LucideWallet, LucideChevronDown, LucideArrowLeftRight, LucideTicket],
  templateUrl: './menu-caja.html',
  styleUrl: './menu-caja.css',
})
export class MenuCaja {
  private host = inject(ElementRef<HTMLElement>);

  retiro = output<void>();
  talonario = output<void>();

  abierto = signal(false);

  elegir(accion: 'retiro' | 'talonario'): void {
    this.abierto.set(false);
    if (accion === 'retiro') this.retiro.emit();
    else this.talonario.emit();
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
