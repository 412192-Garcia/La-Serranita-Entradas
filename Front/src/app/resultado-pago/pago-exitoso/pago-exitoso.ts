import {Component, Input} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';

@Component({
  selector: 'app-pago-exitoso',
  imports: [],
  templateUrl: './pago-exitoso.html',
  styleUrl: './pago-exitoso.css',
})
export class PagoExitoso {
  compraId: string | null = null;
  estadoPago: string | null = null;

  @Input() compraIdActual: number | null = null;
  @Input() compraAcumulada: any = null;

  constructor(private route: ActivatedRoute, private router: Router) {}

  ngOnInit(): void {
    this.route.queryParams.subscribe(params => {
      this.estadoPago = params['collection_status'] || params['status'];
      this.compraId = params['external_reference'];

      console.log('Estado del pago:', this.estadoPago);
      console.log('ID Compra:', this.compraId);
    });
  }

  irAInicio(): void {
    this.router.navigate(['/']);
  }
}
