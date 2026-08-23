import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';

import { authGuard } from './auth.guard';
import { AuthService, UsuarioResponse } from './auth.service';

describe('authGuard', () => {
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

  function ejecutarGuard() {
    return TestBed.runInInjectionContext(() => authGuard({} as any, {} as any));
  }

  it('con sesión válida permite el acceso sin redirigir', (done) => {
    const usuario: UsuarioResponse = { id: 1, nombre: 'Test', email: 't@biopet.com', rol: 'ROLE_DUENO', activo: true };
    authSpy.sesionActual.and.returnValue(of(usuario));

    (ejecutarGuard() as any).subscribe((permitido: boolean) => {
      expect(permitido).toBeTrue();
      expect(routerSpy.navigate).not.toHaveBeenCalled();
      done();
    });
  });

  it('sin sesión redirige a /login y deniega el acceso', (done) => {
    authSpy.sesionActual.and.returnValue(of(null));

    (ejecutarGuard() as any).subscribe((permitido: boolean) => {
      expect(permitido).toBeFalse();
      expect(routerSpy.navigate).toHaveBeenCalledWith(['/login']);
      done();
    });
  });
});
