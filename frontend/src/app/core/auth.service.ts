import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, finalize, map, of, shareReplay, switchMap, tap } from 'rxjs';
import { RolBiopet } from './roles';

/** Refleja AuthSessionResponse del backend: YA NO trae accessToken/refreshToken. */
export interface AuthSessionResponse {
  expiresIn: number;
}

/** Refleja UsuarioResponse del backend (GET /api/usuarios/me). */
export interface UsuarioResponse {
  id: number;
  nombre: string;
  email: string;
  rol: RolBiopet;
  activo: boolean;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = '/api';

  /**
   * Estado de sesión en memoria SOLO para la UI (mostrar/ocultar menús,
   * nombre de usuario, etc.). Nunca contiene el JWT: el token vive
   * exclusivamente en la cookie HttpOnly que Angular no puede leer.
   * La fuente de verdad real de la sesión es siempre el backend
   * (GET /api/usuarios/me), no este signal.
   */
  readonly usuarioActual = signal<UsuarioResponse | null>(null);

  /**
   * Petición /me en curso, compartida entre llamadas concurrentes a
   * sesionActual() (p. ej. el guard resolviendo varias rutas hijas casi
   * al mismo tiempo). Evita disparar la misma petición dos veces.
   */
  private sesionEnCurso$: Observable<UsuarioResponse | null> | null = null;

  constructor(private http: HttpClient) {}

  /**
   * Login: el backend setea las cookies access/refresh en la respuesta.
   * El body de respuesta solo trae expiresIn; no hay nada que guardar
   * en el cliente aparte de refrescar el estado de usuarioActual.
   *
   * IMPORTANTE: se ENCADENA cargarPerfil() con switchMap (no un
   * `tap(() => this.cargarPerfil().subscribe())` disparado en paralelo,
   * como antes de esta fase). Con tap+subscribe suelto, el GET
   * /api/usuarios/me queda en curso pero el observable devuelto por
   * login() ya emitía "completado" de inmediato -antes de que
   * usuarioActual() se hubiera actualizado-, así que cualquier código que
   * decidiera algo según el rol justo al recibir el login (como el
   * aterrizaje por rol de LoginComponent) podía leer un usuarioActual()
   * todavía null/desactualizado. Con switchMap, login() solo emite
   * DESPUÉS de que cargarPerfil() haya resuelto (éxito o error), así que
   * usuarioActual() ya es fiable en el propio callback de éxito de quien
   * llama a login().
   */
  login(email: string, password: string): Observable<AuthSessionResponse> {
    return this.http
      .post<AuthSessionResponse>(`${this.api}/auth/login`, { email, password })
      .pipe(switchMap((respuesta) => this.cargarPerfil().pipe(map(() => respuesta))));
  }

  /**
   * Refresh: usa la cookie de refresh token (HttpOnly) que el navegador
   * envía automáticamente porque withCredentials está activo en el
   * interceptor. No requiere ningún dato del cliente.
   */
  refresh(): Observable<AuthSessionResponse> {
    return this.http.post<AuthSessionResponse>(`${this.api}/auth/refresh`, {});
  }

  /**
   * Logout: el backend revoca el token en Redis (blacklist) y limpia las
   * cookies. Del lado cliente solo limpiamos el estado de UI.
   */
  logout(): Observable<void> {
    return this.http.post<void>(`${this.api}/auth/logout`, {}).pipe(
      tap(() => this.usuarioActual.set(null)),
      catchError(() => {
        // Aunque el logout falle en el servidor, limpiamos el estado local
        // de UI para no dejar al usuario en un estado inconsistente.
        this.usuarioActual.set(null);
        return of(void 0);
      })
    );
  }

  /**
   * Pregunta SIEMPRE al backend si la cookie de sesión actual es válida
   * (GET /api/usuarios/me) y actualiza usuarioActual con el resultado.
   * Úsese cuando se necesita el estado más reciente con certeza (login,
   * o una revalidación explícita). Para navegación normal dentro del
   * shell, usar sesionActual() en su lugar.
   */
  cargarPerfil(): Observable<UsuarioResponse | null> {
    return this.http.get<UsuarioResponse>(`${this.api}/usuarios/me`).pipe(
      tap((usuario) => this.usuarioActual.set(usuario)),
      catchError(() => {
        this.usuarioActual.set(null);
        return of(null);
      })
    );
  }

  /**
   * Resuelve la sesión reutilizando el estado en memoria cuando ya existe,
   * en vez de repetir GET /api/usuarios/me en cada navegación protegida
   * (que es lo que hacía authGuard antes de esta fase). Es exclusivamente
   * una optimización de UI: el backend sigue validando la cookie en cada
   * petición HTTP real de todas formas, así que esta caché nunca amplía lo
   * que el usuario puede hacer — solo evita un viaje de red redundante
   * para decidir qué pinta el sidebar.
   *
   * Tras un logout, usuarioActual queda en null y la siguiente llamada
   * vuelve a preguntar al backend con normalidad.
   */
  sesionActual(): Observable<UsuarioResponse | null> {
    const actual = this.usuarioActual();
    if (actual) return of(actual);

    if (!this.sesionEnCurso$) {
      this.sesionEnCurso$ = this.cargarPerfil().pipe(
        finalize(() => (this.sesionEnCurso$ = null)),
        shareReplay(1)
      );
    }
    return this.sesionEnCurso$;
  }

  /** Uso solo informativo/UI; NUNCA usar esto para decidir si hacer una
   *  llamada protegida — el backend siempre valida la cookie real. */
  isLogged(): boolean {
    return this.usuarioActual() !== null;
  }
}
