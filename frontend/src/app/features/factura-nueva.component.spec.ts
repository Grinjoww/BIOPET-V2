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

  /**
   * Auditoría de usabilidad con datos masivos: dueño y mascota YA NO se
   * cargan de golpe al iniciar (antes: ~1.800 dueños y 200/10.000 mascotas
   * de una sola vez) — ahora son selectores buscables (GET .../duenios y
   * GET /api/mascotas con `q`, bajo demanda). Solo conceptos sigue siendo
   * un catálogo eager: es acotado (activo=true) y se necesita completo.
   */
  function flushCargaInicial() {
    httpMock
      .expectOne((r) => r.url === '/api/facturacion/conceptos')
      .flush([{ id: 9, codigo: 'CPT-1', descripcion: 'Consulta general', tipo: 'CONSULTA', precioUnitario: 20, codigoImpuesto: 'IVA', codigoPorcentaje: '4', activo: true }]);
  }

  it('carga el catálogo de conceptos activos al iniciar; dueño y mascota NO se cargan de golpe (son buscables)', () => {
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();

    const reqConceptos = httpMock.expectOne((r) => r.url === '/api/facturacion/conceptos');
    expect(reqConceptos.request.params.get('activo')).toBe('true');
    reqConceptos.flush([]);
    httpMock.expectNone((r) => r.url === '/api/usuarios/duenios');
    httpMock.expectNone((r) => r.url === '/api/mascotas');
  });

  it('selector de dueño busca por texto (mín. 2 caracteres) contra GET /api/usuarios/duenios?q=...&size=15 — nunca trae todos', () => {
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    flushCargaInicial();

    const component = fixture.componentInstance;
    component.buscarDuenios('Jaime').subscribe((opciones) => {
      expect(opciones).toEqual([{ id: 3, principal: 'Ana Dueña', secundario: 'ana@biopet.test' }]);
    });

    const req = httpMock.expectOne(
      (r) => r.url === '/api/usuarios/duenios' && r.params.get('q') === 'Jaime' && r.params.get('size') === '15'
    );
    req.flush({
      content: [{ id: 3, nombre: 'Ana Dueña', email: 'ana@biopet.test', rol: 'ROLE_DUENO' }],
      totalElements: 1, totalPages: 1, number: 0, size: 15, first: true, last: true, empty: false,
    });
  });

  it('selector de mascota queda deshabilitado sin dueño, y busca acotado a duenioId cuando ya hay uno elegido', () => {
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    flushCargaInicial();
    fixture.detectChanges();

    const component = fixture.componentInstance;
    let sinResultados: unknown[] | undefined;
    component.buscarMascotasDelDuenio('firu').subscribe((r) => (sinResultados = r));
    expect(sinResultados).toEqual([]); // sin dueño elegido: no busca nada (no GET disparado)
    httpMock.expectNone((r) => r.url === '/api/mascotas');

    component.onDuenioSeleccionado({ id: 3, principal: 'Ana Dueña', secundario: 'ana@biopet.test' });
    component.buscarMascotasDelDuenio('firu').subscribe();

    const req = httpMock.expectOne(
      (r) => r.url === '/api/mascotas' && r.params.get('q') === 'firu' && r.params.get('duenioId') === '3'
    );
    req.flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 15, first: true, last: true, empty: true });
  });

  it('cambiar de dueño limpia la mascota ya elegida (campo dependiente no debe quedar con un valor obsoleto)', () => {
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    flushCargaInicial();
    fixture.detectChanges();

    const component = fixture.componentInstance;
    component.onDuenioSeleccionado({ id: 3, principal: 'Ana Dueña' });
    component.onMascotaSeleccionada({ id: 5, principal: 'Firulais' });
    expect(component.datosGeneralesForm.value.mascotaId).toBe(5);

    component.onDuenioSeleccionado({ id: 9, principal: 'Beto Dueño' });
    expect(component.datosGeneralesForm.value.mascotaId).toBeNull();
  });

  it('Consumidor final: NO exige escribir identificación/razón social — usa el estándar del SRI y sigue siendo válido', () => {
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    flushCargaInicial();
    fixture.detectChanges();

    const component = fixture.componentInstance;
    expect(component.esConsumidorFinal()).toBeFalse();
    component.compradorForm.patchValue({ tipoIdentificacion: 'CONSUMIDOR_FINAL' });

    expect(component.esConsumidorFinal()).toBeTrue();
    expect(component.compradorForm.get('identificacion')!.disabled).toBeTrue();
    expect(component.compradorForm.get('razonSocial')!.disabled).toBeTrue();
    expect(component.compradorForm.invalid).toBeFalse(); // los controles disabled no bloquean la validez del grupo

    const v = component.compradorForm.getRawValue(); // getRawValue SÍ incluye los disabled
    expect(v.identificacion).toBe('9999999999999');
    expect(v.razonSocial).toBe('CONSUMIDOR FINAL');

    // Volver a un tipo normal reactiva los campos y limpia el autocompletado.
    component.compradorForm.patchValue({ tipoIdentificacion: 'CEDULA' });
    expect(component.compradorForm.get('identificacion')!.disabled).toBeFalse();
    expect(component.compradorForm.get('identificacion')!.value).toBe('');
    expect(component.compradorForm.get('razonSocial')!.value).toBe('');
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
