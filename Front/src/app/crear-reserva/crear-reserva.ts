import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { Calendario } from '../calendario/calendario';
import { SeleccionEntradas } from '../seleccion-entradas/seleccion-entradas';
import { FormCliente } from '../form-cliente/form-cliente';
import { CompraService, CompraResponseDTO } from '../Services/compra.service';
import { CabeceraInterna } from '../shared/cabecera-interna/cabecera-interna';

enum EtapaReserva {
  SELECCION,
  DATOS,
  CONFIRMAR,
}

interface EntradaSeleccion {
  id: number;
  nombre: string;
  precioUnitario: number;
  cantidad: number;
  subtotal: number;
}

/** Lo que se va acumulando entre los tres pasos, antes de mandarlo al backend. Siempre es una
 * entrada normal con fecha, nunca el "regalo sin fecha fija" de la compra online: sólo que no se cobra. */
interface DatosReserva {
  fechaVisita: Date | null;
  entradas: EntradaSeleccion[];
  subtotal: number;
  cliente: any;
}

function datosVacios(): DatosReserva {
  return { fechaVisita: null, entradas: [], subtotal: 0, cliente: null };
}

@Component({
  selector: 'app-crear-reserva',
  imports: [FormsModule, CurrencyPipe, DatePipe, Calendario, SeleccionEntradas, FormCliente, CabeceraInterna],
  templateUrl: './crear-reserva.html',
  styleUrl: './crear-reserva.css',
})
export class CrearReserva {
  private compraService: CompraService;

  constructor(compraService: CompraService) {
    this.compraService = compraService;
  }

  readonly etapaReserva = EtapaReserva;

  etapa = signal(EtapaReserva.SELECCION);
  datos: DatosReserva = datosVacios();

  creando = signal(false);
  error = signal<string | null>(null);
  resultado = signal<CompraResponseDTO | null>(null);

  /** El DNI ya está registrado a nombre de otra persona: se ofrece confirmar, corregir o marcar que es otra. */
  dniAConfirmar = signal<string | null>(null);
  actualizarDatosCliente = signal(false);

  onFecha(fecha: Date | null): void {
    this.datos.fechaVisita = fecha;
  }

  onPasoSeleccion(datosPaso: any): void {
    this.datos.fechaVisita = datosPaso.fechaVisita;
    this.datos.entradas = datosPaso.entradas;
    this.datos.subtotal = datosPaso.subtotal;
    this.etapa.set(EtapaReserva.DATOS);
  }

  onPasoDatos(datosPaso: any): void {
    this.datos.cliente = datosPaso.cliente;
    this.etapa.set(EtapaReserva.CONFIRMAR);
  }

  volver(): void {
    if (this.etapa() === EtapaReserva.CONFIRMAR) this.etapa.set(EtapaReserva.DATOS);
    else if (this.etapa() === EtapaReserva.DATOS) this.etapa.set(EtapaReserva.SELECCION);
  }

  confirmar(confirmarDniExistente = false, esOtraPersona = false): void {
    this.creando.set(true);
    this.error.set(null);
    this.dniAConfirmar.set(null);
    if (!confirmarDniExistente) {
      this.actualizarDatosCliente.set(false);
    }

    let fechaFormateada: string | null = null;
    if (this.datos.fechaVisita) {
      fechaFormateada = new Date(this.datos.fechaVisita).toISOString().split('T')[0];
    }

    const payload = {
      cliente: {
        dni: this.datos.cliente?.dni,
        nombre: this.datos.cliente?.nombre,
        apellido: this.datos.cliente?.apellido,
        email: this.datos.cliente?.email,
        telefono: this.datos.cliente?.telefono,
        edad: this.datos.cliente?.edad || null,
        localidad: this.datos.cliente?.localidad || null,
      },
      fecha: fechaFormateada,
      entradas: this.datos.entradas.map((e) => ({ tipoEntradaId: e.id, cantidad: e.cantidad })),
      receptor: null,
      confirmarDniExistente,
      actualizarDatosCliente: confirmarDniExistente ? this.actualizarDatosCliente() : false,
      esOtraPersona,
    };

    this.compraService.generarReserva(payload).subscribe({
      next: (res) => {
        this.creando.set(false);
        this.resultado.set(res);
      },
      error: (err) => {
        console.error('Error al generar la reserva:', err);
        const mensaje = typeof err?.error === 'string' ? err.error : null;
        const marcador = 'DNI_YA_REGISTRADO:';
        if (mensaje?.startsWith(marcador)) {
          this.creando.set(false);
          this.dniAConfirmar.set(mensaje.slice(marcador.length).trim());
          return;
        }
        this.creando.set(false);
        this.error.set(mensaje ?? 'No se pudo generar la reserva. Reintentá.');
      },
    });
  }

  /** El admin confirmó que sí es la misma persona del DNI ya registrado: reintenta el mismo pedido. */
  confirmarDniYContinuar(): void {
    this.confirmar(true);
  }

  /** El admin aclaró que NO es la misma persona: se crea un cliente aparte con el mismo DNI. */
  noSoyYo(): void {
    this.confirmar(false, true);
  }

  /** Prefiere revisar los datos en vez de confirmar — probablemente un DNI mal tipeado. */
  corregirDatosPorDni(): void {
    this.dniAConfirmar.set(null);
    this.etapa.set(EtapaReserva.DATOS);
  }

  /** Deja todo listo para cargar la próxima reserva. */
  nuevaReserva(): void {
    this.resultado.set(null);
    this.error.set(null);
    this.datos = datosVacios();
    this.etapa.set(EtapaReserva.SELECCION);
  }
}
