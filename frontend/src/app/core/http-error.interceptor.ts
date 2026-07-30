import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * Forma exacta de un error ProblemDetails (RFC 7807) que devuelve el backend.
 * Se expone tal cual en HttpErrorResponse.error para que los componentes
 * puedan leer err.error?.detail directamente, sin parseo adicional.
 */
export interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance: string;
}

const RUTAS_SIN_REINTENTO = ['/api/auth/login', '/api/auth/refresh', '/api/auth/logout'];

/**
 * Responsabilidades de este interceptor (reemplaza a jwt.interceptor.ts):
 * 1. Enviar withCredentials: true en toda petición a la API para que el
 *    navegador incluya las cookies HttpOnly de acceso/refresh.
 * 2. Si una petición protegida responde 401 (access token expirado) e no es
 *    ella misma /auth/login, /auth/refresh o /auth/logout, intentar UN
 *    refresh automático y reintentar la petición original una sola vez.
 * 3. Si el refresh también falla, limpiar el estado de sesión y redirigir
 *    a /login (evita loops de reintento infinito).
 * 4. Para 403, 409, 422, 429, etc., no reintenta nada: deja pasar el
 *    ProblemDetail intacto para que el componente muestre err.error.detail.
 */
export const httpErrorInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  const reqConCredenciales = req.clone({ withCredentials: true });

  return next(reqConCredenciales).pipe(
    catchError((error: HttpErrorResponse) => {
      const esRutaSinReintento = RUTAS_SIN_REINTENTO.some((ruta) => req.url.includes(ruta));

      if (error.status === 401 && !esRutaSinReintento) {
        return auth.refresh().pipe(
          switchMap(() => next(reqConCredenciales)),
          catchError((refreshError) => {
            auth.logout().subscribe();
            router.navigate(['/login']);
            return throwError(() => refreshError);
          })
        );
      }

      if (error.status === 401 && esRutaSinReintento) {
        // El propio login/refresh/logout devolvió 401: no hay nada que
        // refrescar (ej. credenciales inválidas o refresh token vencido).
        router.navigate(['/login']);
      }

      // 403, 409, 422, 429 y cualquier otro: se propaga el ProblemDetail
      // original para que el componente lo muestre (err.error?.detail).
      return throwError(() => error);
    })
  );
};
