import { FormBuilder } from '@angular/forms';
import { Router } from '@angular/router';
import { of } from 'rxjs';

import { AuthService, UsuarioResponse } from '../core/auth.service';
import { LoginComponent } from './login.component';

/**
 * AuthService.login() real ya encadena (switchMap) la carga del perfil
 * antes de emitir -ver auth.service.ts-, así que aquí basta con simular
 * ESE contrato (usuarioActual ya seteado en el momento en que login()
 * emite) sin reimplementar la secuencia real; esa secuencia interna ya
 * está probada por separado en auth.service.spec.ts.
 */
function crearAuthConRol(rol: UsuarioResponse['rol']): AuthService {
  const auth = new AuthService({} as any);
  spyOn(auth, 'login').and.callFake(() => {
    auth.usuarioActual.set({ id: 1, nombre: 'Test', email: 't@biopet.com', rol, activo: true });
    return of({ expiresIn: 3600 });
  });
  return auth;
}

function crearComponente(rol: UsuarioResponse['rol']) {
  const auth = crearAuthConRol(rol);
  const routerSpy = jasmine.createSpyObj<Router>('Router', ['navigateByUrl']);
  const component = new LoginComponent(auth, routerSpy, {} as any, new FormBuilder());
  component.form.setValue({ email: 't@biopet.com', password: 'ClaveSegura123*' });
  return { component, routerSpy };
}

describe('LoginComponent — aterrizaje por rol tras login exitoso', () => {
  it('ADMIN termina en /panel (no en /mascotas)', () => {
    const { component, routerSpy } = crearComponente('ROLE_ADMIN');

    component.ingresar();

    expect(routerSpy.navigateByUrl).toHaveBeenCalledWith('/panel');
  });

  it('DUENO termina en /mascotas (sin acceso real a Panel)', () => {
    const { component, routerSpy } = crearComponente('ROLE_DUENO');

    component.ingresar();

    expect(routerSpy.navigateByUrl).toHaveBeenCalledWith('/mascotas');
  });

  // VETERINARIO/AUXILIAR no se repiten aquí: rutaInicioParaRol ya los
  // cubre exhaustivamente en roles.spec.ts, y este componente delega en
  // esa misma función sin lógica propia adicional.

  it('si el login "tiene éxito" pero el perfil no pudo resolverse (usuarioActual sigue null), cae al fallback seguro /mascotas en vez de fallar o quedarse bloqueado', () => {
    // Reproduce el caso real (ver auth.service.spec.ts): login() completa
    // con éxito (no propaga el fallo del GET /api/usuarios/me como error),
    // pero usuarioActual() queda en null. Aquí NO se simula ese contrato
    // completo -eso ya está probado por separado-, solo la reacción de
    // LoginComponent ante login() resolviendo mientras usuarioActual()
    // sigue null.
    const auth = new AuthService({} as any);
    spyOn(auth, 'login').and.returnValue(of({ expiresIn: 3600 })); // usuarioActual() NO se setea
    const routerSpy = jasmine.createSpyObj<Router>('Router', ['navigateByUrl']);
    const component = new LoginComponent(auth, routerSpy, {} as any, new FormBuilder());
    component.form.setValue({ email: 't@biopet.com', password: 'ClaveSegura123*' });

    component.ingresar();

    expect(auth.usuarioActual()).toBeNull();
    expect(routerSpy.navigateByUrl).toHaveBeenCalledWith('/mascotas');
    expect(component.cargando()).toBeFalse();
    expect(component.error()).toBe(''); // no se muestra como un fallo de login
  });
});
