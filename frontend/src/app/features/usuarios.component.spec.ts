import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { AuthService, UsuarioResponse } from '../core/auth.service';
import { UsuariosComponent } from './usuarios.component';
import { Usuario } from './usuario-api.service';
import { PageResponse } from './mascota-api.service';

function sesion(rol: UsuarioResponse['rol'], id = 1): UsuarioResponse {
  return { id, nombre: 'Yo', email: 'yo@biopet.com', rol, activo: true };
}

function usuarioFila(overrides: Partial<Usuario> = {}): Usuario {
  return { id: 2, nombre: 'Ana Vet', email: 'ana@biopet.com', rol: 'ROLE_VETERINARIO', activo: true, ...overrides };
}

function paginaCon(usuarios: Usuario[]): PageResponse<Usuario> {
  return {
    content: usuarios,
    totalElements: usuarios.length,
    totalPages: usuarios.length > 0 ? 1 : 0,
    number: 0,
    size: 10,
    first: true,
    last: true,
    empty: usuarios.length === 0,
  };
}

describe('UsuariosComponent (integración ligera: TestBed + HttpTestingController + AuthService real)', () => {
  let fixture: ComponentFixture<UsuariosComponent>;
  let component: UsuariosComponent;
  let httpMock: HttpTestingController;

  function crear(rolPropio: UsuarioResponse['rol'], idPropio = 1) {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [UsuariosComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    fixture = TestBed.createComponent(UsuariosComponent);
    component = fixture.componentInstance;
    TestBed.inject(AuthService).usuarioActual.set(sesion(rolPropio, idPropio));
    httpMock = TestBed.inject(HttpTestingController);
  }

  function flushListado(usuarios: Usuario[]) {
    httpMock.expectOne((r) => r.url === '/api/usuarios' && r.method === 'GET').flush(paginaCon(usuarios));
  }

  afterEach(() => httpMock.verify());

  it('carga real: pinta nombre, email, rol humanizado y estado para ADMIN/VETERINARIO/DUENO', () => {
    crear('ROLE_ADMIN');
    fixture.detectChanges();

    const req = httpMock.expectOne((r) => r.url === '/api/usuarios' && r.method === 'GET');
    expect(req.request.params.get('page')).toBe('0');
    req.flush(
      paginaCon([
        usuarioFila({ id: 1, nombre: 'Root Admin', email: 'root@biopet.com', rol: 'ROLE_ADMIN' }),
        usuarioFila({ id: 2, nombre: 'Ana Vet', email: 'ana@biopet.com', rol: 'ROLE_VETERINARIO' }),
        usuarioFila({ id: 3, nombre: 'Beto Dueño', email: 'beto@biopet.com', rol: 'ROLE_DUENO' }),
      ])
    );
    fixture.detectChanges();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Root Admin');
    expect(texto).toContain('root@biopet.com');
    expect(texto).toContain('Administrador');
    expect(texto).toContain('Ana Vet');
    expect(texto).toContain('Veterinario');
    expect(texto).toContain('Beto Dueño');
    expect(texto).toContain('Dueño');
    expect(texto).toContain('Activo');
  });

  it('propio ADMIN: la fila de sí mismo tiene botón editar pero NO botón de baja', () => {
    crear('ROLE_ADMIN', 1);
    fixture.detectChanges();
    flushListado([usuarioFila({ id: 1, nombre: 'Root Admin', rol: 'ROLE_ADMIN' })]);
    fixture.detectChanges();

    const fila = fixture.nativeElement.querySelector('tbody tr');
    expect(fila.querySelector('.btn-icon:not(.btn-icon--danger)')).toBeTruthy(); // editar
    expect(fila.querySelector('.btn-icon--danger')).toBeFalsy(); // sin baja
  });

  it('propio ADMIN: al editarse a sí mismo, el selector de rol queda deshabilitado, aparece el aviso, y el password no se precarga', () => {
    crear('ROLE_ADMIN', 1);
    fixture.detectChanges();
    const propio = usuarioFila({ id: 1, nombre: 'Root Admin', email: 'root@biopet.com', rol: 'ROLE_ADMIN' });
    flushListado([propio]);
    fixture.detectChanges();

    component.abrirEditar(propio);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Estás editando tu propia cuenta');
    expect(component.form.get('rol')!.disabled).toBeTrue();
    expect(component.form.get('password')!.value).toBe('');

    const inputPassword: HTMLInputElement = fixture.nativeElement.querySelector('#f-password');
    expect(inputPassword.value).toBe('');
  });

  it('editar OTRO usuario: nombre/email/rol se precargan, el selector de rol queda habilitado y el password sigue vacío', () => {
    crear('ROLE_ADMIN', 1);
    fixture.detectChanges();
    const otro = usuarioFila({ id: 2, nombre: 'Ana Vet', email: 'ana@biopet.com', rol: 'ROLE_VETERINARIO' });
    flushListado([otro]);
    fixture.detectChanges();

    component.abrirEditar(otro);
    fixture.detectChanges();

    expect(component.form.getRawValue()).toEqual({ nombre: 'Ana Vet', email: 'ana@biopet.com', rol: 'ROLE_VETERINARIO', password: '' });
    expect(component.form.get('rol')!.disabled).toBeFalse();
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Estás editando tu propia cuenta');

    const fila = fixture.nativeElement.querySelector('tbody tr');
    expect(fila.querySelector('.btn-icon--danger')).toBeTruthy(); // sí hay botón de baja sobre otro usuario
  });

  it('guardar sin tocar el password en edición envía password:null explícito (no "", no se omite)', () => {
    crear('ROLE_ADMIN', 1);
    fixture.detectChanges();
    const otro = usuarioFila({ id: 2, nombre: 'Ana Vet', email: 'ana@biopet.com', rol: 'ROLE_VETERINARIO' });
    flushListado([otro]);
    fixture.detectChanges();

    component.abrirEditar(otro);
    component.form.patchValue({ nombre: 'Ana Veterinaria' }); // toca otro campo, no el password
    component.guardar();

    const put = httpMock.expectOne((r) => r.url === '/api/usuarios/2' && r.method === 'PUT');
    expect(put.request.body).toEqual({ nombre: 'Ana Veterinaria', email: 'ana@biopet.com', rol: 'ROLE_VETERINARIO', password: null });
    put.flush({ ...otro, nombre: 'Ana Veterinaria' });
    flushListado([{ ...otro, nombre: 'Ana Veterinaria' }]);
  });

  it('creación: POST con el payload exacto (sin "activo" inventado) y el selector solo ofrece los 4 roles reales', () => {
    crear('ROLE_ADMIN', 1);
    fixture.detectChanges();
    flushListado([]);
    fixture.detectChanges();

    expect(component.roles).toEqual(['ROLE_ADMIN', 'ROLE_VETERINARIO', 'ROLE_AUXILIAR', 'ROLE_DUENO']);

    component.abrirCrear();
    component.form.setValue({ nombre: 'Nuevo Usuario', email: 'nuevo@biopet.com', rol: 'ROLE_AUXILIAR', password: 'ClaveSegura123*' });
    component.guardar();

    const post = httpMock.expectOne((r) => r.url === '/api/usuarios' && r.method === 'POST');
    const body = post.request.body;
    expect(body).toEqual({ nombre: 'Nuevo Usuario', email: 'nuevo@biopet.com', rol: 'ROLE_AUXILIAR', password: 'ClaveSegura123*' });
    expect('activo' in body).toBeFalse();
    post.flush(usuarioFila({ id: 9, nombre: 'Nuevo Usuario', rol: 'ROLE_AUXILIAR' }));
    flushListado([usuarioFila({ id: 9, nombre: 'Nuevo Usuario', rol: 'ROLE_AUXILIAR' })]);
    fixture.detectChanges();

    expect(component.mostrarFormulario()).toBeFalse();
    expect(component.mensajeExito()).toContain('creado correctamente');
  });

  it('creación con password vacío NO envía POST (obligatorio solo al crear)', () => {
    crear('ROLE_ADMIN', 1);
    fixture.detectChanges();
    flushListado([]);
    fixture.detectChanges();

    component.abrirCrear();
    component.form.patchValue({ nombre: 'X', email: 'x@biopet.com', rol: 'ROLE_DUENO', password: '' });
    component.guardar();
    fixture.detectChanges();

    httpMock.expectNone((r) => r.method === 'POST');
    expect(component.tieneError('password')).toBeTrue();
  });

  it('email duplicado: el backend real responde 409 (EmailDuplicadoException) — mensaje mostrado, formulario abierto, datos conservados, sin éxito', () => {
    crear('ROLE_ADMIN', 1);
    fixture.detectChanges();
    flushListado([]);
    fixture.detectChanges();

    component.abrirCrear();
    component.form.setValue({ nombre: 'Dup', email: 'dup@biopet.com', rol: 'ROLE_DUENO', password: 'ClaveSegura123*' });
    component.guardar();

    const post = httpMock.expectOne((r) => r.url === '/api/usuarios' && r.method === 'POST');
    post.flush(
      {
        type: 'urn:biopet:error:conflict',
        title: 'Conflicto de datos',
        status: 409,
        detail: 'El correo dup@biopet.com ya está registrado.',
        instance: '/api/usuarios',
      },
      { status: 409, statusText: 'Conflict' }
    );
    fixture.detectChanges();

    expect(component.error()).toContain('ya está registrado');
    expect(component.mostrarFormulario()).toBeTrue();
    expect(component.form.get('email')!.value).toBe('dup@biopet.com');
    expect(component.mensajeExito()).toBe('');
  });

  it('baja: el modal dice "Dar de baja usuario" y explica que el registro no se elimina; cancelar no dispara DELETE', () => {
    crear('ROLE_ADMIN', 1);
    fixture.detectChanges();
    const otro = usuarioFila({ id: 2 });
    flushListado([otro]);
    fixture.detectChanges();

    component.pedirConfirmacionBaja(otro);
    fixture.detectChanges();

    const modal = fixture.nativeElement.querySelector('[role="alertdialog"]');
    expect(modal.textContent).toContain('Dar de baja usuario');
    expect(modal.textContent).toContain('no se eliminará de la base de datos');

    const volver: HTMLButtonElement = modal.querySelector('.btn--secondary');
    volver.click();
    fixture.detectChanges();

    httpMock.expectNone((r) => r.method === 'DELETE');
    expect(component.usuarioABajar()).toBeNull();
  });

  it('baja: confirmar dispara DELETE con el id correcto y refresca el listado', () => {
    crear('ROLE_ADMIN', 1);
    fixture.detectChanges();
    const otro = usuarioFila({ id: 2 });
    flushListado([otro]);
    fixture.detectChanges();

    component.pedirConfirmacionBaja(otro);
    component.confirmarBaja();

    const del = httpMock.expectOne((r) => r.url === '/api/usuarios/2' && r.method === 'DELETE');
    del.flush(null);
    flushListado([]);
    fixture.detectChanges();

    expect(component.mensajeExito()).toContain('dada de baja');
  });

  it('toggle de password: empieza oculto, "Mostrar" lo revela con aria-pressed correcto, "Ocultar" lo vuelve a ocultar', () => {
    crear('ROLE_ADMIN', 1);
    fixture.detectChanges();
    flushListado([]);
    fixture.detectChanges();

    component.abrirCrear();
    fixture.detectChanges();

    const input: HTMLInputElement = fixture.nativeElement.querySelector('#f-password');
    const boton: HTMLButtonElement = fixture.nativeElement.querySelector('.password-field button');

    expect(input.type).toBe('password');
    expect(boton.getAttribute('aria-pressed')).toBe('false');

    boton.click();
    fixture.detectChanges();
    expect(input.type).toBe('text');
    expect(boton.getAttribute('aria-pressed')).toBe('true');

    boton.click();
    fixture.detectChanges();
    expect(input.type).toBe('password');
    expect(boton.getAttribute('aria-pressed')).toBe('false');
  });
});
