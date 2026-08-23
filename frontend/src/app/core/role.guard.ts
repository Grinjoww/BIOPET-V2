import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from './auth.service';
import { RolBiopet } from './roles';

/**
 * Complementa (no sustituye) la autorización real del backend.
 * @PreAuthorize en cada controller sigue siendo quien decide qué puede
 * hacer cada rol; este guard solo evita que la UI navegue a una pantalla
 * que el backend rechazaría de todas formas — es wayfinding, no seguridad.
 *
 * Uso: `canActivate: [roleGuard(['ROLE_ADMIN'])]`. Si el usuario no está
 * autenticado, redirige a /login (igual que authGuard). Si está
 * autenticado pero su rol no está en la lista, redirige a /mascotas en
 * vez de mostrar una pantalla en blanco o un 403 sin contexto.
 */
export function roleGuard(rolesPermitidos: RolBiopet[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);

    return auth.sesionActual().pipe(
      map((usuario) => {
        if (!usuario) {
          router.navigate(['/login']);
          return false;
        }
        if (!rolesPermitidos.includes(usuario.rol)) {
          router.navigate(['/mascotas']);
          return false;
        }
        return true;
      })
    );
  };
}
