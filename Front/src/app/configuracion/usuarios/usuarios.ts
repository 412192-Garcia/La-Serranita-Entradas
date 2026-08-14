import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { UsuarioService, Usuario } from '../../services/usuario.service';
import { SesionService, Rol } from '../../services/sesion.service';
import { compararTexto, compararBooleano } from '../../shared/orden.util';
import { Spinner } from '../../shared/spinner/spinner';
import { crearOrdenable, EstadoOrden } from '../../shared/ordenable';
import { crearEstadoEdicion } from '../../shared/edicion.util';
import { ColumnaOrdenable } from '../../shared/columna-ordenable/columna-ordenable';

type CampoOrdenUsuario = 'username' | 'nombre' | 'rol' | 'estado';

@Component({
  selector: 'app-configuracion-usuarios',
  imports: [FormsModule, Spinner, ColumnaOrdenable],
  templateUrl: './usuarios.html',
  styleUrls: ['../configuracion-shared.css', './usuarios.css'],
})
export class ConfiguracionUsuarios implements OnInit {
  private usuarioService = inject(UsuarioService);
  private sesion = inject(SesionService);

  usuarios = signal<Usuario[]>([]);
  cargando = signal(false);
  error = signal<string | null>(null);

  mostrarInactivos = signal(false);
  private orden = crearOrdenable<CampoOrdenUsuario>('username');
  ordenarColumna = this.orden.ordenarColumna;
  estadoOrden = this.orden.estadoOrden;

  usuariosVisibles = computed(() => {
    const base = this.mostrarInactivos() ? this.usuarios() : this.usuarios().filter((u) => u.activo);
    const dir = this.orden.direccionOrden() === 'ASC' ? 1 : -1;
    const campo = this.orden.ordenarPor();
    return [...base].sort((a, b) => {
      switch (campo) {
        case 'username':
          return compararTexto(a.username, b.username) * dir;
        case 'nombre':
          return compararTexto(`${a.nombre} ${a.apellido}`, `${b.nombre} ${b.apellido}`) * dir;
        case 'rol':
          return compararTexto(a.rol, b.rol) * dir;
        case 'estado':
          return compararBooleano(a.activo, b.activo) * dir;
      }
    });
  });

  private edicion = crearEstadoEdicion<number>();
  editandoId = this.edicion.editandoId;
  usernameForm = signal('');
  passwordForm = signal('');
  nombreForm = signal('');
  apellidoForm = signal('');
  rolForm = signal<Rol>('BOLETERO');
  activoForm = signal(true);
  guardando = signal(false);

  /** El propio usuario logueado no se puede desactivar ni eliminar a sí mismo: se quedaría afuera de Configuración. */
  esUsuarioActual(u: Usuario): boolean {
    return u.id === this.sesion.usuario()?.id;
  }

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.cargando.set(true);
    this.usuarioService.getUsuarios().subscribe({
      next: (us) => {
        this.usuarios.set(us);
        this.cargando.set(false);
      },
      error: (err) => {
        console.error('Error al cargar los usuarios:', err);
        this.error.set('No se pudieron cargar los usuarios.');
        this.cargando.set(false);
      },
    });
  }

  editar(u: Usuario): void {
    this.edicion.editar(u.id);
    this.usernameForm.set(u.username);
    this.passwordForm.set('');
    this.nombreForm.set(u.nombre);
    this.apellidoForm.set(u.apellido);
    this.rolForm.set(u.rol);
    this.activoForm.set(u.activo);
    this.error.set(null);
  }

  cancelarEdicion(): void {
    this.edicion.cancelarEdicion();
    this.usernameForm.set('');
    this.passwordForm.set('');
    this.nombreForm.set('');
    this.apellidoForm.set('');
    this.rolForm.set('BOLETERO');
    this.activoForm.set(true);
    this.error.set(null);
  }

  guardar(): void {
    if (this.guardando()) return;
    const idEditando = this.editandoId();

    if (!this.usernameForm().trim() || !this.nombreForm().trim() || !this.apellidoForm().trim()) {
      this.error.set('Completá usuario, nombre y apellido.');
      return;
    }
    if (!idEditando && !this.passwordForm().trim()) {
      this.error.set('Ingresá una contraseña para el usuario nuevo.');
      return;
    }

    this.guardando.set(true);
    this.error.set(null);

    const payload = {
      username: this.usernameForm().trim(),
      password: this.passwordForm().trim() || undefined,
      nombre: this.nombreForm().trim(),
      apellido: this.apellidoForm().trim(),
      rol: this.rolForm(),
      activo: this.activoForm(),
    };

    const request = idEditando ? this.usuarioService.actualizar(idEditando, payload) : this.usuarioService.crear(payload);

    request.subscribe({
      next: (guardado) => {
        this.usuarios.update((us) => {
          const existe = us.some((u) => u.id === guardado.id);
          return existe ? us.map((u) => (u.id === guardado.id ? guardado : u)) : [guardado, ...us];
        });
        this.cancelarEdicion();
        this.guardando.set(false);
      },
      error: (err) => {
        console.error('Error al guardar el usuario:', err);
        this.error.set(typeof err?.error === 'string' ? err.error : 'No se pudo guardar el usuario.');
        this.guardando.set(false);
      },
    });
  }

  toggleActivo(u: Usuario): void {
    if (this.esUsuarioActual(u)) return;
    this.usuarioService.actualizar(u.id, { ...u, password: undefined, activo: !u.activo }).subscribe({
      next: (actualizado) => {
        this.usuarios.update((us) => us.map((x) => (x.id === u.id ? actualizado : x)));
      },
      error: (err) => {
        console.error('Error al cambiar el estado del usuario:', err);
        this.error.set('No se pudo cambiar el estado del usuario.');
      },
    });
  }
}
