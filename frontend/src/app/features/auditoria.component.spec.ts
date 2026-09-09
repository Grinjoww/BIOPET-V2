import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { AuditoriaComponent } from './auditoria.component';
import { AuditoriaEvento, AuditoriaOpciones } from './auditoria-api.service';
import { PageResponse } from './mascota-api.service';

function evento(overrides: Partial<AuditoriaEvento> = {}): AuditoriaEvento {
  return {
    id: 1,
    fechaHora: '2026-09-08T12:00:00Z',
    usuarioEmail: 'admin@biopet.ec',
    accion: 'POST',
    modulo: 'mascotas',
    recurso: '/api/mascotas',
    metodoHttp: 'POST',
    resultado: 'SUCCESS',
    statusHttp: 201,
    ...overrides,
  };
}

function pagina(eventos: AuditoriaEvento[], extra: Partial<PageResponse<AuditoriaEvento>> = {}):
    PageResponse<AuditoriaEvento> {
  return {
    content: eventos,
    totalElements: eventos.length,
    totalPages: eventos.length > 0 ? 1 : 0,
    number: 0,
    size: 20,
    first: true,
    last: true,
    empty: eventos.length === 0,
    ...extra,
  };
}

const OPCIONES: AuditoriaOpciones = {
  usuarios: ['admin@biopet.ec', 'vet@biopet.ec'],
  acciones: ['LOGIN_SUCCESS', 'POST', 'BACKUP_COMPLETED'],
  modulos: ['auth', 'mascotas', 'admin/respaldos'],
};

describe('AuditoriaComponent (integración ligera: TestBed + HttpTestingController)', () => {
  let fixture: ComponentFixture<AuditoriaComponent>;
  let httpMock: HttpTestingController;

  function crear() {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [AuditoriaComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    fixture = TestBed.createComponent(AuditoriaComponent);
    httpMock = TestBed.inject(HttpTestingController);
  }

  function flushCargaInicial(eventos: AuditoriaEvento[] = [evento()]) {
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === '/api/admin/auditoria/opciones').flush(OPCIONES);
    httpMock.expectOne((r) => r.url === '/api/admin/auditoria' && r.method === 'GET').flush(pagina(eventos));
    fixture.detectChanges();
  }

  afterEach(() => httpMock.verify());

  it('carga inicial: puebla los 3 selects con las opciones reales y pinta la tabla', () => {
    crear();
    flushCargaInicial();

    const selectUsuario: HTMLSelectElement = fixture.nativeElement.querySelector('#f-usuario');
    const opcionesUsuario = Array.from(selectUsuario.options).map((o) => o.value);
    expect(opcionesUsuario).toEqual(['', 'admin@biopet.ec', 'vet@biopet.ec']);

    const selectAccion: HTMLSelectElement = fixture.nativeElement.querySelector('#f-accion');
    expect(Array.from(selectAccion.options).map((o) => o.value)).toContain('LOGIN_SUCCESS');

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('admin@biopet.ec');
    expect(texto).toContain('POST');
    expect(texto).toContain('mascotas');
    expect(texto).toContain('SUCCESS');
  });

  it('empty state real: sin eventos muestra el mensaje vacío, sin filas inventadas', () => {
    crear();
    flushCargaInicial([]);

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Sin eventos de auditoría para estos filtros.');
    expect(fixture.nativeElement.querySelectorAll('tbody tr').length).toBe(1);
  });

  it('filtrar por usuario/acción/módulo/rango de fechas envía exactamente esos parámetros', () => {
    crear();
    flushCargaInicial();

    const selectUsuario: HTMLSelectElement = fixture.nativeElement.querySelector('#f-usuario');
    selectUsuario.value = 'admin@biopet.ec';
    selectUsuario.dispatchEvent(new Event('change'));
    const selectAccion: HTMLSelectElement = fixture.nativeElement.querySelector('#f-accion');
    selectAccion.value = 'POST';
    selectAccion.dispatchEvent(new Event('change'));
    const selectModulo: HTMLSelectElement = fixture.nativeElement.querySelector('#f-modulo');
    selectModulo.value = 'mascotas';
    selectModulo.dispatchEvent(new Event('change'));
    const desde: HTMLInputElement = fixture.nativeElement.querySelector('#f-desde');
    desde.value = '2026-09-01';
    desde.dispatchEvent(new Event('input'));
    const hasta: HTMLInputElement = fixture.nativeElement.querySelector('#f-hasta');
    hasta.value = '2026-09-08';
    hasta.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const boton: HTMLButtonElement = fixture.nativeElement.querySelector('button[type="submit"]');
    boton.click();

    const req = httpMock.expectOne((r) => r.url === '/api/admin/auditoria' && r.method === 'GET' && r.params.get('usuario') === 'admin@biopet.ec');
    expect(req.request.params.get('accion')).toBe('POST');
    expect(req.request.params.get('modulo')).toBe('mascotas');
    expect(req.request.params.get('desde')).toBe('2026-09-01T00:00:00Z');
    expect(req.request.params.get('hasta')).toBe('2026-09-08T23:59:59Z');
    expect(req.request.params.get('page')).toBe('0');
    req.flush(pagina([evento()]));
  });

  it('"Todos"/"Todas" no envían el parámetro correspondiente', () => {
    crear();
    flushCargaInicial();

    const boton: HTMLButtonElement = fixture.nativeElement.querySelector('button[type="submit"]');
    boton.click();

    const req = httpMock.expectOne((r) => r.url === '/api/admin/auditoria' && r.method === 'GET');
    expect(req.request.params.has('usuario')).toBe(false);
    expect(req.request.params.has('accion')).toBe(false);
    expect(req.request.params.has('modulo')).toBe(false);
    expect(req.request.params.has('desde')).toBe(false);
    expect(req.request.params.has('hasta')).toBe(false);
    req.flush(pagina([]));
  });

  it('limpiar filtros resetea el formulario y vuelve a buscar sin parámetros', () => {
    crear();
    flushCargaInicial();

    const selectAccion: HTMLSelectElement = fixture.nativeElement.querySelector('#f-accion');
    selectAccion.value = 'POST';
    selectAccion.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    const botonLimpiar: HTMLButtonElement = Array.from(
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>
    ).find((b) => b.textContent?.trim() === 'Limpiar')!;
    botonLimpiar.click();

    const req = httpMock.expectOne((r) => r.url === '/api/admin/auditoria' && r.method === 'GET');
    expect(req.request.params.has('accion')).toBe(false);
    req.flush(pagina([]));
    fixture.detectChanges();

    expect(selectAccion.value).toBe('');
  });

  it('paginación: "Siguiente" pide la página 1', () => {
    crear();
    flushCargaInicial([evento()]);
    (fixture.componentInstance as AuditoriaComponent).totalPaginas.set(2);
    fixture.detectChanges();

    const siguiente: HTMLButtonElement = Array.from(
      fixture.nativeElement.querySelectorAll('.pagination button') as NodeListOf<HTMLButtonElement>
    ).find((b) => b.textContent?.includes('Siguiente'))!;
    siguiente.click();

    const req = httpMock.expectOne((r) => r.url === '/api/admin/auditoria' && r.params.get('page') === '1');
    expect(req.request.params.get('page')).toBe('1');
    req.flush(pagina([]));
  });

  it('un error del backend se muestra tal cual lo traduce ProblemDetailService', () => {
    crear();
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === '/api/admin/auditoria/opciones').flush(OPCIONES);
    httpMock
      .expectOne((r) => r.url === '/api/admin/auditoria')
      .flush({ detail: 'No tienes permiso.' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('No tienes permiso.');
  });
});
