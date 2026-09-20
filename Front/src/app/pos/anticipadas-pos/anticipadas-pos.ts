import { Component, HostListener, OnInit, effect, inject, input, output, untracked, viewChild } from '@angular/core';
import { Reserva } from '../../services/boleteria.service';
import { ReservasBusqueda } from '../../reservas/busqueda-reservas';
import { BuscadorReservas } from '../../reservas/buscador-reservas/buscador-reservas';
import { PanelRegalos } from '../../reservas/panel-regalos/panel-regalos';
import { ListadoReservas } from '../../reservas/listado-reservas/listado-reservas';

/**
 * Panel de anticipadas dentro del POS: tapa el catálogo/carrito (sin destruirlos) para validar
 * el ingreso de quien ya tiene una entrada, con el mismo buscador, listado y filas que Control de
 * Accesos pero sin filtros ni el menú ⋮ (editar, reenviar mail y reembolsar son de esa pantalla,
 * sólo del admin). No tiene cabecera ni botón de cerrar: el título lo pone la cabecera del POS
 * y se vuelve a la venta con el botón "Volver a la venta" de esa misma cabecera.
 *
 * Se abre de dos formas (ver Pos.abrirAnticipadas): escaneando un DNI, que llega en `consulta` y
 * se busca solo, o a mano (botón "Anticipadas" o F2), con `consulta` vacía: se muestra la lista de
 * hoy y el boletero busca. Una APROBADO se valida acá mismo; una RESERVADO_EFECTIVO no se cobra
 * acá sino en el carrito del POS (`cobrarEnPos`), para elegir la forma de pago y que la venta
 * pase por la cola offline como cualquier otra.
 */
@Component({
  selector: 'app-anticipadas-pos',
  imports: [BuscadorReservas, PanelRegalos, ListadoReservas],
  providers: [ReservasBusqueda],
  templateUrl: './anticipadas-pos.html',
  styleUrl: './anticipadas-pos.css',
})
export class AnticipadasPos implements OnInit {
  private busqueda = inject(ReservasBusqueda);

  /** DNI recién escaneado. Vacío = se abre con la lista de hoy, sin buscar nada. */
  consulta = input('');
  cobrarEnPos = output<Reserva>();

  private buscador = viewChild(BuscadorReservas);
  private consultaAplicada = '';

  /** En táctil no se enfoca el campo al abrir: levantaría el teclado encima de la lista del día. */
  readonly autoenfocar = !window.matchMedia('(pointer: coarse)').matches;

  constructor() {
    // El POS puede escanear otro DNI con el panel ya abierto: se rehace la búsqueda.
    effect(() => {
      const consulta = this.consulta();
      untracked(() => {
        if (consulta === this.consultaAplicada) return;
        this.consultaAplicada = consulta;
        if (consulta) {
          this.busqueda.texto.set(consulta);
          this.busqueda.buscar();
        }
      });
    });
  }

  ngOnInit(): void {
    this.consultaAplicada = this.consulta();
    if (this.consultaAplicada) {
      this.busqueda.texto.set(this.consultaAplicada);
      this.busqueda.buscar();
    } else {
      this.busqueda.ejecutarBusqueda();
    }
  }

  /** F2 con el panel ya abierto: vuelve al buscador para atender a otra persona. */
  @HostListener('window:keydown.f2', ['$event'])
  enfocarBuscador(event: Event): void {
    event.preventDefault();
    this.buscador()?.enfocar();
  }
}
