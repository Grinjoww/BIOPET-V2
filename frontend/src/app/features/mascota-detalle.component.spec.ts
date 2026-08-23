import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { ActivatedRoute, convertToParamMap } from '@angular/router';

import { MascotaDetalleComponent } from './mascota-detalle.component';
import { Mascota, PageResponse } from './mascota-api.service';
import { Vacuna } from './vacuna-api.service';
import { Cita } from './cita-api.service';
import { Consulta } from './consulta-api.service';

function mascota(overrides: Partial<Mascota> = {}): Mascota {
  return {
    id: 7,
    duenioId: 3,
    duenioNombre: 'Ana Dueña',
    nombre: 'Firulais',
    especie: 'Perro',
    raza: 'Mestizo',
    fechaNacimiento: '2023-06-15',
    activo: true,
    creadoEn: '2026-01-01T00:00:00Z',
    actualizadoEn: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

function paginaVacia<T>(): PageResponse<T> {
  return { content: [], totalElements: 0, totalPages: 0, number: 0, size: 10, first: true, last: true, empty: true };
}

function paginaCon<T>(items: T[]): PageResponse<T> {
  return { content: items, totalElements: items.length, totalPages: 1, number: 0, size: 10, first: true, last: true, empty: items.length === 0 };
}

describe('MascotaDetalleComponent (integración ligera: /mascotas/:id, solo lectura)', () => {
  let fixture: ComponentFixture<MascotaDetalleComponent>;
  let component: MascotaDetalleComponent;
  let httpMock: HttpTestingController;

  function crear(id = '7') {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [MascotaDetalleComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id }) } } },
      ],
    });
    fixture = TestBed.createComponent(MascotaDetalleComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  }

  afterEach(() => httpMock.verify());

  it('carga la cabecera real: nombre, expediente, especie, raza, fecha, edad, dueño, y fallback de iniciales sin foto', () => {
    jasmine.clock().install();
    jasmine.clock().mockDate(new Date(2026, 5, 15));
    try {
      crear('7');
      fixture.detectChanges(); // ngOnInit -> GET /api/mascotas/7

      httpMock.expectOne((r) => r.url === '/api/mascotas/7' && r.method === 'GET').flush(mascota());
      httpMock.expectOne((r) => r.url === '/api/vacunas/mascota/7').flush(paginaVacia<Vacuna>());
      fixture.detectChanges();

      const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
      expect(texto).toContain('Firulais');
      expect(texto).toContain('EXP-000007');
      expect(texto).toContain('Perro');
      expect(texto).toContain('Mestizo');
      expect(texto).toContain('3 años'); // calcularEdad, reloj fijado
      expect(texto).toContain('Ana Dueña');

      expect(fixture.nativeElement.querySelector('img')).toBeFalsy(); // sin foto: nunca un <img>
      expect(fixture.nativeElement.querySelector('.record-header__avatar').textContent.trim()).toBe('F'); // inicialesDe
    } finally {
      jasmine.clock().uninstall();
    }
  });

  it('404: muestra un error legible, el enlace real "Volver a Mascotas", y no intenta pintar tabs', () => {
    crear('999');
    fixture.detectChanges();

    httpMock.expectOne((r) => r.url === '/api/mascotas/999').flush(
      { type: 'urn:biopet:error:not-found', title: 'Recurso no encontrado', status: 404, detail: 'El recurso solicitado no existe.', instance: '/api/mascotas/999' },
      { status: 404, statusText: 'Not Found' }
    );
    fixture.detectChanges();

    expect(component.error()).toContain('no existe');
    const enlace: HTMLAnchorElement = fixture.nativeElement.querySelector('a.btn--secondary');
    expect(enlace.getAttribute('href')).toBe('/mascotas');
    expect(fixture.nativeElement.querySelector('[role="tablist"]')).toBeFalsy();
  });

  it('lazy tabs: Vacunas se carga de inmediato (pestaña por defecto), pero Citas/Consultas/Especie NO se disparan hasta activarlas', () => {
    crear('7');
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === '/api/mascotas/7').flush(mascota());

    // Vacunas: única de las 4 que carga automáticamente junto a la cabecera.
    httpMock.expectOne((r) => r.url === '/api/vacunas/mascota/7').flush(paginaVacia<Vacuna>());
    fixture.detectChanges();

    expect(() => httpMock.expectNone((r) => r.url === '/api/citas/mascota/7')).not.toThrow();
    expect(() => httpMock.expectNone((r) => r.url === '/api/consultas/mascota/7')).not.toThrow();
    expect(() => httpMock.expectNone((r) => r.url === '/api/externa/especies')).not.toThrow();
  });

  it('tab Vacunas: no se repite la petición al salir y volver a entrar (ya cargada junto a la cabecera)', () => {
    crear('7');
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === '/api/mascotas/7').flush(mascota());
    httpMock.expectOne((r) => r.url === '/api/vacunas/mascota/7').flush(paginaVacia<Vacuna>());
    fixture.detectChanges();

    component.irATab('citas');
    httpMock.expectOne((r) => r.url === '/api/citas/mascota/7').flush(paginaVacia<Cita>());
    fixture.detectChanges();

    component.irATab('vacunas');
    fixture.detectChanges();

    expect(() => httpMock.expectNone((r) => r.url === '/api/vacunas/mascota/7')).not.toThrow();
  });

  it('tab Citas: carga PROGRAMADA/COMPLETADA/CANCELADA con sus chips, y solo una vez', () => {
    crear('7');
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === '/api/mascotas/7').flush(mascota());
    httpMock.expectOne((r) => r.url === '/api/vacunas/mascota/7').flush(paginaVacia<Vacuna>());
    fixture.detectChanges();

    component.irATab('citas');
    const citas: Cita[] = [
      { id: 1, mascotaId: 7, mascotaNombre: 'Firulais', veterinarioId: 2, veterinarioNombre: 'Dra. Pérez', fechaHora: '2026-06-20T10:00:00Z', estado: 'PROGRAMADA', motivo: 'Control', activo: true, creadoEn: '', actualizadoEn: '' },
      { id: 2, mascotaId: 7, mascotaNombre: 'Firulais', veterinarioId: 2, veterinarioNombre: 'Dra. Pérez', fechaHora: '2026-05-01T10:00:00Z', estado: 'COMPLETADA', motivo: 'Vacunación', activo: true, creadoEn: '', actualizadoEn: '' },
      { id: 3, mascotaId: 7, mascotaNombre: 'Firulais', veterinarioId: 2, veterinarioNombre: 'Dra. Pérez', fechaHora: '2026-04-01T10:00:00Z', estado: 'CANCELADA', motivo: 'Emergencia', activo: true, creadoEn: '', actualizadoEn: '' },
    ];
    httpMock.expectOne((r) => r.url === '/api/citas/mascota/7').flush(paginaCon(citas));
    fixture.detectChanges();

    const filas = fixture.nativeElement.querySelectorAll('#panel-citas tbody tr');
    expect(filas.length).toBe(3);
    expect(fixture.nativeElement.querySelector('#panel-citas').textContent).toContain('Control');
    expect(fixture.nativeElement.querySelector('#panel-citas').textContent).toContain('Vacunación');
    expect(fixture.nativeElement.querySelector('#panel-citas').textContent).toContain('Emergencia');

    // Sin acciones de escritura: la ficha es de solo lectura en este contexto.
    expect(fixture.nativeElement.querySelector('#panel-citas .actions')).toBeFalsy();

    component.irATab('vacunas');
    component.irATab('citas');
    httpMock.expectNone((r) => r.url === '/api/citas/mascota/7');
  });

  it('tab Consultas: renderiza registros clínicos con fallback "No registrado"/"No registradas" para nulls', () => {
    crear('7');
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === '/api/mascotas/7').flush(mascota());
    httpMock.expectOne((r) => r.url === '/api/vacunas/mascota/7').flush(paginaVacia<Vacuna>());
    fixture.detectChanges();

    component.irATab('consultas');
    const consulta: Consulta = {
      id: 1, mascotaId: 7, mascotaNombre: 'Firulais', veterinarioId: 2, veterinarioNombre: 'Dra. Pérez',
      fechaConsulta: '2026-05-01T10:00:00Z', motivo: 'Chequeo', diagnostico: null, tratamiento: null, observaciones: null,
      activo: true, creadoEn: '', actualizadoEn: '',
    };
    httpMock.expectOne((r) => r.url === '/api/consultas/mascota/7').flush(paginaCon([consulta]));
    fixture.detectChanges();

    const texto = fixture.nativeElement.querySelector('#panel-consultas').textContent;
    expect(texto).toContain('Chequeo');
    expect(texto).toContain('Dra. Pérez');
    expect(texto).toContain('No registrado');
    expect(texto).toContain('No registradas');
  });

  it('tab Especie: éxito muestra nombre científico/hábitat/dieta/origen reales', () => {
    crear('7');
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === '/api/mascotas/7').flush(mascota());
    httpMock.expectOne((r) => r.url === '/api/vacunas/mascota/7').flush(paginaVacia<Vacuna>());
    fixture.detectChanges();

    component.irATab('especie');
    const req = httpMock.expectOne((r) => r.url === '/api/externa/especies');
    expect(req.request.params.get('especie')).toBe('Perro');
    req.flush({
      especieConsultada: 'Perro',
      nombreCientifico: 'Canis lupus familiaris',
      habitat: 'Doméstico',
      dieta: 'Omnívoro',
      origen: 'api-ninjas',
      consultadoEn: '2026-06-15T10:00:00Z',
    });
    fixture.detectChanges();

    const texto = fixture.nativeElement.querySelector('#panel-especie').textContent;
    expect(texto).toContain('Canis lupus familiaris');
    expect(texto).toContain('Doméstico');
    expect(texto).toContain('Omnívoro');
    expect(texto).toContain('api-ninjas');
  });

  it('tab Especie: un 502 degrada a "no disponible" sin romper la ficha ni filtrar detalles técnicos', () => {
    crear('7');
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === '/api/mascotas/7').flush(mascota());
    httpMock.expectOne((r) => r.url === '/api/vacunas/mascota/7').flush(paginaVacia<Vacuna>());
    fixture.detectChanges();

    component.irATab('especie');
    httpMock.expectOne((r) => r.url === '/api/externa/especies').flush(
      { type: 'urn:biopet:error:bad-gateway', title: 'Servicio externo no disponible', status: 502, detail: 'No se pudo obtener información de la especie en este momento.', instance: '/api/externa/especies' },
      { status: 502, statusText: 'Bad Gateway' }
    );
    fixture.detectChanges();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('no disponible');
    expect(texto).not.toContain('502');
    expect(texto).not.toContain('ExternalApiException');
    // El resto de la ficha (cabecera) sigue intacto.
    expect(texto).toContain('Firulais');
  });

  it('ROLE_DUENO / cualquier rol: la ficha no tiene ningún control de escritura clínica (es de solo lectura), y las tabs siguen accesibles', () => {
    crear('7');
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === '/api/mascotas/7').flush(mascota());
    httpMock.expectOne((r) => r.url === '/api/vacunas/mascota/7').flush(paginaVacia<Vacuna>());
    fixture.detectChanges();

    // Ningún botón de crear/editar/eliminar en toda la ficha: el componente
    // ni siquiera inyecta AuthService, es de solo lectura para cualquier rol.
    expect(fixture.nativeElement.querySelectorAll('.btn--primary, .btn-icon--danger').length).toBe(0);
    expect(fixture.nativeElement.querySelectorAll('[role="tab"]').length).toBe(4);
  });
});
