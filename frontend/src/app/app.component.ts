import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * Raíz de la aplicación: solo enruta. El chrome visual (marca,
 * navegación, sesión) vive en AppShellComponent, montado por las rutas
 * autenticadas en app.routes.ts — /login queda fuera de ese shell.
 */
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  template: `<router-outlet />`,
})
export class AppComponent {}
