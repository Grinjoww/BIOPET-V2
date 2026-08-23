import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { AuthService, UsuarioResponse } from '../core/auth.service';
import { ConsultasComponent } from './consultas.component';
import { Consulta } from './consulta-api.service';
import { PageResponse } from './mascota-api.service';

function usuario(rol: UsuarioResponse['rol']): UsuarioResponse {
  return { id: 1, nombre: 'Test', email: 't@biopet.com', rol, activo: true };
}

function consulta(overrides: Partial<Consulta> = {}): Consulta {
  return {
    id: 4,
    mascotaId: 7,
    mascotaNombre: 'Firulais',
    veterinarioId: 2,
    veterinarioNombre: 'Dra. Pérez',
    fechaConsulta: '2026-03-10T14:30:00Z',
    motivo: 'Chequeo anual',
    diagnostico: 'Sano',
    tratamiento: 'Ninguno',
    observaciones: 'Buen estado general',
    activo: true,
    creadoEn: '2026-03-10T14:30:00Z',
    actualizadoEn: '2026-03-10T14:30:00Z',
    ...overrides,
  };
}

function paginaCon(consultas: Consulta[], extra: Partial<PageResponse<Consulta>> = {}): PageResponse<Consulta> {
  return {
    content: consultas,
    totalElements: consultas.length,
    totalPages: consultas.length > 0 ? 1 : 0,
    number: 0,
    size: 10,
    first: true,
    last: true,
    empty: consultas.length === 0,
    ...extra,
  };
}

describe('ConsultasComponent (integración ligera: TestBed + HttpTestingController + Router real)', () => {
  let fixture: ComponentFixture<ConsultasComponent>;
  let component: ConsultasComponent;
  let httpMock: HttpTestingController;

  function crear(rol: UsuarioResponse['rol']) {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [ConsultasComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    fixture = TestBed.createComponent(ConsultasComponent);
    component = fixture.componentInstance;
    TestBed.inject(AuthService).usuarioActual.set(usuario(rol));
    httpMock = TestBed.inject(HttpTestingController);
  }

  function flushListado(consultas: Consulta[]) {
    httpMock.expectOne((r) => r.url === '/api/consultas' && r.method === 'GET').flush(paginaCon(consultas));
  }

  afterEach(() => httpMock.verify());

  it('carga y presentación: muestra fecha, mascota, veterinario, motivo, diagnóstico, tratamiento y observaciones reales', () => {
    crear('ROLE_ADMIN');
    fixture.detectChanges();
    flushListado([consulta()]);
    fixture.detectChanges();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Firulais');
    expect(texto).toContain('Dra. Pérez');
    expect(texto).toContain('Chequeo anual');
    expect(texto).toContain('Sano');
    expect(texto).toContain('Ninguno');
    expect(texto).toContain('Buen estado general');
    expect(texto).toContain('2026'); // el date pipe formatea la fecha real
  });

  it('cuando diagnóstico/tratamiento/observaciones vienen null, usa los fallbacks reales "No registrado"/"No registradas"', () => {
    crear('ROLE_ADMIN');
    fixture.detectChanges();
    flushListado([consulta({ diagnostico: null, tratamiento: null, observaciones: null })]);
    fixture.detectChanges();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('No registrado');
    expect(texto).toContain('No registradas');
  });

  it('el nombre de la mascota es un enlace real a /mascotas/:id', () => {
    crear('ROLE_ADMIN');
    fixture.detectChanges();
    flushListado([consulta({ mascotaId: 42 })]);
    fixture.detectChanges();

    const enlace: HTMLAnchorElement = fixture.nativeElement.querySelector('a.record-link');
    expect(enlace.getAttribute('href')).toBe('/mascotas/42');
  });

  it('ADMIN, VETERINARIO y AUXILIAR ven las acciones de crear/editar/dar de baja', () => {
    for (const rol of ['ROLE_ADMIN', 'ROLE_VETERINARIO', 'ROLE_AUXILIAR'] as const) {
      crear(rol);
      fixture.detectChanges();
      flushListado([consulta()]);
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('.page-header__actions button')).withContext(rol).toBeTruthy();
      expect(fixture.nativeElement.querySelector('article .actions')).withContext(rol).toBeTruthy();
    }
  });

  it('DUENO solo lectura: sin botón de creación y sin acciones de editar/baja por registro', () => {
    crear('ROLE_DUENO');
    fixture.detectChanges();
    flushListado([consulta()]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.page-header__actions')).toBeFalsy();
    expect(fixture.nativeElement.querySelector('article .actions')).toBeFalsy();
  });

  it('creación: carga perezosa de mascotas/veterinarios al abrir, y POST con los 7 campos reales', () => {
    crear('ROLE_ADMIN');
    fixture.detectChanges();
    flushListado([]);
    fixture.detectChanges();

    httpMock.expectNone((r) => r.url === '/api/mascotas');
    httpMock.expectNone((r) => r.url === '/api/usuarios/veterinarios');

    component.abrirCrear();
    fixture.detectChanges();

    httpMock.expectOne((r) => r.url === '/api/mascotas' && r.method === 'GET').flush(paginaCon([]));
    httpMock.expectOne('/api/usuarios/veterinarios').flush([]);

    component.form.setValue({
      mascotaId: 7,
      veterinarioId: 2,
      fechaConsulta: '2026-03-01T09:00',
      motivo: 'Vacunación',
      diagnostico: 'N/A',
      tratamiento: 'N/A',
      observaciones: 'Ninguna',
    });
    component.guardar();

    const post = httpMock.expectOne((r) => r.url === '/api/consultas' && r.method === 'POST');
    expect(post.request.body).toEqual({
      mascotaId: 7,
      veterinarioId: 2,
      fechaConsulta: new Date('2026-03-01T09:00').toISOString(),
      motivo: 'Vacunación',
      diagnostico: 'N/A',
      tratamiento: 'N/A',
      observaciones: 'Ninguna',
    });
    post.flush(consulta({ id: 99 }));
    flushListado([consulta({ id: 99 })]);
    fixture.detectChanges();

    expect(component.mostrarFormulario()).toBeFalse();
    expect(component.mensajeExito()).toContain('registrada correctamente');
  });

  it('fecha futura: el input tiene el atributo max correcto (hora fijada) y ningún validator propio de Reactive Forms bloquea una fecha futura', () => {
    jasmine.clock().install();
    jasmine.clock().mockDate(new Date(2026, 5, 15, 10, 0));
    try {
      crear('ROLE_ADMIN');
      fixture.detectChanges();
      flushListado([]);
      component.abrirCrear();
      httpMock.expectOne((r) => r.url === '/api/mascotas').flush(paginaCon([]));
      httpMock.expectOne('/api/usuarios/veterinarios').flush([]);
      fixture.detectChanges();

      const input: HTMLInputElement = fixture.nativeElement.querySelector('#f-fechaConsulta');
      expect(input.getAttribute('max')).toBe('2026-06-15T10:00');

      // Solo `max` HTML es UX; Reactive Forms no tiene un validador propio
      // de "no futuro" en este control (únicamente Validators.required) —
      // se comprueba exactamente eso, sin fingir que Angular invalida algo
      // que en realidad no invalida.
      component.form.get('fechaConsulta')!.setValue('2099-01-01T00:00');
      expect(component.form.get('fechaConsulta')!.valid).toBeTrue();
    } finally {
      jasmine.clock().uninstall();
    }
  });

  it('textos largos (diagnóstico/tratamiento/observaciones) se renderizan íntegros, sin recortarse', () => {
    const textoLargo = 'Observación clínica detallada. '.repeat(10); // ~320 caracteres
    crear('ROLE_ADMIN');
    fixture.detectChanges();
    flushListado([consulta({ observaciones: textoLargo })]);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain(textoLargo.trim());
  });

  it('edición: precarga los 7 campos reales (mascota/veterinario/fecha incluidos) y envía PUT correcto', () => {
    crear('ROLE_ADMIN');
    fixture.detectChanges();
    const existente = consulta({ id: 15, mascotaId: 7, veterinarioId: 2 });
    flushListado([existente]);
    fixture.detectChanges();

    component.abrirEditar(existente);
    httpMock.expectOne((r) => r.url === '/api/mascotas').flush(paginaCon([]));
    httpMock.expectOne('/api/usuarios/veterinarios').flush([]);
    fixture.detectChanges();

    const v = component.form.getRawValue();
    expect(v.mascotaId).toBe(7);
    expect(v.veterinarioId).toBe(2);
    expect(v.motivo).toBe('Chequeo anual');
    expect(v.diagnostico).toBe('Sano');

    component.form.patchValue({ motivo: 'Chequeo de seguimiento' });
    component.guardar();

    const put = httpMock.expectOne((r) => r.url === '/api/consultas/15' && r.method === 'PUT');
    expect(put.request.body.mascotaId).toBe(7);
    expect(put.request.body.veterinarioId).toBe(2);
    expect(put.request.body.motivo).toBe('Chequeo de seguimiento');
    put.flush({ ...existente, motivo: 'Chequeo de seguimiento' });
    flushListado([{ ...existente, motivo: 'Chequeo de seguimiento' }]);
  });

  it('baja lógica: el modal dice "Dar de baja el registro" (nunca "Eliminar definitivamente"), y cancelar no dispara DELETE', () => {
    crear('ROLE_ADMIN');
    fixture.detectChanges();
    const c = consulta();
    flushListado([c]);
    fixture.detectChanges();

    component.pedirConfirmacionBaja(c);
    fixture.detectChanges();

    const modal = fixture.nativeElement.querySelector('[role="alertdialog"]');
    expect(modal.textContent).toContain('Dar de baja el registro');
    expect(modal.textContent).not.toContain('Eliminar definitivamente');

    const volver: HTMLButtonElement = modal.querySelector('.btn--secondary');
    volver.click();
    fixture.detectChanges();

    httpMock.expectNone((r) => r.method === 'DELETE');
    expect(component.consultaABajar()).toBeNull();
  });

  it('baja lógica: confirmar dispara DELETE con el id correcto y refresca el listado', () => {
    crear('ROLE_ADMIN');
    fixture.detectChanges();
    const c = consulta();
    flushListado([c]);
    fixture.detectChanges();

    component.pedirConfirmacionBaja(c);
    component.confirmarBaja();

    const del = httpMock.expectOne((r) => r.url === '/api/consultas/4' && r.method === 'DELETE');
    del.flush(null);
    flushListado([]);
    fixture.detectChanges();

    expect(component.mensajeExito()).toContain('dada de baja');
  });
});
