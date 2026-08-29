import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { FacturacionConfigComponent } from './facturacion-config.component';

/**
 * Acceso a esta pantalla (solo ADMIN) ya lo cubre role.guard.spec.ts de
 * forma genérica -es exactamente el mismo guard que protege /usuarios-; aquí
 * solo se prueba el RENDER real de la pantalla, no la seguridad de la ruta.
 */
describe('FacturacionConfigComponent (integración ligera: TestBed + HttpTestingController)', () => {
  let fixture: ComponentFixture<FacturacionConfigComponent>;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [FacturacionConfigComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    fixture = TestBed.createComponent(FacturacionConfigComponent);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  function flushCargaInicial(emisorStatus: 200 | 404 = 404) {
    const reqEmisor = httpMock.expectOne((r) => r.url === '/api/facturacion/emisor');
    if (emisorStatus === 404) {
      reqEmisor.flush({ detail: 'Todavia no configurado' }, { status: 404, statusText: 'Not Found' });
    } else {
      reqEmisor.flush({ id: 1, ruc: '0900000000001', razonSocial: 'BIOPET', nombreComercial: null, direccionMatriz: 'Dir', obligadoContabilidad: true, contribuyenteEspecial: null, rimpe: false, agenteRetencionResolucion: null, activo: true });
    }
    httpMock.expectOne((r) => r.url === '/api/facturacion/puntos-emision').flush([]);
    httpMock.expectOne((r) => r.url === '/api/facturacion/conceptos').flush([]);
    httpMock.expectOne((r) => r.url === '/api/facturacion/tarifas').flush([]);
  }

  it('sin emisor configurado (404): muestra el estado "todavía no configurado", no un error genérico', () => {
    fixture.detectChanges();
    flushCargaInicial(404);
    fixture.detectChanges();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Todavía no se ha configurado el emisor fiscal');
    expect(texto).not.toContain('No se pudo completar la operación');
  });

  it('con emisor configurado: pinta el RUC y permite navegar a las otras 3 pestañas', () => {
    fixture.detectChanges();
    flushCargaInicial(200);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('0900000000001');

    const tabPuntos: HTMLButtonElement = fixture.nativeElement.querySelector('#tab-puntos');
    tabPuntos.click();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Nuevo punto de emisión');

    const tabTarifas: HTMLButtonElement = fixture.nativeElement.querySelector('#tab-tarifas');
    tabTarifas.click();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Nueva vigencia');
  });
});
