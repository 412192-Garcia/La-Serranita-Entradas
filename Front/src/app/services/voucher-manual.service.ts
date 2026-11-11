import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

/** EXTRA = artículos (con cantidad); CANTINA = un monto. */
export type TipoVoucher = 'EXTRA' | 'CANTINA';

export interface VoucherManualPedido {
  tipo: TipoVoucher;
  /** EXTRA: el artículo. CANTINA: motivo (opcional). */
  descripcion: string | null;
  cantidad: number | null;
  monto: number | null;
  titular: string | null;
  impresora: string | null;
}

/** Vouchers sueltos para la cantina, sin reserva, que emite el admin desde Acciones. */
@Injectable({ providedIn: 'root' })
export class VoucherManualService {
  private http = inject(HttpClient);
  private url = `${environment.apiBase}/interno/vouchers/manuales`;

  emitir(pedido: VoucherManualPedido): Observable<unknown> {
    return this.http.post(this.url, pedido);
  }
}
