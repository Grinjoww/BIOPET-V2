import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { FacturaNuevaComponent } from './factura-nueva.component';

describe('FacturaNuevaComponent (integración ligera: TestBed + HttpTestingController)', () => {
  let fixture: ComponentFixture<FacturaNuevaComponent>;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [FacturaNuevaComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    fixture = TestBed.createComponent(FacturaNuevaComponent);
  });

  afterEach(() => httpMock.verify());

  function flushCargaInicial() {
    httpMock.expectOne((r) => r.url === '/api/usuarios/duenios').flush([{ id: 3, nombre: 'Ana Dueña', email: 'ana@biopet.test', rol: 'ROLE_DUENO' }]);
    httpMock
      .expectOne((r) => r.url === '/api/mascotas')
      .flush({ content: [{ id: 5, duenioId: 3, duenioNombre: 'Ana Dueña', nombre: 'Firulais', especie: 'Perro', raza: 'Mestizo', fechaNacimiento: '2022-01-01', activo: true, creadoEn: '', actualizadoEn: '' }], totalElements: 1, totalPages: 1, number: 0, size: 200, first: true, last: true, empty: false });
    httpMock
      .expectOne((r) => r.url === '/api/facturacion/conceptos')
      .flush([{ id: 9, codigo: 'CPT-1', descripcion: 'Consulta general', tipo: 'CONSULTA', precioUnitario: 20, codigoImpuesto: 'IVA', codigoPorcentaje: '4', activo: true }]);
  }

  it('carga sus 3 catálogos reales al iniciar: dueños, mascotas y conceptos activos (nunca IDs escritos a mano)', () => {
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();

    const reqConceptos = httpMock.expectOne((r) => r.url === '/api/facturacion/conceptos');
    expect(reqConceptos.request.params.get('activo')).toBe('true');
    reqConceptos.flush([]);
    httpMock.expectOne((r) => r.url === '/api/usuarios/duenios').flush([]);
    httpMock.expectOne((r) => r.url === '/api/mascotas').flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 200, first: true, last: true, empty: true });
  });

  it('crear borrador envía SOLO usuarioId/mascotaId/fechaEmision — nunca un precio, impuesto, ambiente o secuencial', () => {
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    flushCargaInicial();
    fixture.detectChanges();

    const component = fixture.componentInstance;
    component.datosGeneralesForm.setValue({ usuarioId: 3, mascotaId: 5, fechaEmision: '2026-09-01' });
    component.crearBorrador();

    const req = httpMock.expectOne((r) => r.url === '/api/facturas' && r.method === 'POST');
    expect(req.request.body).toEqual({ usuarioId: 3, mascotaId: 5, fechaEmision: '2026-09-01' });
    expect(Object.keys(req.request.body)).toEqual(['usuarioId', 'mascotaId', 'fechaEmision']);

    req.flush({
      id: 42,
      estado: 'BORRADOR',
      usuarioId: 3,
      ambiente: null,
      establecimiento: null,
      puntoEmision: null,
      secuencial: null,
      codigoNumerico: null,
      claveAcceso: null,
      fechaEmision: '2026-09-01',
      compradorTipoIdentificacion: null,
      compradorIdentificacion: null,
      compradorRazonSocial: null,
      compradorDireccion: null,
      compradorEmail: null,
      compradorTelefono: null,
      mascotaId: 5,
      mascotaNombre: 'Firulais',
      detalles: [],
      pagos: [],
      totalSinImpuestos: null,
      totalDescuento: null,
      totalImpuestos: null,
      importeTotal: null,
      moneda: null,
      estadoRecepcion: null,
      estadoAutorizacion: null,
      numeroAutorizacion: null,
      fechaAutorizacion: null,
      proximoIntentoEn: null,
      intentosAutorizacion: null,
      documentosDisponibles: [],
      creadoEn: '2026-09-01T00:00:00Z',
      actualizadoEn: '2026-09-01T00:00:00Z',
    });

    httpMock.expectOne((r) => r.url === '/api/usuarios/3/datos-facturacion').flush([]);
    fixture.detectChanges();

    expect(fixture.componentInstance.factura()?.id).toBe(42);
  });

  it('guardar líneas envía SOLO conceptoFacturableId/cantidad/descuento/origen — nunca un precio ni un impuesto calculado en el cliente', () => {
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    flushCargaInicial();
    fixture.detectChanges();

    const component = fixture.componentInstance;
    component.factura.set({
      id: 42,
      estado: 'BORRADOR',
      detalles: [],
      pagos: [],
      documentosDisponibles: [],
    } as any);
    component.conceptoSeleccionado = { id: 9, codigo: 'CPT-1', descripcion: 'Consulta general', tipo: 'CONSULTA', precioUnitario: 20, codigoImpuesto: 'IVA', codigoPorcentaje: '4', activo: true };
    component.cantidadNueva = 2;
    component.agregarLinea();
    component.guardarDetalles();

    const req = httpMock.expectOne((r) => r.url === '/api/facturas/42/detalles' && r.method === 'PUT');
    expect(req.request.body).toEqual({
      detalles: [{ conceptoFacturableId: 9, cantidad: 2, descuento: null, origenTipo: null, origenId: null }],
    });
  });
});
