import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { UsuarioSeleccionableApiService } from './usuario-seleccionable-api.service';

const PAGINA_VACIA = {
  content: [],
  totalElements: 0,
  totalPages: 0,
  number: 0,
  size: 15,
  first: true,
  last: true,
  empty: true,
};

describe('UsuarioSeleccionableApiService (selectores de solo lectura, distintos del CRUD de /api/usuarios)', () => {
  let service: UsuarioSeleccionableApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(UsuarioSeleccionableApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('buscarDuenios() y buscarVeterinarios() llaman a sus endpoints reales, PAGINADOS, nunca al CRUD completo de /api/usuarios', () => {
    service.buscarDuenios('Jaime', 0, 15).subscribe();
    const duenios = httpMock.expectOne(
      (r) => r.url === '/api/usuarios/duenios' && r.params.get('q') === 'Jaime' && r.params.get('page') === '0' && r.params.get('size') === '15'
    );
    expect(duenios.request.method).toBe('GET');
    duenios.flush(PAGINA_VACIA);

    service.buscarVeterinarios('vet', 0, 15).subscribe();
    const veterinarios = httpMock.expectOne(
      (r) => r.url === '/api/usuarios/veterinarios' && r.params.get('q') === 'vet'
    );
    expect(veterinarios.request.method).toBe('GET');
    veterinarios.flush(PAGINA_VACIA);
  });

  it('sin q, no envía el parámetro q (no fuerza texto vacío al backend)', () => {
    service.buscarDuenios(null, 0, 15).subscribe();
    const duenios = httpMock.expectOne((r) => r.url === '/api/usuarios/duenios');
    expect(duenios.request.params.has('q')).toBeFalse();
    duenios.flush(PAGINA_VACIA);
  });
});
