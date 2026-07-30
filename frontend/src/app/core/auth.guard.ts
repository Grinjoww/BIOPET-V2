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
 * Nota de UX: esto agrega una llamada de red antes de cada navegación
 * protegida. Es el costo correcto de mover el token fuera del alcance de
 * JS (mitigación XSS); si en el futuro se requiere evitar el round-trip,
 * se podría cachear el resultado por unos segundos en auth.service, pero
 * eso queda fuera del alcance de esta tarea.
 */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return auth.cargarPerfil().pipe(
    map((usuario) => {
      if (usuario) return true;
      router.navigate(['/login']);
      return false;
    })
  );
};