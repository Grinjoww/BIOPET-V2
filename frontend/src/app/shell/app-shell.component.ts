import { CommonModule } from '@angular/common';
import { Component, computed, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { humanizarRol, inicialesDe, navGroupsParaRol } from '../core/roles';
import { FocusTrapDirective } from '../shared/focus-trap/focus-trap.directive';
import { IconComponent } from '../shared/icons/icon.component';
import { MonogramComponent } from '../shared/icons/monogram.component';

const SIDEBAR_COLLAPSED_KEY = 'biopet.sidebarCollapsed';

/**
 * Application shell autenticado: sidebar por rol (desktop/laptop),
 * drawer accesible (tablet/mobile) y pie con la sesión real.
 *
 * `sidebarCollapsed` es la ÚNICA preferencia que este componente guarda
 * en localStorage, y es exclusivamente visual (ancho del sidebar). Nunca
 * se guarda aquí JWT, access_token, refresh_token ni ningún dato de
 * sesión: la sesión real sigue viviendo solo en la cookie HttpOnly y en
 * AuthService.usuarioActual (en memoria).
 */
@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [
    CommonModule,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    IconComponent,
    MonogramComponent,
    FocusTrapDirective,
  ],
  template: `
    <div class="shell">
      <!-- ===== Sidebar (>=1024px) ===== -->
      <aside
        class="shell__sidebar"
        [class.shell__sidebar--collapsed]="sidebarCollapsed()"
      >
        <div class="shell__brand">
          <a class="shell__brand-mark" routerLink="/mascotas" aria-label="Ir al inicio de BIOPET">
            <app-monogram [size]="28"></app-monogram>
            <span class="shell__wordmark" *ngIf="!sidebarCollapsed()">BIOPET</span>
          </a>
          <button
            type="button"
            class="btn-icon shell__collapse-btn"
            (click)="alternarColapso()"
            [attr.aria-label]="sidebarCollapsed() ? 'Expandir menú' : 'Contraer menú'"
            [attr.aria-expanded]="!sidebarCollapsed()"
          >
            <app-icon [name]="sidebarCollapsed() ? 'chevron-right' : 'chevron-left'"></app-icon>
          </button>
        </div>

        <nav class="shell__nav" aria-label="Navegación principal">
          <div class="shell__nav-group" *ngFor="let grupo of navGroups()">
            <span class="eyebrow shell__nav-group-label" *ngIf="!sidebarCollapsed()">{{ grupo.label }}</span>
            <ul>
              <li *ngFor="let item of grupo.items">
                <a
                  class="shell__nav-item"
                  [routerLink]="item.path"
                  routerLinkActive="is-active"
                  #rla="routerLinkActive"
                  [attr.aria-current]="rla.isActive ? 'page' : null"
                  [attr.aria-label]="sidebarCollapsed() ? item.label : null"
                >
                  <app-icon [name]="item.icon" [size]="21"></app-icon>
                  <span>{{ item.label }}</span>
                </a>
              </li>
            </ul>
          </div>
        </nav>

        <div class="shell__footer">
          <div class="shell__user">
            <span class="shell__avatar" aria-hidden="true">{{ iniciales() }}</span>
            <div class="shell__user-info">
              <div class="shell__user-name">{{ usuario()?.nombre }}</div>
              <div class="shell__user-role">{{ rolHumanizado() }}</div>
            </div>
          </div>
          <button type="button" class="btn btn--sm btn--ghost shell__logout-btn" (click)="cerrarSesion()">
            <app-icon name="logout"></app-icon>
            <span>Cerrar sesión</span>
          </button>
        </div>
      </aside>

      <!-- ===== Columna principal ===== -->
      <div class="shell__main">
        <!-- ===== Topbar móvil (<1024px) ===== -->
        <div class="shell__mobile-topbar">
          <button
            type="button"
            class="btn-icon"
            style="color:#fff"
            (click)="abrirMenu()"
            aria-label="Abrir menú de navegación"
            aria-haspopup="true"
            [attr.aria-expanded]="menuAbierto()"
          >
            <app-icon name="menu"></app-icon>
          </button>
          <app-monogram [size]="22"></app-monogram>
          <span class="shell__wordmark">BIOPET</span>
        </div>

        <a class="skip-link" href="#contenido-principal">Saltar al contenido</a>

        <main id="contenido-principal" class="shell__content">
          <router-outlet></router-outlet>
        </main>
      </div>
    </div>

    <!-- ===== Drawer móvil ===== -->
    <ng-container *ngIf="menuAbierto()">
      <div class="shell__drawer-overlay" (click)="cerrarMenu()"></div>
      <nav
        class="shell__drawer"
        appFocusTrap
        (keydown.escape)="cerrarMenu()"
        role="dialog"
        aria-modal="true"
        aria-label="Navegación principal"
      >
        <div class="shell__drawer-header">
          <div class="shell__brand-mark">
            <app-monogram [size]="26"></app-monogram>
            <span class="shell__wordmark">BIOPET</span>
          </div>
          <button
            type="button"
            class="btn-icon shell__drawer-close"
            (click)="cerrarMenu()"
            aria-label="Cerrar menú"
          >
            <app-icon name="close"></app-icon>
          </button>
        </div>

        <nav class="shell__nav" aria-label="Secciones">
          <div class="shell__nav-group" *ngFor="let grupo of navGroups()">
            <span class="eyebrow shell__nav-group-label">{{ grupo.label }}</span>
            <ul>
              <li *ngFor="let item of grupo.items">
                <a
                  class="shell__nav-item"
                  [routerLink]="item.path"
                  routerLinkActive="is-active"
                  (click)="cerrarMenu()"
                >
                  <app-icon [name]="item.icon" [size]="21"></app-icon>
                  <span>{{ item.label }}</span>
                </a>
              </li>
            </ul>
          </div>
        </nav>

        <div class="shell__footer">
          <div class="shell__user">
            <span class="shell__avatar" aria-hidden="true">{{ iniciales() }}</span>
            <div class="shell__user-info">
              <div class="shell__user-name">{{ usuario()?.nombre }}</div>
              <div class="shell__user-role">{{ rolHumanizado() }}</div>
            </div>
          </div>
          <button type="button" class="btn btn--sm btn--ghost shell__logout-btn" (click)="cerrarSesion()">
            <app-icon name="logout"></app-icon>
            <span>Cerrar sesión</span>
          </button>
        </div>
      </nav>
    </ng-container>
  `,
})
export class AppShellComponent {
  private readonly usuarioSignal = this.auth.usuarioActual;

  readonly usuario = computed(() => this.usuarioSignal());
  readonly navGroups = computed(() => {
    const rol = this.usuarioSignal()?.rol;
    return rol ? navGroupsParaRol(rol) : [];
  });
  readonly rolHumanizado = computed(() => humanizarRol(this.usuarioSignal()?.rol));
  readonly iniciales = computed(() => inicialesDe(this.usuarioSignal()?.nombre));

  readonly sidebarCollapsed = signal(this.leerPreferenciaColapso());
  readonly menuAbierto = signal(false);

  constructor(private readonly auth: AuthService, private readonly router: Router) {}

  alternarColapso(): void {
    const nuevoValor = !this.sidebarCollapsed();
    this.sidebarCollapsed.set(nuevoValor);
    this.guardarPreferenciaColapso(nuevoValor);
  }

  abrirMenu(): void {
    this.menuAbierto.set(true);
  }

  cerrarMenu(): void {
    this.menuAbierto.set(false);
  }

  cerrarSesion(): void {
    this.auth.logout().subscribe(() => this.router.navigate(['/login']));
  }

  /**
   * localStorage puede lanzar (modo privado, site data bloqueada, etc.);
   * nunca debe romper el render del shell por una preferencia visual.
   * Este es el único uso de localStorage en todo el shell, y guarda
   * exclusivamente un booleano de UI — nunca sesión ni tokens.
   */
  private leerPreferenciaColapso(): boolean {
    try {
      return localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === 'true';
    } catch {
      return false;
    }
  }

  private guardarPreferenciaColapso(valor: boolean): void {
    try {
      localStorage.setItem(SIDEBAR_COLLAPSED_KEY, String(valor));
    } catch {
      // Preferencia visual no persistida; no afecta la funcionalidad.
    }
  }
}
