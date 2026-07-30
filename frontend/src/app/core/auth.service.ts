import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, of, tap } from 'rxjs';

/** Refleja AuthSessionResponse del backend: YA NO trae accessToken/refreshToken. */
export interface AuthSessionResponse {
  expiresIn: number;
}

/** Refleja UsuarioResponse del backend (GET /api/usuarios/me). */
export interface UsuarioResponse {
  id: number;
  nombre: string;
  email: string;
  rol: string;
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

  constructor(private http: HttpClient) {}

  /**
   * Login: el backend setea las cookies access/refresh en la respuesta.
   * El body de respuesta solo trae expiresIn; no hay nada que guardar
   * en el cliente aparte de refrescar el estado de usuarioActual.
   */
  login(email: string, password: string): Observable<AuthSessionResponse> {
    return this.http
      .post<AuthSessionResponse>(`${this.api}/auth/login`, { email, password })
      .pipe(tap(() => this.cargarPerfil().subscribe()));
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
   * Pregunta al backend si la cookie de sesión actual es válida.
   * Es la base del auth.guard: como el JWT ya no es legible desde JS,
   * la única forma correcta de saber "¿hay sesión?" es preguntarle al
   * servidor, no inspeccionar nada en el cliente.
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

  /** Uso solo informativo/UI; NUNCA usar esto para decidir si hacer una
   *  llamada protegida — el backend siempre valida la cookie real. */
  isLogged(): boolean {
    return this.usuarioActual() !== null;
  }
}