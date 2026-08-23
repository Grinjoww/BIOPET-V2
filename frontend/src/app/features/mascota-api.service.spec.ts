import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { MascotaApiService } from './mascota-api.service';

describe('MascotaApiService', () => {
  let service: MascotaApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(MascotaApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('listar() hace GET /api/mascotas con page/size/sort como query params', () => {
    service.listar(2, 10, 'nombre,asc').subscribe();

    const req = httpMock.expectOne(
      (r) => r.url === '/api/mascotas' && r.method === 'GET'
    );
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('10');
    expect(req.request.params.get('sort')).toBe('nombre,asc');
    req.flush({ content: [], totalElements: 0, totalPages: 0, number: 2, size: 10, first: false, last: true, empty: true });
  });

  it('crear() hace POST /api/mascotas con el payload tal cual', () => {
    const payload = { duenioId: 5, nombre: 'Firulais', especie: 'Perro', raza: 'Mestizo', fechaNacimiento: '2022-01-01' };
    service.crear(payload).subscribe();

    const req = httpMock.expectOne('/api/mascotas');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush({ id: 1, ...payload, duenioNombre: 'Ana', activo: true, creadoEn: '', actualizadoEn: '' });
  });

  it('resumenPorEspecies() agrega duenioId solo cuando se provee', () => {
    service.resumenPorEspecies(5).subscribe();
    const conDuenio = httpMock.expectOne((r) => r.url === '/api/mascotas/resumen-especies');
    expect(conDuenio.request.params.get('duenioId')).toBe('5');
    conDuenio.flush([]);

    service.resumenPorEspecies().subscribe();
    const sinDuenio = httpMock.expectOne((r) => r.url === '/api/mascotas/resumen-especies');
    expect(sinDuenio.request.params.has('duenioId')).toBeFalse();
    sinDuenio.flush([]);
  });
});
