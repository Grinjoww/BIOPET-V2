import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from './auth.service';
import { rutaInicioParaRol } from './roles';

/**
 * Sustituye al redirect estático que antes tenían '' (raíz del shell) y
 * '**' (wildcard): ambos apuntaban siempre a 'mascotas' con
 * `redirectTo: 'mascotas'`, sin conocer el rol. Angular Router 17.3 tipa
 * `redirectTo` únicamente como `string` (sin soporte de función), así que
 * la única forma de decidir el destino según el rol autenticado es un
 * guard que navega imperativamente y cancela la activación de la ruta
 * (retorna `false`) — patrón estándar para este caso antes de que
 * existieran los redirects por función.
 *
 * Se usa en dos sitios (app.routes.ts: '' del shell y '**'), ambos
 * delegando en el mismo rutaInicioParaRol(...) para no duplicar el
 * criterio. No reemplaza a authGuard/roleGuard: si no hay sesión, este
 * guard igual manda a /login (mismo destino que authGuard), y si el
 * usuario navega después a una ruta restringida por rol, roleGuard sigue
 * siendo quien la protege.
 */
export const homeRedirectGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return auth.sesionActual().pipe(
    map((usuario) => {
      router.navigateByUrl(usuario ? rutaInicioParaRol(usuario.rol) : '/login');
      return false;
    })
  );
};
