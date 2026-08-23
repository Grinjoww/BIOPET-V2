import { Routes } from '@angular/router';
import { LoginComponent } from './features/login.component';
import { authGuard } from './core/auth.guard';
import { homeRedirectGuard } from './core/home-redirect.guard';
import { roleGuard } from './core/role.guard';
import { AppShellComponent } from './shell/app-shell.component';

/**
 * Auditoría de bundle (fase "Panel V2 + control del bundle"): hasta esta
 * fase, TODAS las features (Mascotas, ficha de mascota, Vacunas, Citas,
 * Consultas) se importaban aquí con `component: XComponent` — un import
 * estático de nivel de módulo. Como este archivo lo importa
 * `provideRouter` en main.ts (que sí es parte del bootstrap), cada
 * componente importado aquí terminaba en el chunk `main` inicial sin
 * importar que el usuario nunca visitara esa ruta. El bundle inicial
 * crecía con cada fase nueva porque literalmente cargábamos las 5
 * pantallas completas (formularios reactivos, tablas, servicios HTTP)
 * en cada carga de la app, incluso solo para mostrar el login.
 *
 * Solución: `loadComponent` con `import()` dinámico para toda feature
 * "grande" (todo lo que cuelga del shell autenticado, salvo el propio
 * shell). Angular/esbuild generan un chunk separado por componente,
 * descargado solo al navegar a esa ruta. `LoginComponent` y
 * `AppShellComponent` se mantienen eager a propósito:
 * - Login es la pantalla de entrada real para casi cualquier visita sin
 *   sesión (staying eager evita un salto de red extra en el camino más
 *   común).
 * - AppShellComponent es el chrome (sidebar/nav/sesión) de TODA ruta
 *   autenticada: se necesita casi inmediatamente después de cualquier
 *   login exitoso, así que lazy-cargarlo no ahorraría bundle percibido,
 *   solo añadiría una descarga extra en el camino crítico.
 *
 * Con Perfil V2 (última fase, ver informe), las 8 rutas del shell ya
 * tienen pantalla real — `ComingSoonComponent` (page-header + dos
 * párrafos, usado hasta ahora por Panel/Usuarios/Perfil mientras no
 * tenían contenido real) ya no se referencia desde ningún lado y se
 * quitó de este archivo. El componente en sí (shell/coming-soon.component.ts)
 * se deja sin borrar por si una fase futura necesita un placeholder
 * honesto de nuevo, pero ahora mismo no forma parte del árbol de rutas.
 *
 * `withComponentInputBinding()` (main.ts) y los guards existentes
 * (`authGuard`, `roleGuard`) funcionan igual con `loadComponent`: operan
 * sobre la ruta activada después de resuelto el componente, no sobre
 * cómo se importó.
 */
export const routes: Routes = [
  { path: 'login', component: LoginComponent },

  {
    path: '',
    component: AppShellComponent,
    canActivate: [authGuard],
    children: [
      // Antes: `redirectTo: 'mascotas'` fijo para los 4 roles. Ahora que
      // Panel V2 tiene contenido real, ADMIN/VETERINARIO/AUXILIAR deben
      // aterrizar ahí en vez de en Mascotas — pero `redirectTo` en esta
      // versión de Angular Router solo admite un string fijo, no una
      // función que conozca el rol. `homeRedirectGuard` es el reemplazo:
      // navega imperativamente a rutaInicioParaRol(rol) (core/roles.ts,
      // misma fuente que usa LoginComponent) y cancela esta activación.
      // ROLE_DUENO sigue en 'mascotas' (sin acceso real a Panel — ver
      // roles.ts), así que para ese rol el resultado visible es idéntico
      // al de antes.
      { path: '', pathMatch: 'full', canActivate: [homeRedirectGuard] },

      // Panel V2 consume GET /api/dashboard/resumen, restringido en el
      // backend a ADMIN/VETERINARIO/AUXILIAR (fn_reporte_dashboard no
      // admite filtrar por dueño — ver informe de esta fase). roleGuard
      // es solo wayfinding: si ROLE_DUENO navega aquí a mano, lo manda a
      // /mascotas en vez de mostrar una pantalla que el backend
      // rechazaría con 403. El sidebar (core/roles.ts) ya no le muestra
      // este enlace en absoluto.
      {
        path: 'panel',
        loadComponent: () => import('./features/panel.component').then((m) => m.PanelComponent),
        canActivate: [roleGuard(['ROLE_ADMIN', 'ROLE_VETERINARIO', 'ROLE_AUXILIAR'])],
      },
      {
        path: 'mascotas',
        loadComponent: () => import('./features/mascotas.component').then((m) => m.MascotasComponent),
      },
      {
        path: 'mascotas/:id',
        loadComponent: () =>
          import('./features/mascota-detalle.component').then((m) => m.MascotaDetalleComponent),
      },
      {
        path: 'citas',
        loadComponent: () => import('./features/citas.component').then((m) => m.CitasComponent),
      },
      {
        path: 'consultas',
        loadComponent: () => import('./features/consultas.component').then((m) => m.ConsultasComponent),
      },
      {
        path: 'vacunas',
        loadComponent: () => import('./features/vacunas.component').then((m) => m.VacunasComponent),
      },

      {
        path: 'usuarios',
        loadComponent: () => import('./features/usuarios.component').then((m) => m.UsuariosComponent),
        canActivate: [roleGuard(['ROLE_ADMIN'])],
      },
      {
        path: 'perfil',
        loadComponent: () => import('./features/perfil.component').then((m) => m.PerfilComponent),
      },
    ],
  },

  // Mismo guard que la raíz del shell (arriba): una URL inexistente para un
  // ADMIN/VETERINARIO/AUXILIAR autenticado debe rebotar a su home real
  // (Panel), no forzarlo a Mascotas. Sin sesión, homeRedirectGuard manda a
  // /login igual que antes hacía indirectamente (redirectTo: 'mascotas' →
  // authGuard del shell → /login).
  { path: '**', canActivate: [homeRedirectGuard] },
];
