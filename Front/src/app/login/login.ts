import { Component, ElementRef, ViewChild, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { LucideUser, LucideX } from '@lucide/angular';
import { CuentaReciente, SesionService } from '../Services/sesion.service';

@Component({
  selector: 'app-login',
  imports: [FormsModule, LucideUser, LucideX],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login {
  private sesion = inject(SesionService);
  private router = inject(Router);

  @ViewChild('inputPassword') private inputPassword?: ElementRef<HTMLInputElement>;

  readonly cuentasRecientes = this.sesion.cuentasRecientes;

  username = signal('');
  password = signal('');
  ingresando = signal(false);
  error = signal<string | null>(null);

  /** Precarga el usuario. La contraseña sólo se precarga si es una cuenta de BOLETERO (se guarda
   *  para agilizar el cambio de turno en el dispositivo compartido); para ADMIN nunca se guarda,
   *  así que el foco pasa a esa contraseña para que la tipee. */
  elegirCuenta(cuenta: CuentaReciente): void {
    this.username.set(cuenta.username);
    this.password.set(cuenta.password ?? '');
    if (!cuenta.password) {
      this.inputPassword?.nativeElement.focus();
    }
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

    this.sesion.login(username, password).subscribe({
      next: () => {
        this.ingresando.set(false);
        this.router.navigateByUrl('/boleteria');
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
