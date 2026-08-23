import { FormBuilder } from '@angular/forms';

import { AuthService, UsuarioResponse } from '../core/auth.service';
import { Cita } from './cita-api.service';
import { CitasComponent } from './citas.component';

/**
 * Se instancia el componente directamente con `new` (sin TestBed): estos
 * métodos/getters de permiso solo leen `auth.usuarioActual()`, así que no
 * hace falta levantar HttpClient, change detection ni el template completo
 * -y así ngOnInit() nunca se llama, evitando disparar cargar() (HTTP real).
 * Esto es UX, no seguridad: la regla real vive en @PreAuthorize
 * (CitaController/CitaService) — ver los comentarios del propio componente.
 */
function crearComponente(): { component: CitasComponent; auth: AuthService } {
  const auth = new AuthService({} as any);
  const component = new CitasComponent(
    {} as any, // CitaApiService
    {} as any, // MascotaApiService
    {} as any, // UsuarioSeleccionableApiService
    auth,
    {} as any, // ProblemDetailService
    new FormBuilder()
  );
  return { component, auth };
}

function usuario(rol: UsuarioResponse['rol'], id = 1): UsuarioResponse {
  return { id, nombre: 'Test', email: 't@biopet.com', rol, activo: true };
}

function cita(veterinarioId: number): Cita {
  return {
    id: 1,
    mascotaId: 1,
    mascotaNombre: 'Firulais',
    veterinarioId,
    veterinarioNombre: 'Dr. Vet',
    fechaHora: new Date().toISOString(),
    estado: 'PROGRAMADA',
    motivo: null,
    activo: true,
    creadoEn: new Date().toISOString(),
    actualizadoEn: new Date().toISOString(),
  };
}

describe('CitasComponent — permisos de UI (POST/PUT /api/citas)', () => {
  it('puedeCrear es true para ADMIN y AUXILIAR (únicos roles que POST /api/citas acepta)', () => {
    const { component, auth } = crearComponente();

    auth.usuarioActual.set(usuario('ROLE_ADMIN'));
    expect(component.puedeCrear).toBeTrue();

    auth.usuarioActual.set(usuario('ROLE_AUXILIAR'));
    expect(component.puedeCrear).toBeTrue();
  });

  it('puedeCrear es false para VETERINARIO y DUENO', () => {
    const { component, auth } = crearComponente();

    auth.usuarioActual.set(usuario('ROLE_VETERINARIO'));
    expect(component.puedeCrear).toBeFalse();

    auth.usuarioActual.set(usuario('ROLE_DUENO'));
    expect(component.puedeCrear).toBeFalse();
  });

  it('un VETERINARIO puede editar su propia cita asignada, pero no la de un veterinario ajeno', () => {
    const { component, auth } = crearComponente();
    auth.usuarioActual.set(usuario('ROLE_VETERINARIO', 42));

    expect(component.puedeEditar(cita(42))).toBeTrue();
    expect(component.puedeEditar(cita(99))).toBeFalse();
  });

  it('ADMIN puede editar cualquier cita, independientemente del veterinario asignado', () => {
    const { component, auth } = crearComponente();
    auth.usuarioActual.set(usuario('ROLE_ADMIN', 1));

    expect(component.puedeEditar(cita(42))).toBeTrue();
    expect(component.puedeEditar(cita(99))).toBeTrue();
  });
});
