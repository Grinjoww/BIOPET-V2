import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { AuthService, UsuarioResponse } from '../core/auth.service';
import { MascotasComponent } from './mascotas.component';
import { Mascota, PageResponse } from './mascota-api.service';

function usuario(rol: UsuarioResponse['rol']): UsuarioResponse {
  return { id: 1, nombre: 'Test', email: 't@biopet.com', rol, activo: true };
}

function mascota(overrides: Partial<Mascota> = {}): Mascota {
  return {
    id: 7,
    duenioId: 3,
    duenioNombre: 'Ana Dueña',
    nombre: 'Firulais',
    especie: 'Perro',
    raza: 'Mestizo',
    fechaNacimiento: '2022-01-15',
    activo: true,
    creadoEn: '2026-01-01T00:00:00Z',
    actualizadoEn: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

function paginaCon(mascotas: Mascota[], extra: Partial<PageResponse<Mascota>> = {}): PageResponse<Mascota> {
  return {
    content: mascotas,
    totalElements: mascotas.length,
    totalPages: mascotas.length > 0 ? 1 : 0,
    number: 0,
    size: 10,
    first: true,
    last: true,
    empty: mascotas.length === 0,
    ...extra,
  };
}

describe('MascotasComponent (integración ligera: TestBed + HttpTestingController + Router real)', () => {
  let fixture: ComponentFixture<MascotasComponent>;
  let component: MascotasComponent;
  let httpMock: HttpTestingController;
  let auth: AuthService;

  function crear(rol: UsuarioResponse['rol']) {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [MascotasComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    fixture = TestBed.createComponent(MascotasComponent);
    component = fixture.componentInstance;
    auth = TestBed.inject(AuthService);
    auth.usuarioActual.set(usuario(rol));
    httpMock = TestBed.inject(HttpTestingController);
  }

  afterEach(() => httpMock.verify());

  it('carga inicial: pinta nombre, expediente derivado, especie, raza, dueño, enlace y paginación reales', () => {
    crear('ROLE_ADMIN');
    fixture.detectChanges(); // ngOnInit -> cargar() -> GET /api/mascotas

    const req = httpMock.expectOne((r) => r.url === '/api/mascotas' && r.method === 'GET');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('10');
    req.flush(paginaCon([mascota()], { totalPages: 3, totalElements: 25 }));
    fixture.detectChanges();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Firulais');
    expect(texto).toContain('EXP-000007');
    expect(texto).toContain('Perro');
    expect(texto).toContain('Mestizo');
    expect(texto).toContain('Ana Dueña');
    expect(texto).toContain('Página 1 de 3 (25 mascotas)');

    const enlace: HTMLAnchorElement = fixture.nativeElement.querySelector('a.record-link');
    expect(enlace.getAttribute('href')).toBe('/mascotas/7');
  });

  it('empty state clínico: aparece CTA "Registrar mascota"; para DUENO, copy distinto y sin CTA', () => {
    crear('ROLE_ADMIN');
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === '/api/mascotas' && r.method === 'GET').flush(paginaCon([]));
    fixture.detectChanges();

    let texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Sin expedientes todavía');
    expect(texto).toContain('Registra la primera mascota');
    expect(fixture.nativeElement.querySelector('button.btn--primary')).toBeTruthy();

    // --- Segunda instancia, esta vez DUENO ---
    crear('ROLE_DUENO');
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === '/api/mascotas' && r.method === 'GET').flush(paginaCon([]));
    fixture.detectChanges();

    texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Cuando la clínica registre una mascota a tu nombre');
    expect(texto).not.toContain('Registra la primera mascota');
  });

  it('selector de dueño: NO se dispara hasta abrir "Nueva mascota"; luego trae nombre — email y el value real es el id', () => {
    crear('ROLE_ADMIN');
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === '/api/mascotas' && r.method === 'GET').flush(paginaCon([]));
    fixture.detectChanges();

    httpMock.expectNone('/api/usuarios/duenios');

    const btnNueva: HTMLButtonElement = fixture.nativeElement.querySelector('.page-header__actions button');
    btnNueva.click();
    fixture.detectChanges();

    const req = httpMock.expectOne('/api/usuarios/duenios');
    req.flush([
      { id: 3, nombre: 'Ana Dueña', email: 'ana@biopet.com', rol: 'ROLE_DUENO' },
      { id: 9, nombre: 'Beto Dueño', email: 'beto@biopet.com', rol: 'ROLE_DUENO' },
    ]);
    fixture.detectChanges();

    const select: HTMLSelectElement = fixture.nativeElement.querySelector('#f-duenioId');
    const opciones = Array.from(select.options).filter((o) => !o.disabled);
    expect(opciones.map((o) => o.textContent?.trim())).toEqual(['Ana Dueña — ana@biopet.com', 'Beto Dueño — beto@biopet.com']);

    const segunda = opciones[1];
    select.value = segunda.value;
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(component.form.get('duenioId')!.value).toBe(9); // el id real, no el texto ni el índice
  });

  it('selector de dueño: si falla, muestra el error y "Reintentar" dispara una nueva petición', () => {
    crear('ROLE_ADMIN');
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === '/api/mascotas' && r.method === 'GET').flush(paginaCon([]));
    fixture.detectChanges();

    component.abrirCrear();
    fixture.detectChanges();
    httpMock.expectOne('/api/usuarios/duenios').flush('fallo', { status: 500, statusText: 'Internal Server Error' });
    fixture.detectChanges();

    const hint: HTMLElement = fixture.nativeElement.querySelector('#hint-duenioId');
    expect(hint.textContent).toContain('No se pudo completar la operación');

    const reintentar: HTMLButtonElement = hint.querySelector('button')!;
    reintentar.click();
    httpMock.expectOne('/api/usuarios/duenios').flush([]);
  });

  it('creación exitosa: POST con el body exacto, luego cierra el formulario, refresca el listado y muestra éxito', () => {
    crear('ROLE_ADMIN');
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === '/api/mascotas' && r.method === 'GET').flush(paginaCon([]));
    fixture.detectChanges();

    component.abrirCrear();
    httpMock.expectOne('/api/usuarios/duenios').flush([]);
    component.form.setValue({ duenioId: 3, nombre: 'Rex', especie: 'Perro', raza: 'Labrador', fechaNacimiento: '2023-05-01' });

    component.guardar();

    const post = httpMock.expectOne((r) => r.url === '/api/mascotas' && r.method === 'POST');
    expect(post.request.body).toEqual({ duenioId: 3, nombre: 'Rex', especie: 'Perro', raza: 'Labrador', fechaNacimiento: '2023-05-01' });
    post.flush(mascota({ id: 8, nombre: 'Rex' }));

    httpMock.expectOne((r) => r.url === '/api/mascotas' && r.method === 'GET').flush(paginaCon([mascota({ id: 8, nombre: 'Rex' })]));
    fixture.detectChanges();

    expect(component.mostrarFormulario()).toBeFalse();
    expect(component.mensajeExito()).toContain('creada correctamente');
  });

  it('validación cliente: submit con un campo requerido vacío NO dispara POST y marca el error', () => {
    crear('ROLE_ADMIN');
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === '/api/mascotas' && r.method === 'GET').flush(paginaCon([]));
    fixture.detectChanges();

    component.abrirCrear();
    httpMock.expectOne('/api/usuarios/duenios').flush([]);
    // nombre queda vacío a propósito.
    component.form.patchValue({ duenioId: 3, especie: 'Perro', raza: 'Mestizo', fechaNacimiento: '2023-01-01' });

    component.guardar();
    fixture.detectChanges();

    httpMock.expectNone((r) => r.method === 'POST');
    expect(component.tieneError('nombre')).toBeTrue();
  });

  it('validación 422 real del backend: el error llega al campo exacto, no solo como banner genérico, y el formulario no se pierde', () => {
    crear('ROLE_ADMIN');
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === '/api/mascotas' && r.method === 'GET').flush(paginaCon([]));
    fixture.detectChanges();

    component.abrirCrear();
    httpMock.expectOne('/api/usuarios/duenios').flush([]);
    component.form.setValue({ duenioId: 3, nombre: 'Rex', especie: 'Perro', raza: 'Labrador', fechaNacimiento: '2023-05-01' });

    component.guardar();
    const post = httpMock.expectOne('/api/mascotas');
    post.flush(
      {
        type: 'urn:biopet:error:validation',
        title: 'Error de validación',
        status: 422,
        detail: 'Uno o más campos contienen valores inválidos.',
        instance: '/api/mascotas',
        errors: { especie: ['Especie no reconocida'] },
      },
      { status: 422, statusText: 'Unprocessable Entity' }
    );
    fixture.detectChanges();

    expect(component.tieneError('especie')).toBeTrue();
    expect(component.mensajeError('especie')).toBe('Especie no reconocida');
    expect(component.error()).toBe(''); // no se degradó a un banner genérico
    expect(component.mostrarFormulario()).toBeTrue(); // el formulario sigue abierto con los datos
    expect(component.form.get('nombre')!.value).toBe('Rex');
  });

  it('edición: precarga los datos reales (incl. dueño) y envía PUT con el id correcto', () => {
    crear('ROLE_ADMIN');
    fixture.detectChanges();
    const existente = mascota({ id: 12, nombre: 'Luna', duenioId: 9 });
    httpMock.expectOne((r) => r.url === '/api/mascotas' && r.method === 'GET').flush(paginaCon([existente]));
    fixture.detectChanges();

    component.abrirEditar(existente);
    httpMock.expectOne('/api/usuarios/duenios').flush([]);
    fixture.detectChanges();

    expect(component.form.getRawValue()).toEqual({
      duenioId: 9,
      nombre: 'Luna',
      especie: 'Perro',
      raza: 'Mestizo',
      fechaNacimiento: '2022-01-15',
    });
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('EXP-000012');

    component.form.patchValue({ nombre: 'Luna II' });
    component.guardar();

    const put = httpMock.expectOne((r) => r.url === '/api/mascotas/12' && r.method === 'PUT');
    expect(put.request.body.nombre).toBe('Luna II');
    expect(put.request.body.duenioId).toBe(9);
    put.flush({ ...existente, nombre: 'Luna II' });
    httpMock.expectOne((r) => r.url === '/api/mascotas' && r.method === 'GET').flush(paginaCon([{ ...existente, nombre: 'Luna II' }]));
  });

  it('baja: cancelar en el modal NO dispara DELETE', () => {
    crear('ROLE_ADMIN');
    fixture.detectChanges();
    const m = mascota();
    httpMock.expectOne((r) => r.url === '/api/mascotas' && r.method === 'GET').flush(paginaCon([m]));
    fixture.detectChanges();

    component.pedirConfirmacionEliminar(m);
    fixture.detectChanges();

    const modal = fixture.nativeElement.querySelector('[role="alertdialog"]');
    expect(modal).toBeTruthy();
    expect(modal.textContent).toContain('Confirmar eliminación');

    const cancelar: HTMLButtonElement = modal.querySelector('.btn--secondary');
    cancelar.click();
    fixture.detectChanges();

    httpMock.expectNone((r) => r.method === 'DELETE');
    expect(component.mascotaAEliminar()).toBeNull();
  });

  it('baja: confirmar dispara DELETE con el id correcto y refresca el listado', () => {
    crear('ROLE_ADMIN');
    fixture.detectChanges();
    const m = mascota();
    httpMock.expectOne((r) => r.url === '/api/mascotas' && r.method === 'GET').flush(paginaCon([m]));
    fixture.detectChanges();

    component.pedirConfirmacionEliminar(m);
    component.confirmarEliminar();

    const del = httpMock.expectOne((r) => r.url === '/api/mascotas/7' && r.method === 'DELETE');
    del.flush(null);
    httpMock.expectOne((r) => r.url === '/api/mascotas' && r.method === 'GET').flush(paginaCon([]));
    fixture.detectChanges();

    expect(component.mensajeExito()).toContain('fue eliminada');
  });
});
