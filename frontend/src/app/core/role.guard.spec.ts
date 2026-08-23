import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';

import { AuthService, UsuarioResponse } from './auth.service';
import { roleGuard } from './role.guard';

function usuario(rol: UsuarioResponse['rol']): UsuarioResponse {
  return { id: 1, nombre: 'Test', email: 't@biopet.com', rol, activo: true };
}

describe('roleGuard (wayfinding — la autorización real vive en el backend)', () => {
  let authSpy: jasmine.SpyObj<AuthService>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(() => {
    authSpy = jasmine.createSpyObj<AuthService>('AuthService', ['sesionActual']);
    routerSpy = jasmine.createSpyObj<Router>('Router', ['navigate']);

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authSpy },
        { provide: Router, useValue: routerSpy },
      ],
    });
  });

  function ejecutarGuard(roles: UsuarioResponse['rol'][]) {
    return TestBed.runInInjectionContext(() => roleGuard(roles)({} as any, {} as any));
  }

  it('un rol permitido obtiene acceso (ADMIN entrando a /usuarios)', (done) => {
    authSpy.sesionActual.and.returnValue(of(usuario('ROLE_ADMIN')));

    (ejecutarGuard(['ROLE_ADMIN']) as any).subscribe((permitido: boolean) => {
      expect(permitido).toBeTrue();
      expect(routerSpy.navigate).not.toHaveBeenCalled();
      done();
    });
  });

  it('DUENO intentando /panel es redirigido a /mascotas, no se le concede acceso', (done) => {
    authSpy.sesionActual.and.returnValue(of(usuario('ROLE_DUENO')));

    (ejecutarGuard(['ROLE_ADMIN', 'ROLE_VETERINARIO', 'ROLE_AUXILIAR']) as any).subscribe((permitido: boolean) => {
      expect(permitido).toBeFalse();
      expect(routerSpy.navigate).toHaveBeenCalledWith(['/mascotas']);
      done();
    });
  });

  it('sin sesión (usuario null) redirige a /login en vez de a la ruta de "rol no permitido"', (done) => {
    authSpy.sesionActual.and.returnValue(of(null));

    (ejecutarGuard(['ROLE_ADMIN']) as any).subscribe((permitido: boolean) => {
      expect(permitido).toBeFalse();
      expect(routerSpy.navigate).toHaveBeenCalledWith(['/login']);
      done();
    });
  });

  it('un rol no permitido nunca navega a /login (esa redirección es exclusiva del caso "sin sesión")', (done) => {
    authSpy.sesionActual.and.returnValue(of(usuario('ROLE_VETERINARIO')));

    (ejecutarGuard(['ROLE_ADMIN']) as any).subscribe(() => {
      expect(routerSpy.navigate).not.toHaveBeenCalledWith(['/login']);
      done();
    });
  });
});
