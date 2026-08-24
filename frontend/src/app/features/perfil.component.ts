import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../core/auth.service';
import { humanizarRol, inicialesDe } from '../core/roles';
import { PageHeaderComponent } from '../shared/page-header/page-header.component';
import { IconComponent } from '../shared/icons/icon.component';

/**
 * IMPORTANTE — auditoría real de esta fase (no asumida):
 *
 *  - No existe NINGÚN endpoint de autoservicio para que un usuario
 *    edite su propio nombre/email/password. `AuthController` solo tiene
 *    registro/login/refresh/logout. `PUT /api/usuarios/{id}` existe,
 *    pero exige `@PreAuthorize("hasRole('ADMIN')")` — un VETERINARIO/
 *    AUXILIAR/DUENO recibiría 403 si esta pantalla intentara usarlo
 *    contra su propio id. Por eso Perfil V2 es de solo lectura para
 *    los 4 roles: mostrar un formulario "Guardar cambios" que el
 *    backend rechazaría sería un botón sin función real, algo que
 *    este proyecto evita a propósito.
 *  - GET /api/usuarios/me (sin restricción de rol, cualquier
 *    autenticado) ya alimenta `AuthService.usuarioActual`: es la única
 *    fuente de datos real de esta pantalla, la misma que ya usa el
 *    shell para el nombre/rol del sidebar.
 *  - No existe cambio de contraseña propio en ningún endpoint. Esta
 *    pantalla nunca muestra un formulario de contraseña que no
 *    funciona; solo un texto informativo honesto.
 *  - Logout reutiliza EXACTAMENTE `AuthService.logout()` — el mismo
 *    método que ya usa `AppShellComponent`, sin duplicar la llamada
 *    HTTP ni la limpieza de estado.
 */
@Component({
  standalone: true,
  imports: [CommonModule, RouterLink, PageHeaderComponent, IconComponent],
  template: `
  <app-page-header
    eyebrow="Cuenta"
    title="Perfil"
    description="Información de tu cuenta y acceso a BIOPET."
    [hasActions]="false">
  </app-page-header>

  <div class="profile-content" *ngIf="usuario() as u">
    <section class="panel profile-panel" aria-labelledby="titulo-identidad">
      <h2 id="titulo-identidad" class="sr-only">Identidad</h2>
      <div class="profile-identity">
        <span class="profile-identity__avatar" aria-hidden="true">{{ inicialesDe(u.nombre) }}</span>
        <div>
          <p class="profile-identity__nombre">{{ u.nombre }}</p>
          <p class="profile-identity__email">{{ u.email }}</p>
          <span class="chip chip--neutral profile-identity__rol">{{ humanizarRol(u.rol) }}</span>
        </div>
      </div>

      <h3 class="label form-section-label">Información de cuenta</h3>
      <dl class="profile-info">
        <div class="profile-info__row">
          <dt class="label">Correo</dt>
          <dd>{{ u.email }}</dd>
        </div>
        <div class="profile-info__row">
          <dt class="label">Rol</dt>
          <dd>{{ humanizarRol(u.rol) }}</dd>
        </div>
      </dl>
    </section>

    <section class="panel profile-panel" aria-labelledby="titulo-seguridad">
      <h2 id="titulo-seguridad" class="panel__title-inline">Seguridad</h2>
      <p class="field-hint profile-panel__texto">
        Tu contraseña está protegida. Por ahora, solo un administrador puede actualizarla desde la gestión de
        usuarios.
      </p>
      <a routerLink="/usuarios" class="btn btn--ghost btn--sm" *ngIf="esAdmin()">Ir a Usuarios</a>
    </section>

    <section class="panel profile-panel" aria-labelledby="titulo-sesion">
      <h2 id="titulo-sesion" class="panel__title-inline">Sesión</h2>
      <p class="field-hint profile-panel__texto">Tu sesión actual está activa.</p>
      <button type="button" class="btn btn--secondary" (click)="cerrarSesion()" [disabled]="cerrandoSesion()">
        <app-icon name="logout"></app-icon>
        {{ cerrandoSesion() ? 'Cerrando sesión…' : 'Cerrar sesión' }}
      </button>
    </section>
  </div>

  <p class="alert alert--danger" role="alert" *ngIf="!usuario()">
    <strong>Error:</strong> No se pudo cargar tu información de cuenta. Intenta recargar la página.
  </p>
  `,
})
export class PerfilComponent {
  readonly humanizarRol = humanizarRol;
  readonly inicialesDe = inicialesDe;

  cerrandoSesion = signal(false);

  constructor(private auth: AuthService, private router: Router) {}

  usuario() {
    return this.auth.usuarioActual();
  }

  esAdmin(): boolean {
    return this.auth.usuarioActual()?.rol === 'ROLE_ADMIN';
  }

  cerrarSesion(): void {
    this.cerrandoSesion.set(true);
    this.auth.logout().subscribe(() => this.router.navigate(['/login']));
  }
}
