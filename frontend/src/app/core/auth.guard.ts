import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * Ya no existe un token legible en JavaScript (vive en una cookie HttpOnly),
 * así que el guard no puede preguntar "¿existe un token en memoria?" como
 * antes. En su lugar, confirma la sesión contra el backend real
 * (GET /api/usuarios/me), que es quien valida la cookie firmada.
 *
 * Usa AuthService.sesionActual(), que reutiliza el estado ya resuelto en
 * memoria en vez de repetir la petición en cada navegación protegida (la
 * versión anterior de este guard llamaba a cargarPerfil() —sin caché—
 * en cada cambio de ruta). El backend sigue siendo la autoridad real:
 * cada petición HTTP posterior se valida de forma independiente contra la
 * cookie, esta caché solo evita un round-trip redundante para decidir si
 * se puede pintar la pantalla.
 */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return auth.sesionActual().pipe(
    map((usuario) => {
      if (usuario) return true;
      router.navigate(['/login']);
      return false;
    })
  );
};
