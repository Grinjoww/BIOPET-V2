import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { UsuarioSeleccionableApiService } from './usuario-seleccionable-api.service';

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

  it('listarDuenios() y listarVeterinarios() llaman a sus endpoints reales, nunca al CRUD completo de /api/usuarios', () => {
    service.listarDuenios().subscribe();
    const duenios = httpMock.expectOne('/api/usuarios/duenios');
    expect(duenios.request.method).toBe('GET');
    duenios.flush([]);

    service.listarVeterinarios().subscribe();
    const veterinarios = httpMock.expectOne('/api/usuarios/veterinarios');
    expect(veterinarios.request.method).toBe('GET');
    veterinarios.flush([]);
  });
});
