import { Component, DestroyRef, OnInit, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { LucideSearch } from '@lucide/angular';
import { Pagina, RegistroAuditoria } from '../../models/sistema';
import { SistemaService } from '../../services/sistema.service';
import { Spinner } from '../../shared/spinner/spinner';
import { aFechaISO } from '../../shared/fecha.util';

/** Nombre para el filtro de cada código de acción del historial (los que no estén, se muestran con el código). */
const NOMBRES_ACCION: Record<string, string> = {
  LOGIN_OK: 'Inicio de sesión',
  LOGIN_FALLIDO: 'Inicio de sesión fallido',
  PASSWORD_CAMBIADA: 'Cambio de contraseña',
  VENTA_CANCELADA: 'Venta cancelada',
  VENTA_EDITADA: 'Venta editada',
  CONTACTO_MODIFICADO: 'Contacto editado',
  COMPRA_REEMBOLSADA: 'Compra reembolsada',
  PAGO_EFECTIVO_CONFIRMADO: 'Pago en efectivo confirmado',
  MAIL_REENVIADO: 'Mail de compra reenviado',
  VALIDACION_DESHECHA: 'Validación deshecha',
  RESERVA_GENERADA: 'Reserva manual',
  FACTURA_MANUAL: 'Factura manual',
  FACTURA_ANULADA: 'Factura anulada',
  FACTURA_REINTENTADA: 'Factura reintentada',
  FACTURA_ENVIADA: 'Factura mandada por mail',
  FACTURA_REENVIADA: 'Factura reenviada',
  VENTA_FACTURADA: 'Venta facturada a mano',
  COMPRA_ONLINE_FACTURADA: 'Compra online facturada a mano',
  NUMERACION_CONTROLADA: 'Control de numeración',
  FACTURA_IMPRESA: 'Factura impresa',
  CAJA_ABIERTA: 'Caja abierta',
  CAJA_ABIERTA_SIN_CONTROL: 'Caja abierta sin control',
  CAJA_CERRADA: 'Caja cerrada',
  CAJA_CERRADA_SIN_CONTROL: 'Caja cerrada sin control',
  CAJA_CORREGIDA: 'Caja corregida',
  CAJA_DESHABILITADA: 'Caja borrada',
  AJUSTE_CAJA_DESHECHO: 'Ajuste de caja deshecho',
  RETIRO_APORTE: 'Retiro / aporte',
  ENTRADAS_FISICAS: 'Entradas físicas',
  RECHAZO_RESUELTO: 'Rechazo resuelto',
  RECHAZO_REINTENTADO: 'Rechazo reintentado',
  RECHAZOS_REINTENTADOS: 'Rechazos reintentados',
  TIPO_ENTRADA_CREADO: 'Tipo de entrada creado',
  TIPO_ENTRADA_MODIFICADO: 'Tipo de entrada modificado',
  TIPO_ENTRADA_ELIMINADO: 'Tipo de entrada eliminado',
  TIPOS_ENTRADA_REORDENADOS: 'Tipos de entrada reordenados',
  ARTICULO_CREADO: 'Artículo creado',
  ARTICULO_MODIFICADO: 'Artículo modificado',
  ARTICULO_ELIMINADO: 'Artículo eliminado',
  PRECIO_GRUPO_CREADO: 'Precio por grupo creado',
  PRECIO_GRUPO_MODIFICADO: 'Precio por grupo modificado',
  PRECIO_GRUPO_ELIMINADO: 'Precio por grupo eliminado',
  PROMOCION_CREADA: 'Promoción creada',
  PROMOCION_MODIFICADA: 'Promoción modificada',
  PROMOCION_ELIMINADA: 'Promoción eliminada',
  CUPON_CREADO: 'Cupón creado',
  CUPON_MODIFICADO: 'Cupón modificado',
  CUPONES_GENERADOS: 'Cupones generados',
  FAMILIA_CUPONES_ACTIVADA: 'Familia de cupones activada/desactivada',
  FAMILIA_CUPONES_VENCIMIENTO: 'Vencimiento de cupones',
  HORARIO_MODIFICADO: 'Horario del parque',
  DIA_APERTURA_MODIFICADO: 'Día abierto/cerrado',
  DIA_HORARIO_MODIFICADO: 'Horario de un día',
  USUARIO_CREADO: 'Usuario creado',
  USUARIO_MODIFICADO: 'Usuario modificado',
  USUARIO_ELIMINADO: 'Usuario eliminado',
  ERROR_RESUELTO: 'Error marcado resuelto',
  OTRA: 'Otras',
};

/** Sistema > Historial de acciones: quién hizo qué y cuándo, con filtros. */
@Component({
  selector: 'app-historial-acciones',
  imports: [DatePipe, FormsModule, Spinner, LucideSearch],
  templateUrl: './historial-acciones.html',
  styleUrl: './historial-acciones.css',
})
export class HistorialAcciones implements OnInit {
  private sistemaService = inject(SistemaService);
  private destroyRef = inject(DestroyRef);

  accionInicial = input<string | null>(null);

  desde = signal(aFechaISO(new Date(Date.now() - 7 * 86_400_000)));
  hasta = signal(aFechaISO(new Date()));
  usuario = signal('');
  accion = signal('');
  texto = signal('');

  acciones = signal<string[]>([]);
  usuarios = signal<string[]>([]);

  pagina = signal<Pagina<RegistroAuditoria> | null>(null);
  numeroPagina = signal(0);
  cargando = signal(true);
  error = signal<string | null>(null);
  abiertoId = signal<number | null>(null);

  ngOnInit(): void {
    if (this.accionInicial()) this.accion.set(this.accionInicial()!);
    this.sistemaService.filtrosAuditoria().subscribe({
      next: (f) => {
        this.acciones.set(f.acciones);
        this.usuarios.set(f.usuarios);
      },
      error: () => {},
    });
    this.buscar();
  }

  nombreAccion(codigo: string): string {
    return NOMBRES_ACCION[codigo] ?? codigo;
  }

  buscar(pagina = 0): void {
    this.cargando.set(true);
    this.error.set(null);
    this.numeroPagina.set(pagina);
    this.sistemaService
      .auditoria({ desde: this.desde(), hasta: this.hasta(), usuario: this.usuario(), accion: this.accion(), texto: this.texto(), pagina })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (p) => {
          this.pagina.set(p);
          this.cargando.set(false);
        },
        error: () => {
          this.cargando.set(false);
          this.error.set('No se pudo consultar el historial.');
        },
      });
  }

  alternar(r: RegistroAuditoria): void {
    this.abiertoId.set(this.abiertoId() === r.id ? null : r.id);
  }
}
