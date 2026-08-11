import { ChangeDetectorRef, Component, NgZone } from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import { LucideCircleX } from '@lucide/angular';
import { CompraService } from '../../services/compra.service';

@Component({
  selector: 'app-pago-fallido',
  imports: [LucideCircleX],
  templateUrl: './pago-fallido.html',
  styleUrl: './pago-fallido.css',
})
export class PagoFallido {
  compraId: string | null = null;
  codigoReserva: string | null = null;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private compraService: CompraService,
    private ngZone: NgZone,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    // Mercado Pago envía parámetros por la URL cuando falla el pago
    this.route.queryParams.subscribe(params => {
      this.compraId = params['external_reference'];
      if (!this.compraId) return;

      const compraId = +this.compraId;

      // Antes de asumir que falló, se reconcilia directo contra Mercado Pago: si
      // en realidad el pago sí se aprobó (por ejemplo, MP redirigió acá por un
      // problema transitorio pero el cobro se concretó), se muestra la pantalla
      // de éxito en vez de la de fallo.
      this.compraService.verificarPago(compraId).subscribe({
        next: (res) => {
          if (res.estado === 'APROBADO' || res.estado === 'USADO') {
            this.router.navigate(['/pago-exitoso'], { queryParams: { external_reference: this.compraId } });
            return;
          }
          this.resolverCodigoReserva(compraId);
        },
        error: () => this.resolverCodigoReserva(compraId),
      });
    });
  }

  /** El external_reference de MP es el id numérico interno; se usa sólo para
   *  resolver el código de reserva visible (yyMMdd-N). */
  private resolverCodigoReserva(compraId: number): void {
    this.compraService.obtenerCompra(compraId).subscribe({
      next: (compra) => {
        this.ngZone.run(() => {
          this.codigoReserva = compra.codigoReserva;
          this.cdr.detectChanges();
        });
      },
      error: (err) => console.error('No se pudo resolver el código de reserva:', err)
    });
  }

  reintentarPago(): void {
    // Redirige al resumen o al carrito para volver a disparar el flujo
    this.router.navigate(['/entradas']);
  }

  irAInicio(): void {
    this.router.navigate(['/']);
  }
}
