import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';

import { AuthService, UsuarioResponse } from './auth.service';
import { homeRedirectGuard } from './home-redirect.guard';

function usuario(rol: UsuarioResponse['rol']): UsuarioResponse {
  return { id: 1, nombre: 'Test', email: 't@biopet.com', rol, activo: true };
}

describe('homeRedirectGuard (reemplaza redirectTo estático en "" y "**")', () => {
  let authSpy: jasmine.SpyObj<AuthService>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(() => {
    authSpy = jasmine.createSpyObj<AuthService>('AuthService', ['sesionActual']);
    routerSpy = jasmine.createSpyObj<Router>('Router', ['navigateByUrl']);

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authSpy },
        { provide: Router, useValue: routerSpy },
      ],
    });
  });

  function ejecutarGuard() {
    return TestBed.runInInjectionContext(() => homeRedirectGuard({} as any, {} as any));
  }

  it('ADMIN es enviado a /panel, no a /mascotas', (done) => {
    authSpy.sesionActual.and.returnValue(of(usuario('ROLE_ADMIN')));

    (ejecutarGuard() as any).subscribe((activar: boolean) => {
      expect(activar).toBeFalse(); // cancela la activación: la navegación real ya ocurrió
      expect(routerSpy.navigateByUrl).toHaveBeenCalledWith('/panel');
      done();
    });
  });

  it('DUENO sigue siendo enviado a /mascotas', (done) => {
    authSpy.sesionActual.and.returnValue(of(usuario('ROLE_DUENO')));

    (ejecutarGuard() as any).subscribe(() => {
      expect(routerSpy.navigateByUrl).toHaveBeenCalledWith('/mascotas');
      done();
    });
  });

  it('sin sesión, envía a /login en vez de intentar decidir un home por rol', (done) => {
    authSpy.sesionActual.and.returnValue(of(null));

    (ejecutarGuard() as any).subscribe(() => {
      expect(routerSpy.navigateByUrl).toHaveBeenCalledWith('/login');
      done();
    });
  });
});
