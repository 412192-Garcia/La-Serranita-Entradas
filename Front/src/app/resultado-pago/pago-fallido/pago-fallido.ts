import { ChangeDetectorRef, Component, NgZone, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { LucideCircleX } from '@lucide/angular';
import { switchMap } from 'rxjs/operators';
import { CompraService } from '../../services/compra.service';
import { cerrarOVolverAlSitio, esVentanaDePago } from '../ventana-resultado.util';

@Component({
  selector: 'app-pago-fallido',
  imports: [LucideCircleX],
  templateUrl: './pago-fallido.html',
  styleUrl: './pago-fallido.css',
})
export class PagoFallido implements OnInit {
  codigoReserva: string | null = null;
  esPopup = false;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private compraService: CompraService,
    private ngZone: NgZone,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.esPopup = esVentanaDePago();

    // Mercado Pago manda el código de reserva como external_reference.
    const codigo = this.route.snapshot.queryParamMap.get('external_reference');
    if (!codigo) return;

    // Antes de asumir que falló, se reconcilia directo contra Mercado Pago: si en realidad
    // el pago sí se aprobó (MP redirigió acá por un problema transitorio pero el cobro se
    // concretó), se muestra la pantalla de éxito en vez de la de fallo.
    this.compraService.obtenerCompraPorCodigo(codigo).pipe(
      switchMap((compra) => {
        this.ngZone.run(() => {
          this.codigoReserva = compra.codigoReserva;
          this.cdr.detectChanges();
        });
        return this.compraService.verificarPago(compra.id);
      })
    ).subscribe({
      next: (res) => {
        if (res.estado === 'APROBADO' || res.estado === 'USADO') {
          this.router.navigate(['/pago-exitoso'], { queryParams: { external_reference: codigo } });
        }
      },
      error: (err) => console.error('No se pudo verificar la compra:', err),
    });
  }

  cerrar(): void {
    cerrarOVolverAlSitio();
  }
}
