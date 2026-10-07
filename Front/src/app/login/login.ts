import { Component, ElementRef, ViewChild, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { LucideEye, LucideEyeOff, LucideUser, LucideX } from '@lucide/angular';
import { CuentaReciente, SesionService, nombreRol } from '../services/sesion.service';

@Component({
  selector: 'app-login',
  imports: [FormsModule, LucideEye, LucideEyeOff, LucideUser, LucideX],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login {
  private sesion = inject(SesionService);
  private router = inject(Router);

  @ViewChild('inputPassword') private inputPassword?: ElementRef<HTMLInputElement>;

  readonly cuentasRecientes = this.sesion.cuentasRecientes;
  readonly nombreRol = nombreRol;

  username = signal('');
  password = signal('');
  mostrarPassword = signal(false);
  /** Desmarcada por defecto: cada quien decide si quiere mantenerla en su dispositivo. Si es un
   * POS sin internet donde importa no tener que volver a loguearse (el login necesita
   * conexión), el boletero la tilda a mano. */
  mantenerSesion = signal(false);
  ingresando = signal(false);
  error = signal<string | null>(null);

  /** Precarga sólo el usuario: la contraseña nunca se guarda (ver CuentaReciente), así que
   *  el foco pasa directo ahí para que la tipee. */
  elegirCuenta(cuenta: CuentaReciente): void {
    this.username.set(cuenta.username);
    this.password.set('');
    this.inputPassword?.nativeElement.focus();
  }

  quitarCuenta(username: string, evento: Event): void {
    evento.stopPropagation();
    this.sesion.quitarCuentaReciente(username);
  }

  ingresar(): void {
    const username = this.username().trim();
    const password = this.password();
    if (!username || !password || this.ingresando()) return;

    this.ingresando.set(true);
    this.error.set(null);

    this.sesion.login(username, password, this.mantenerSesion()).subscribe({
      next: () => {
        this.ingresando.set(false);
        // El admin arranca en el dashboard de hoy; el boletero, directo a vender (Control de
        // Accesos es del admin: el boletero valida y cobra anticipadas desde el POS).
        this.router.navigateByUrl(this.sesion.esAdmin() ? '/hoy' : '/pos');
      },
      error: (err) => {
        console.error('Error al iniciar sesión:', err);
        this.error.set(
          err.status === 401
            ? 'Usuario o contraseña incorrectos.'
            : 'No se pudo conectar con el servidor. Reintentá.'
        );
        this.ingresando.set(false);
      },
    });
  }
}
