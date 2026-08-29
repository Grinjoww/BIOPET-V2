import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { AuthService, UsuarioResponse } from '../core/auth.service';
import { FacturasComponent } from './facturas.component';
import { Factura } from './factura-api.service';
import { PageResponse } from './mascota-api.service';

function usuario(rol: UsuarioResponse['rol']): UsuarioResponse {
  return { id: 1, nombre: 'Test', email: 't@biopet.com', rol, activo: true };
}

function factura(overrides: Partial<Factura> = {}): Factura {
  return {
    id: 7,
    estado: 'EMITIDA',
    usuarioId: 3,
    ambiente: 'PRUEBAS',
    establecimiento: '001',
    puntoEmision: '001',
    secuencial: 42,
    codigoNumerico: '12345678',
    claveAcceso: 'clave-ficticia',
    fechaEmision: '2026-09-01',
    compradorTipoIdentificacion: 'CEDULA',
    compradorIdentificacion: '0000000000',
    compradorRazonSocial: 'Ana Dueña',
    compradorDireccion: null,
    compradorEmail: null,
    compradorTelefono: null,
    mascotaId: 5,
    mascotaNombre: 'Firulais',
    detalles: [],
    pagos: [],
    totalSinImpuestos: 20,
    totalDescuento: 0,
    totalImpuestos: 3,
    importeTotal: 23,
    moneda: 'USD',
    estadoRecepcion: 'RECIBIDA',
    estadoAutorizacion: null,
    numeroAutorizacion: null,
    fechaAutorizacion: null,
    proximoIntentoEn: null,
    intentosAutorizacion: null,
    documentosDisponibles: ['XML_GENERADO'],
    creadoEn: '2026-09-01T00:00:00Z',
    actualizadoEn: '2026-09-01T00:00:00Z',
    ...overrides,
  };
}

function paginaCon(facturas: Factura[], extra: Partial<PageResponse<Factura>> = {}): PageResponse<Factura> {
  return {
    content: facturas,
    totalElements: facturas.length,
    totalPages: facturas.length > 0 ? 1 : 0,
    number: 0,
    size: 10,
    first: true,
    last: true,
    empty: facturas.length === 0,
    ...extra,
  };
}

describe('FacturasComponent (integración ligera: TestBed + HttpTestingController + Router real)', () => {
  let fixture: ComponentFixture<FacturasComponent>;
  let httpMock: HttpTestingController;
  let auth: AuthService;

  function crear(rol: UsuarioResponse['rol']) {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [FacturasComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    fixture = TestBed.createComponent(FacturasComponent);
    auth = TestBed.inject(AuthService);
    auth.usuarioActual.set(usuario(rol));
    httpMock = TestBed.inject(HttpTestingController);
  }

  afterEach(() => httpMock.verify());

  it('carga inicial: pinta número, comprador, mascota, total y estado reales', () => {
    crear('ROLE_ADMIN');
    fixture.detectChanges();

    const req = httpMock.expectOne((r) => r.url === '/api/facturas' && r.method === 'GET');
    req.flush(paginaCon([factura()], { totalPages: 2, totalElements: 15 }));
    fixture.detectChanges();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('001-001-000000042');
    expect(texto).toContain('Ana Dueña');
    expect(texto).toContain('Firulais');
    expect(texto).toContain('23.00 USD');
    expect(texto).toContain('Emitida');
    expect(texto).toContain('Recibida');
    expect(texto).toContain('Página 1 de 2 (15 facturas)');

    const enlace: HTMLAnchorElement = fixture.nativeElement.querySelector('a.record-link');
    expect(enlace.getAttribute('href')).toBe('/facturas/7');
  });

  it('empty state real: sin facturas muestra el mensaje vacío, sin datos inventados', () => {
    crear('ROLE_ADMIN');
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === '/api/facturas').flush(paginaCon([]));
    fixture.detectChanges();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Sin facturas todavía');
    expect(fixture.nativeElement.querySelectorAll('tbody tr').length).toBe(1);
  });

  it('ADMIN/AUXILIAR ven "Nueva factura"; VETERINARIO/DUENO no', () => {
    crear('ROLE_ADMIN');
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === '/api/facturas').flush(paginaCon([]));
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Nueva factura');

    crear('ROLE_VETERINARIO');
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === '/api/facturas').flush(paginaCon([]));
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Nueva factura');

    crear('ROLE_DUENO');
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === '/api/facturas').flush(paginaCon([]));
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Nueva factura');
  });

  it('un error del backend se muestra tal cual lo traduce ProblemDetailService, nunca un total o listado inventado', () => {
    crear('ROLE_DUENO');
    fixture.detectChanges();
    httpMock
      .expectOne((r) => r.url === '/api/facturas')
      .flush({ detail: 'No tienes permiso.' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('No tienes permiso.');
    expect(fixture.nativeElement.querySelectorAll('tbody tr').length).toBe(0);
  });
});
