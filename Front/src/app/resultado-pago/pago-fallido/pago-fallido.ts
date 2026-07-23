import { Component } from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';

@Component({
  selector: 'app-pago-fallido',
  imports: [],
  templateUrl: './pago-fallido.html',
  styleUrl: './pago-fallido.css',
})
export class PagoFallido {
  compraId: string | null = null;
  estadoPago: string | null = null;

  constructor(private route: ActivatedRoute, private router: Router) {}

  ngOnInit(): void {
    // Mercado Pago envía parámetros por la URL cuando falla el pago
    this.route.queryParams.subscribe(params => {
      this.estadoPago = params['collection_status'] || params['status'];
      this.compraId = params['external_reference'];

      console.log('Pago fallido/rechazado. Estado:', this.estadoPago, 'ID Compra:', this.compraId);
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
