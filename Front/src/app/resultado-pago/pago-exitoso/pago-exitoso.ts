import { ChangeDetectorRef, Component, Input, NgZone, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { LucideCircleCheck } from '@lucide/angular';
import { of } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { CompraService, CompraResponseDTO } from '../../services/compra.service';
import { AnaliticaService } from '../../services/analitica.service';
import { cerrarOVolverAlSitio, esVentanaDePago } from '../ventana-resultado.util';

@Component({
  selector: 'app-pago-exitoso',
  imports: [LucideCircleCheck],
  templateUrl: './pago-exitoso.html',
  styleUrl: './pago-exitoso.css',
})
export class PagoExitoso implements OnInit {
  @Input() codigoReserva: string | null = null;
  @Input() compraAcumulada: any = null;

  /** Llegó por la vuelta de Mercado Pago (back_url), no embebida en el flujo de Entradas. */
  esRutaDirecta = false;
  esPopup = false;
  verificando = false;
  noEncontrada = false;

  constructor(
    private route: ActivatedRoute,
    private compraService: CompraService,
    private analitica: AnaliticaService,
    private ngZone: NgZone,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    // Embebida en el flujo de compra (con los @Input ya cargados por Entradas) no hay nada
    // que resolver: eso sólo aplica cuando Mercado Pago redirige acá directamente.
    if (this.codigoReserva) return;

    this.esRutaDirecta = true;
    this.esPopup = esVentanaDePago();

    // Mercado Pago manda el código de reserva como external_reference.
    const codigo = this.route.snapshot.queryParamMap.get('external_reference');
    if (!codigo) {
      this.noEncontrada = true;
      return;
    }

    this.verificando = true;
    // Reconciliación directa contra Mercado Pago: no depende de que el webhook haya llegado.
    // Si la verificación falla igual se muestra lo que diga la base.
    this.compraService.obtenerCompraPorCodigo(codigo).pipe(
      switchMap((compra) => this.compraService.verificarPago(compra.id).pipe(
        catchError(() => of(null)),
        switchMap(() => this.compraService.obtenerCompra(compra.id))
      ))
    ).subscribe({
      next: (compra) => this.mostrar(compra),
      error: (err) => {
        console.error('No se pudo resolver la compra:', err);
        this.mostrar(null);
      },
    });
  }

  private mostrar(compra: CompraResponseDTO | null): void {
    this.ngZone.run(() => {
      if (compra) {
        this.codigoReserva = compra.codigoReserva;
        this.compraAcumulada = { cliente: { dni: compra.cliente?.dni ?? null } };
      }
      this.noEncontrada = !compra || (compra.estado !== 'APROBADO' && compra.estado !== 'USADO');
      this.verificando = false;
      this.cdr.detectChanges();
    });

    // En el popup la conversión la registra la ventana que lo abrió. Acá sólo cuando quedó sola
    // (pestaña nueva al volver desde la app de MP, o un popup que perdió su vínculo): si no, esa
    // venta no se contaría nunca.
    if (compra && compra.estado === 'APROBADO' && !this.esPopup) {
      this.analitica.registrarCompra({ codigoReserva: compra.codigoReserva, montoTotal: compra.montoTotal });
    }
  }

  cerrar(): void {
    cerrarOVolverAlSitio();
  }
}
