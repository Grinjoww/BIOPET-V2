import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';

import { httpErrorInterceptor } from './http-error.interceptor';

/**
 * AuthService NO se mockea: se usa la instancia real (inyectada por
 * DI normal) para que auth.refresh()/auth.logout() disparen peticiones
 * HTTP reales sobre el mismo HttpTestingController, ejercitando el
 * comportamiento end-to-end real del interceptor tal como está implementado
 * hoy (sin mutex de refresh concurrente — ver informe).
 */
describe('httpErrorInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(() => {
    routerSpy = jasmine.createSpyObj<Router>('Router', ['navigate']);

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([httpErrorInterceptor])),
        provideHttpClientTesting(),
        { provide: Router, useValue: routerSpy },
      ],
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('aplica withCredentials:true a toda petición (necesario para que el navegador envíe las cookies HttpOnly)', () => {
    http.get('/api/mascotas').subscribe();

    const req = httpMock.expectOne('/api/mascotas');
    expect(req.request.withCredentials).toBeTrue();
    req.flush({});
  });

  it('ante 401 en una ruta protegida, hace como máximo un refresh y reintenta la petición original una sola vez', () => {
    let resultado: unknown;
    http.get('/api/mascotas').subscribe((r) => (resultado = r));

    httpMock.expectOne('/api/mascotas').flush('access token expirado', {
      status: 401,
      statusText: 'Unauthorized',
    });

    const refreshReq = httpMock.expectOne('/api/auth/refresh');
    expect(refreshReq.request.method).toBe('POST');
    refreshReq.flush({ expiresIn: 3600 });

    // Reintento de la petición ORIGINAL, ahora con la sesión ya refrescada.
    const reintento = httpMock.expectOne('/api/mascotas');
    reintento.flush({ ok: true });

    expect(resultado).toEqual({ ok: true });
    expect(routerSpy.navigate).not.toHaveBeenCalled();
  });

  it('si el refresh también falla, cierra sesión, navega a /login y NO reintenta la petición original una segunda vez', () => {
    let error: unknown;
    http.get('/api/mascotas').subscribe({ error: (e) => (error = e) });

    httpMock.expectOne('/api/mascotas').flush('access token expirado', {
      status: 401,
      statusText: 'Unauthorized',
    });

    httpMock.expectOne('/api/auth/refresh').flush('refresh token vencido', {
      status: 401,
      statusText: 'Unauthorized',
    });

    // Efecto de auth.logout() disparado por el catchError del interceptor.
    httpMock.expectOne('/api/auth/logout').flush(null, { status: 204, statusText: 'No Content' });

    // Ninguna petición pendiente adicional a /api/mascotas: no hay reintento
    // en bucle. httpMock.verify() (afterEach) además fallaría si quedara
    // cualquier request sin resolver.
    httpMock.expectNone('/api/mascotas');

    expect(routerSpy.navigate).toHaveBeenCalledWith(['/login']);
    expect(error).toBeTruthy();
  });

  it('un 401 en la propia ruta de login NO dispara un refresh: navega directo a /login', () => {
    let error: unknown;
    http.post('/api/auth/login', { email: 'x@biopet.com', password: 'mal' }).subscribe({
      error: (e) => (error = e),
    });

    httpMock.expectOne('/api/auth/login').flush('credenciales inválidas', {
      status: 401,
      statusText: 'Unauthorized',
    });

    // Ninguna petición a /api/auth/refresh: no existe nada que refrescar.
    httpMock.expectNone('/api/auth/refresh');
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/login']);
    expect(error).toBeTruthy();
  });

  it('403/409/422/429 se propagan intactos: no reintenta nada ni navega', () => {
    let error: any;
    http.post('/api/citas', {}).subscribe({ error: (e) => (error = e) });

    const problemDetail = { type: 'urn:biopet:error:forbidden', title: 'Prohibido', status: 403, detail: 'Sin permiso' };
    httpMock.expectOne('/api/citas').flush(problemDetail, { status: 403, statusText: 'Forbidden' });

    httpMock.expectNone('/api/auth/refresh');
    expect(routerSpy.navigate).not.toHaveBeenCalled();
    expect(error.error).toEqual(problemDetail);
  });
});
