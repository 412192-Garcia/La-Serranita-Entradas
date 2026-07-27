import {Component, Input} from '@angular/core';
import {Router} from '@angular/router';
import { LucideCircleCheck } from '@lucide/angular';

@Component({
  selector: 'app-pago-exitoso',
  imports: [LucideCircleCheck],
  templateUrl: './pago-exitoso.html',
  styleUrl: './pago-exitoso.css',
})
export class PagoExitoso {
  @Input() codigoReserva: string | null = null;
  @Input() compraAcumulada: any = null;

  constructor(private router: Router) {}

  irAInicio(): void {
    this.router.navigate(['/']);
  }
}
