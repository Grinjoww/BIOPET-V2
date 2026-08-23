import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { AuthService, UsuarioResponse } from './auth.service';

const USUARIO: UsuarioResponse = {
  id: 1,
  nombre: 'Ana QA',
  email: 'ana.qa@biopet.com',
  rol: 'ROLE_DUENO',
  activo: true,
};

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('login llama a POST /api/auth/login con el body real y, al terminar, recarga el perfil vía GET /api/usuarios/me', () => {
    service.login('ana.qa@biopet.com', 'ClaveSegura123*').subscribe();

    const loginReq = httpMock.expectOne('/api/auth/login');
    expect(loginReq.request.method).toBe('POST');
    expect(loginReq.request.body).toEqual({ email: 'ana.qa@biopet.com', password: 'ClaveSegura123*' });
    loginReq.flush({ expiresIn: 3600 });

    const perfilReq = httpMock.expectOne('/api/usuarios/me');
    expect(perfilReq.request.method).toBe('GET');
    perfilReq.flush(USUARIO);

    expect(service.usuarioActual()).toEqual(USUARIO);
  });

  it('logout llama a POST /api/auth/logout y limpia usuarioActual incluso si el backend responde con error', () => {
    service.usuarioActual.set(USUARIO);

    service.logout().subscribe();

    const req = httpMock.expectOne('/api/auth/logout');
    expect(req.request.method).toBe('POST');
    req.flush('fallo simulado', { status: 500, statusText: 'Internal Server Error' });

    expect(service.usuarioActual()).toBeNull();
  });

  it('cargarPerfil actualiza usuarioActual en éxito y lo limpia a null si el backend responde con error', () => {
    service.cargarPerfil().subscribe();
    httpMock.expectOne('/api/usuarios/me').flush(USUARIO);
    expect(service.usuarioActual()).toEqual(USUARIO);

    service.cargarPerfil().subscribe();
    httpMock.expectOne('/api/usuarios/me').flush('no autenticado', { status: 401, statusText: 'Unauthorized' });
    expect(service.usuarioActual()).toBeNull();
  });

  it('sesionActual no repite la petición HTTP si ya hay un usuario resuelto en memoria', () => {
    service.cargarPerfil().subscribe();
    httpMock.expectOne('/api/usuarios/me').flush(USUARIO);

    let resultado: UsuarioResponse | null = null;
    service.sesionActual().subscribe((u) => (resultado = u));

    expect(resultado!).toEqual(USUARIO);
    // httpMock.verify() en afterEach fallaría si sesionActual() hubiese
    // disparado una petición nueva sin descargar (flush): no hacerlo aquí
    // es la prueba de que no se disparó ninguna.
  });

  it('dos llamadas concurrentes a sesionActual() comparten la misma petición en curso (no duplican /api/usuarios/me)', () => {
    let primero: UsuarioResponse | null = null;
    let segundo: UsuarioResponse | null = null;

    service.sesionActual().subscribe((u) => (primero = u));
    service.sesionActual().subscribe((u) => (segundo = u));

    // Si hubiera 2 peticiones en curso, expectOne lanzaría por ambigüedad.
    httpMock.expectOne('/api/usuarios/me').flush(USUARIO);

    expect(primero!).toEqual(USUARIO);
    expect(segundo!).toEqual(USUARIO);
  });

  it('si el POST de login tiene éxito pero GET /api/usuarios/me falla, usuarioActual queda en null (la identidad nunca queda a medio resolver)', () => {
    let resultado: unknown;
    let errorRecibido: unknown;

    service.login('ana.qa@biopet.com', 'ClaveSegura123*').subscribe({
      next: (r) => (resultado = r),
      error: (e) => (errorRecibido = e),
    });

    httpMock.expectOne('/api/auth/login').flush({ expiresIn: 3600 });
    httpMock.expectOne('/api/usuarios/me').flush('fallo de perfil', {
      status: 500,
      statusText: 'Internal Server Error',
    });

    // cargarPerfil() atrapa su propio error (catchError -> of(null)), así
    // que login() -encadenado con switchMap- SÍ completa con éxito (no
    // propaga el fallo del perfil como error de login()); lo que importa
    // para el aterrizaje por rol es que usuarioActual queda en null, no
    // que login() "falle". LoginComponent depende de este null (no de un
    // error) para decidir su fallback -ver login.component.spec.ts-.
    expect(errorRecibido).toBeUndefined();
    expect(resultado).toEqual({ expiresIn: 3600 });
    expect(service.usuarioActual()).toBeNull();
  });

  it('nunca escribe la sesión en localStorage ni sessionStorage (el JWT vive solo en la cookie HttpOnly)', () => {
    spyOn(localStorage, 'setItem');
    spyOn(sessionStorage, 'setItem');

    service.login('ana.qa@biopet.com', 'ClaveSegura123*').subscribe();
    httpMock.expectOne('/api/auth/login').flush({ expiresIn: 3600 });
    httpMock.expectOne('/api/usuarios/me').flush(USUARIO);

    expect(localStorage.setItem).not.toHaveBeenCalled();
    expect(sessionStorage.setItem).not.toHaveBeenCalled();
  });
});
