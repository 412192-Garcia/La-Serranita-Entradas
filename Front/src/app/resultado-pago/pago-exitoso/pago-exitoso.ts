import { ChangeDetectorRef, Component, Input, NgZone, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { LucideCircleCheck } from '@lucide/angular';
import { CompraService } from '../../services/compra.service';

@Component({
  selector: 'app-pago-exitoso',
  imports: [LucideCircleCheck],
  templateUrl: './pago-exitoso.html',
  styleUrl: './pago-exitoso.css',
})
export class PagoExitoso implements OnInit {
  @Input() codigoReserva: string | null = null;
  @Input() compraAcumulada: any = null;

  /** Sólo se muestra cuando se llega por el redirect de Mercado Pago (no embebido en Entradas). */
  esRutaDirecta = false;
  verificando = false;
  noEncontrada = false;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private compraService: CompraService,
    private ngZone: NgZone,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    // Si ya llegó embebido en el flujo de compra (con los @Input ya cargados por
    // Entradas), no hay nada más que resolver: eso sólo aplica cuando Mercado Pago
    // redirige acá directamente (back_url), fuera de ese flujo.
    if (this.codigoReserva) return;

    this.esRutaDirecta = true;
    this.route.queryParams.subscribe(params => {
      const compraIdStr = params['external_reference'];
      const compraId = compraIdStr ? +compraIdStr : null;
      if (!compraId) {
        this.noEncontrada = true;
        return;
      }

      this.verificando = true;
      // Reconciliación directa contra Mercado Pago: no depende de que el webhook
      // haya llegado (por ejemplo, si el túnel de notificaciones estaba caído).
      this.compraService.verificarPago(compraId).subscribe({
        next: () => this.cargarCompra(compraId),
        error: () => this.cargarCompra(compraId),
      });
    });
  }

  private cargarCompra(compraId: number): void {
    this.compraService.obtenerCompra(compraId).subscribe({
      next: (compra) => {
        this.ngZone.run(() => {
          this.codigoReserva = compra.codigoReserva;
          this.compraAcumulada = { cliente: { dni: compra.cliente?.dni ?? null } };
          this.noEncontrada = compra.estado !== 'APROBADO' && compra.estado !== 'USADO';
          this.verificando = false;
          this.cdr.detectChanges();
        });
      },
      error: (err) => {
        console.error('No se pudo resolver la compra:', err);
        this.ngZone.run(() => {
          this.noEncontrada = true;
          this.verificando = false;
          this.cdr.detectChanges();
        });
      },
    });
  }

  irAInicio(): void {
    this.router.navigate(['/']);
  }
}
