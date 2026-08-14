import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LucideUser } from '@lucide/angular';
import { UsuarioService } from '../services/usuario.service';
import { SesionService } from '../services/sesion.service';
import { PALETAS_PREARMADAS, PALETAS_FONDO, PALETAS_TARJETA, PALETAS_BORDE, DISENIOS_PREARMADOS, DisenioPrearmado, ThemeService } from '../services/theme.service';
import { CabeceraInterna } from '../shared/cabecera-interna/cabecera-interna';

const PRIMARIO_POR_DEFECTO = '#39a935';
const FONDO_POR_DEFECTO = '#f4f5f7';
const TARJETA_POR_DEFECTO = '#ffffff';
const BORDE_POR_DEFECTO = '#e5e7eb';

/** Lado del avatar ya recortado a cuadrado, en píxeles: alcanza de sobra para un círculo chico en la cabecera y en "Mi cuenta". */
const FOTO_LADO_PX = 200;

/**
 * Configuración personal de cualquier usuario logueado (BOLETERO o ADMIN), a diferencia
 * de configuracion/usuarios que es ADMIN-only y gestiona a terceros: cambio de contraseña
 * propia y personalización del color de la interfaz.
 */
@Component({
  selector: 'app-mi-cuenta',
  imports: [FormsModule, CabeceraInterna, LucideUser],
  templateUrl: './mi-cuenta.html',
  styleUrls: ['../configuracion/configuracion-shared.css', './mi-cuenta.css'],
})
export class MiCuenta {
  private usuarioService = inject(UsuarioService);
  private sesion = inject(SesionService);
  private theme = inject(ThemeService);

  readonly operador = this.sesion.usuario;

  // ---------- Foto de perfil ----------
  fotoPerfil = signal<string | null>(this.operador()?.fotoPerfil ?? null);
  guardandoFoto = signal(false);
  errorFoto = signal<string | null>(null);

  passwordActual = signal('');
  passwordNueva = signal('');
  passwordConfirmar = signal('');
  guardando = signal(false);
  error = signal<string | null>(null);
  mensaje = signal<string | null>(null);

  // ---------- Apariencia: paletas prearmadas + color libre "a tu propio riesgo" ----------
  readonly paletasPrimario = PALETAS_PREARMADAS;
  readonly paletasFondo = PALETAS_FONDO;
  readonly paletasTarjeta = PALETAS_TARJETA;
  readonly paletasBorde = PALETAS_BORDE;
  readonly disenios = DISENIOS_PREARMADOS;
  colorTema = signal<string | null>(this.operador()?.colorTema ?? null);
  colorFondo = signal<string | null>(this.operador()?.colorFondo ?? null);
  colorTarjeta = signal<string | null>(this.operador()?.colorTarjeta ?? null);
  colorBorde = signal<string | null>(this.operador()?.colorBorde ?? null);
  guardandoTema = signal(false);
  errorTema = signal<string | null>(null);

  /** Lo que se ve en cada picker nativo: si no hay nada personalizado, arranca en el valor por defecto. */
  colorTemaParaPicker = computed(() => this.colorTema() ?? PRIMARIO_POR_DEFECTO);
  colorFondoParaPicker = computed(() => this.colorFondo() ?? FONDO_POR_DEFECTO);
  colorTarjetaParaPicker = computed(() => this.colorTarjeta() ?? TARJETA_POR_DEFECTO);
  colorBordeParaPicker = computed(() => this.colorBorde() ?? BORDE_POR_DEFECTO);

  /** Si hay algo personalizado (en cualquiera de los cuatro), habilita el botón de restablecer todo. */
  hayPersonalizacion = computed(
    () => this.colorTema() !== null || this.colorFondo() !== null || this.colorTarjeta() !== null || this.colorBorde() !== null
  );

  cambiarPassword(): void {
    if (this.guardando()) return;

    const actual = this.passwordActual();
    const nueva = this.passwordNueva();
    const confirmar = this.passwordConfirmar();

    this.mensaje.set(null);

    if (!actual || !nueva || !confirmar) {
      this.error.set('Completá los tres campos.');
      return;
    }
    if (nueva.length < 6) {
      this.error.set('La contraseña nueva tiene que tener al menos 6 caracteres.');
      return;
    }
    if (nueva !== confirmar) {
      this.error.set('La confirmación no coincide con la contraseña nueva.');
      return;
    }

    this.guardando.set(true);
    this.error.set(null);

    this.usuarioService.cambiarMiPassword(actual, nueva).subscribe({
      next: () => {
        this.guardando.set(false);
        this.mensaje.set('Contraseña actualizada.');
        this.passwordActual.set('');
        this.passwordNueva.set('');
        this.passwordConfirmar.set('');
      },
      error: (err) => {
        console.error('Error al cambiar la contraseña:', err);
        this.error.set(typeof err?.error === 'string' ? err.error : 'No se pudo cambiar la contraseña. Reintentá.');
        this.guardando.set(false);
      },
    });
  }

  async onSeleccionarFoto(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const archivo = input.files?.[0] ?? null;
    input.value = '';
    if (!archivo) return;

    if (!archivo.type.startsWith('image/')) {
      this.errorFoto.set('El archivo elegido no es una imagen.');
      return;
    }

    try {
      const dataUri = await this.redimensionar(archivo);
      this.guardarFoto(dataUri);
    } catch {
      this.errorFoto.set('No se pudo procesar la imagen. Probá con otra.');
    }
  }

  quitarFoto(): void {
    this.guardarFoto(null);
  }

  /** Se aplica al toque y recién después se confirma contra el backend; si falla, vuelve atrás (mismo patrón que guardarColores). */
  private guardarFoto(fotoPerfil: string | null): void {
    const anterior = this.fotoPerfil();
    if (anterior === fotoPerfil) return;

    this.fotoPerfil.set(fotoPerfil);
    this.guardandoFoto.set(true);
    this.errorFoto.set(null);

    this.usuarioService.cambiarMiFoto(fotoPerfil).subscribe({
      next: () => {
        this.guardandoFoto.set(false);
        this.sesion.actualizarFoto(fotoPerfil);
      },
      error: (err) => {
        console.error('Error al cambiar la foto de perfil:', err);
        this.errorFoto.set(typeof err?.error === 'string' ? err.error : 'No se pudo guardar la foto. Reintentá.');
        this.guardandoFoto.set(false);
        this.fotoPerfil.set(anterior);
      },
    });
  }

  /** Recorta al cuadrado central y reduce a FOTO_LADO_PX antes de subirla: así una foto de varios MB no termina guardada entera en la base. */
  private redimensionar(archivo: File): Promise<string> {
    return new Promise((resolve, reject) => {
      const imagen = new Image();
      const url = URL.createObjectURL(archivo);
      imagen.onload = () => {
        URL.revokeObjectURL(url);
        const lado = Math.min(imagen.width, imagen.height);
        const origenX = (imagen.width - lado) / 2;
        const origenY = (imagen.height - lado) / 2;

        const canvas = document.createElement('canvas');
        canvas.width = FOTO_LADO_PX;
        canvas.height = FOTO_LADO_PX;
        const ctx = canvas.getContext('2d');
        if (!ctx) {
          reject(new Error('sin contexto 2d'));
          return;
        }
        ctx.drawImage(imagen, origenX, origenY, lado, lado, 0, 0, FOTO_LADO_PX, FOTO_LADO_PX);
        resolve(canvas.toDataURL('image/jpeg', 0.85));
      };
      imagen.onerror = () => {
        URL.revokeObjectURL(url);
        reject(new Error('no se pudo cargar la imagen'));
      };
      imagen.src = url;
    });
  }

  elegirPrimario(color: string | null): void {
    this.guardarColores(color, this.colorFondo(), this.colorTarjeta(), this.colorBorde());
  }

  onPrimarioLibre(color: string): void {
    this.guardarColores(color, this.colorFondo(), this.colorTarjeta(), this.colorBorde());
  }

  /** El picker nativo tira un evento "input" por cada frame de arrastre: sólo se previsualiza acá, no se guarda (eso es onPrimarioLibre, en el evento "change" al soltar). */
  previsualizarPrimario(color: string): void {
    this.theme.aplicarPrimario(color);
  }

  elegirFondo(color: string | null): void {
    this.guardarColores(this.colorTema(), color, this.colorTarjeta(), this.colorBorde());
  }

  onFondoLibre(color: string): void {
    this.guardarColores(this.colorTema(), color, this.colorTarjeta(), this.colorBorde());
  }

  previsualizarFondo(color: string): void {
    this.theme.aplicarFondo(color);
  }

  elegirTarjeta(color: string | null): void {
    this.guardarColores(this.colorTema(), this.colorFondo(), color, this.colorBorde());
  }

  onTarjetaLibre(color: string): void {
    this.guardarColores(this.colorTema(), this.colorFondo(), color, this.colorBorde());
  }

  previsualizarTarjeta(color: string): void {
    this.theme.aplicarTarjeta(color);
  }

  elegirBorde(color: string | null): void {
    this.guardarColores(this.colorTema(), this.colorFondo(), this.colorTarjeta(), color);
  }

  onBordeLibre(color: string): void {
    this.guardarColores(this.colorTema(), this.colorFondo(), this.colorTarjeta(), color);
  }

  previsualizarBorde(color: string): void {
    this.theme.aplicarBorde(color);
  }

  /** Diseño prearmado: pisa los 4 colores de una vez con una combinación ya coordinada. */
  elegirDisenio(d: DisenioPrearmado): void {
    this.guardarColores(d.colorPrimario, d.colorFondo, d.colorTarjeta, d.colorBorde);
  }

  /** Un solo botón para volver los cuatro colores a su valor por defecto de una vez. */
  restablecerTodo(): void {
    this.guardarColores(null, null, null, null);
  }

  /** Se aplica al toque (sensación instantánea) y recién después se confirma contra el backend; si falla, vuelve atrás. */
  private guardarColores(
    colorTema: string | null,
    colorFondo: string | null,
    colorTarjeta: string | null,
    colorBorde: string | null
  ): void {
    const temaAnterior = this.colorTema();
    const fondoAnterior = this.colorFondo();
    const tarjetaAnterior = this.colorTarjeta();
    const bordeAnterior = this.colorBorde();
    if (
      temaAnterior === colorTema &&
      fondoAnterior === colorFondo &&
      tarjetaAnterior === colorTarjeta &&
      bordeAnterior === colorBorde
    )
      return;

    this.colorTema.set(colorTema);
    this.colorFondo.set(colorFondo);
    this.colorTarjeta.set(colorTarjeta);
    this.colorBorde.set(colorBorde);
    this.theme.aplicarPrimario(colorTema);
    this.theme.aplicarFondo(colorFondo);
    this.theme.aplicarTarjeta(colorTarjeta);
    this.theme.aplicarBorde(colorBorde);
    this.guardandoTema.set(true);
    this.errorTema.set(null);

    this.usuarioService.cambiarMiTema(colorTema, colorFondo, colorTarjeta, colorBorde).subscribe({
      next: () => {
        this.guardandoTema.set(false);
        this.sesion.actualizarColores(colorTema, colorFondo, colorTarjeta, colorBorde);
      },
      error: (err) => {
        console.error('Error al cambiar el tema:', err);
        this.errorTema.set(typeof err?.error === 'string' ? err.error : 'No se pudo guardar el color. Reintentá.');
        this.guardandoTema.set(false);
        // Vuelve a los colores anteriores: si no se pudo guardar, no tiene sentido dejar la
        // UI mostrando algo que no está persistido (se perdería solo con recargar la página).
        this.colorTema.set(temaAnterior);
        this.colorFondo.set(fondoAnterior);
        this.colorTarjeta.set(tarjetaAnterior);
        this.colorBorde.set(bordeAnterior);
        this.theme.aplicarPrimario(temaAnterior);
        this.theme.aplicarFondo(fondoAnterior);
        this.theme.aplicarTarjeta(tarjetaAnterior);
        this.theme.aplicarBorde(bordeAnterior);
      },
    });
  }
}
