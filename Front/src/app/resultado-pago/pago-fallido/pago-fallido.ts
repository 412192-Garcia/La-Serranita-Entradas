import { ChangeDetectorRef, Component, NgZone } from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import { LucideCircleX } from '@lucide/angular';
import { CompraService } from '../../Services/compra.service';

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

      // El external_reference de MP es el id numérico interno; lo usamos solo
      // para resolver el código de reserva visible (yyMMdd-N).
      if (this.compraId) {
        this.compraService.obtenerCompra(+this.compraId).subscribe({
          next: (compra) => {
            this.ngZone.run(() => {
              this.codigoReserva = compra.codigoReserva;
              this.cdr.detectChanges();
            });
          },
          error: (err) => console.error('No se pudo resolver el código de reserva:', err)
        });
      }
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
