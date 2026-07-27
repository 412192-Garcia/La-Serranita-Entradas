import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { SesionService } from '../Services/sesion.service';

@Component({
  selector: 'app-login',
  imports: [FormsModule, RouterLink],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login {
  private sesion = inject(SesionService);
  private router = inject(Router);

  username = signal('');
  password = signal('');
  ingresando = signal(false);
  error = signal<string | null>(null);

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
