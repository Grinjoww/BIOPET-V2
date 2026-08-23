import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { AuthService, UsuarioResponse } from '../core/auth.service';
import { VacunasComponent } from './vacunas.component';
import { Vacuna } from './vacuna-api.service';
import { PageResponse } from './mascota-api.service';

function usuario(rol: UsuarioResponse['rol']): UsuarioResponse {
  return { id: 1, nombre: 'Test', email: 't@biopet.com', rol, activo: true };
}

function vacuna(overrides: Partial<Vacuna> = {}): Vacuna {
  return {
    id: 5,
    mascotaId: 7,
    mascotaNombre: 'Firulais',
    veterinarioId: 2,
    veterinarioNombre: 'Dra. Pérez',
    tipo: 'Antirrábica',
    fechaAplicacion: '2026-01-10',
    proximaFecha: '2027-01-10',
    observaciones: null,
    activo: true,
    creadoEn: '2026-01-10T00:00:00Z',
    actualizadoEn: '2026-01-10T00:00:00Z',
    ...overrides,
  };
}

function paginaCon(vacunas: Vacuna[]): PageResponse<Vacuna> {
  return {
    content: vacunas,
    totalElements: vacunas.length,
    totalPages: vacunas.length > 0 ? 1 : 0,
    number: 0,
    size: 10,
    first: true,
    last: true,
    empty: vacunas.length === 0,
  };
}

describe('VacunasComponent (integración ligera)', () => {
  let fixture: ComponentFixture<VacunasComponent>;
  let component: VacunasComponent;
  let httpMock: HttpTestingController;

  function crear(rol: UsuarioResponse['rol']) {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [VacunasComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    fixture = TestBed.createComponent(VacunasComponent);
    component = fixture.componentInstance;
    TestBed.inject(AuthService).usuarioActual.set(usuario(rol));
    httpMock = TestBed.inject(HttpTestingController);
  }

  function flushListado(vacunas: Vacuna[]) {
    httpMock.expectOne((r) => r.url === '/api/vacunas' && r.method === 'GET').flush(paginaCon(vacunas));
  }

  afterEach(() => httpMock.verify());

  it('lista las 3 variantes de estado real (vencida/próxima/al día) con su etiqueta correcta', () => {
    jasmine.clock().install();
    jasmine.clock().mockDate(new Date(2026, 5, 15));
    try {
      crear('ROLE_ADMIN');
      fixture.detectChanges();
      flushListado([
        vacuna({ id: 1, tipo: 'Vencida', proximaFecha: '2026-05-01' }),
        vacuna({ id: 2, tipo: 'Proxima', proximaFecha: '2026-06-20' }),
        vacuna({ id: 3, tipo: 'AlDia', proximaFecha: '2026-12-01' }),
      ]);
      fixture.detectChanges();

      const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
      expect(texto).toContain('Refuerzo vencido');
      expect(texto).toContain('Refuerzo próximo');
      expect(texto).toContain('Al día');
    } finally {
      jasmine.clock().uninstall();
    }
  });

  it('selector de mascota: carga perezosa al abrir el formulario (no antes)', () => {
    crear('ROLE_ADMIN');
    fixture.detectChanges();
    flushListado([]);
    fixture.detectChanges();

    httpMock.expectNone((r) => r.url === '/api/mascotas');

    component.abrirCrear();
    fixture.detectChanges();

    const mascotasReq = httpMock.expectOne((r) => r.url === '/api/mascotas' && r.method === 'GET');
    const veterinariosReq = httpMock.expectOne('/api/usuarios/veterinarios');
    expect(mascotasReq.request.method).toBe('GET');
    expect(veterinariosReq.request.method).toBe('GET');
    mascotasReq.flush(paginaCon([] as any));
    veterinariosReq.flush([]);
  });

  it('veterinario es opcional: POST con veterinarioId=null cuando no se asigna ninguno', () => {
    crear('ROLE_ADMIN');
    fixture.detectChanges();
    flushListado([]);
    component.abrirCrear();
    httpMock.expectOne((r) => r.url === '/api/mascotas').flush(paginaCon([] as any));
    httpMock.expectOne('/api/usuarios/veterinarios').flush([]);
    fixture.detectChanges();

    component.form.setValue({
      mascotaId: 7,
      veterinarioId: null,
      tipo: 'Antirrábica',
      fechaAplicacion: '2026-06-01',
      proximaFecha: '',
      observaciones: '',
    });
    component.guardar();

    const post = httpMock.expectOne((r) => r.url === '/api/vacunas' && r.method === 'POST');
    expect(post.request.body).toEqual({
      mascotaId: 7,
      veterinarioId: null,
      tipo: 'Antirrábica',
      fechaAplicacion: '2026-06-01',
      proximaFecha: null,
      observaciones: null,
    });
    post.flush(vacuna({ id: 20, veterinarioId: null, veterinarioNombre: null }));
    flushListado([vacuna({ id: 20, veterinarioId: null, veterinarioNombre: null })]);
  });

  it('edición: precarga tipo/mascota/fechas reales y envía PUT correcto', () => {
    crear('ROLE_ADMIN');
    fixture.detectChanges();
    const existente = vacuna({ id: 11 });
    flushListado([existente]);
    fixture.detectChanges();

    component.abrirEditar(existente);
    httpMock.expectOne((r) => r.url === '/api/mascotas').flush(paginaCon([] as any));
    httpMock.expectOne('/api/usuarios/veterinarios').flush([]);
    fixture.detectChanges();

    expect(component.form.getRawValue().tipo).toBe('Antirrábica');
    expect(component.form.getRawValue().mascotaId).toBe(7);
    expect(component.form.getRawValue().fechaAplicacion).toBe('2026-01-10');

    component.form.patchValue({ observaciones: 'Sin reacciones adversas' });
    component.guardar();

    const put = httpMock.expectOne((r) => r.url === '/api/vacunas/11' && r.method === 'PUT');
    expect(put.request.body.observaciones).toBe('Sin reacciones adversas');
    expect(put.request.body.tipo).toBe('Antirrábica');
    put.flush({ ...existente, observaciones: 'Sin reacciones adversas' });
    flushListado([{ ...existente, observaciones: 'Sin reacciones adversas' }]);
  });

  it('ROLE_DUENO: solo lectura, sin botón de registrar ni acciones de editar/eliminar', () => {
    crear('ROLE_DUENO');
    fixture.detectChanges();
    flushListado([vacuna()]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.page-header__actions')).toBeFalsy();
    expect(fixture.nativeElement.querySelector('td.actions')).toBeFalsy();
  });
});
