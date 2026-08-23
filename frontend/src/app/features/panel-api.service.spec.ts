import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { PanelApiService } from './panel-api.service';

describe('PanelApiService', () => {
  let service: PanelApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PanelApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('resumen() hace GET /api/dashboard/resumen con desde/hasta como query params', () => {
    service.resumen('2026-05-01', '2026-05-31').subscribe();

    const req = httpMock.expectOne((r) => r.url === '/api/dashboard/resumen' && r.method === 'GET');
    expect(req.request.params.get('desde')).toBe('2026-05-01');
    expect(req.request.params.get('hasta')).toBe('2026-05-31');
    req.flush({
      desde: '2026-05-01',
      hasta: '2026-05-31',
      mascotasActivas: 0,
      citasProgramadas: 0,
      consultasEnRango: 0,
      vacunasEnRango: 0,
      mascotasSinConsulta: 0,
    });
  });
});
