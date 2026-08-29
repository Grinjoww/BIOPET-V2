import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, ActivatedRoute, Router, convertToParamMap } from '@angular/router';

import { AuthService, UsuarioResponse } from '../core/auth.service';
import { FacturaDetalleComponent } from './factura-detalle.component';
import { Factura } from './factura-api.service';

function usuario(rol: UsuarioResponse['rol']): UsuarioResponse {
  return { id: 1, nombre: 'Test', email: 't@biopet.com', rol, activo: true };
}

function factura(overrides: Partial<Factura> = {}): Factura {
  return {
    id: 7,
    estado: 'BORRADOR',
    usuarioId: 3,
    ambiente: null,
    establecimiento: null,
    puntoEmision: null,
    secuencial: null,
    codigoNumerico: null,
    claveAcceso: null,
    fechaEmision: '2026-09-01',
    compradorTipoIdentificacion: 'CEDULA',
    compradorIdentificacion: '0000000000',
    compradorRazonSocial: 'Ana Dueña',
    compradorDireccion: 'Dirección ficticia',
    compradorEmail: 'ana@biopet.test',
    compradorTelefono: '0999999999',
    mascotaId: 5,
    mascotaNombre: 'Firulais',
    detalles: [
      {
        linea: 1,
        conceptoFacturableId: 9,
        codigoPrincipal: 'CPT-1',
        descripcion: 'Consulta general',
        cantidad: 1,
        precioUnitario: 20,
        descuento: 0,
        precioTotalSinImpuesto: 20,
        impuestoCodigo: 'IVA',
        impuestoCodigoPorcentaje: '4',
        impuestoTarifa: 15,
        baseImponible: 20,
        impuestoValor: 3,
        origenTipo: null,
        origenId: null,
      },
    ],
    pagos: [{ formaPago: 'TARJETA_DEBITO', total: 23, plazo: null, unidadTiempo: null }],
    totalSinImpuestos: 20,
    totalDescuento: 0,
    totalImpuestos: 3,
    importeTotal: 23,
    moneda: 'USD',
    estadoRecepcion: null,
    estadoAutorizacion: null,
    numeroAutorizacion: null,
    fechaAutorizacion: null,
    proximoIntentoEn: null,
    intentosAutorizacion: null,
    documentosDisponibles: [],
    creadoEn: '2026-09-01T00:00:00Z',
    actualizadoEn: '2026-09-01T00:00:00Z',
    ...overrides,
  };
}

describe('FacturaDetalleComponent (integración ligera: TestBed + HttpTestingController + Router real)', () => {
  let fixture: ComponentFixture<FacturaDetalleComponent>;
  let httpMock: HttpTestingController;
  let auth: AuthService;

  function crear(rol: UsuarioResponse['rol']) {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [FacturaDetalleComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: '7' }) } } },
      ],
    });
    fixture = TestBed.createComponent(FacturaDetalleComponent);
    auth = TestBed.inject(AuthService);
    auth.usuarioActual.set(usuario(rol));
    httpMock = TestBed.inject(HttpTestingController);
  }

  function cargar(rol: UsuarioResponse['rol'], f: Factura) {
    crear(rol);
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === '/api/facturas/7' && r.method === 'GET').flush(f);
    fixture.detectChanges();
  }

  afterEach(() => httpMock.verify());

  it('pinta cabecera, comprador, mascota, detalles, totales y pagos reales', () => {
    cargar('ROLE_ADMIN', factura());
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';

    expect(texto).toContain('Ana Dueña');
    expect(texto).toContain('Firulais');
    expect(texto).toContain('Consulta general');
    expect(texto).toContain('23.00 USD');
    expect(texto).toContain('Tarjeta de débito');
    expect(texto).toContain('Borrador');
  });

  it('ADMIN/AUXILIAR ven la sección de acciones fiscales; VETERINARIO y DUENO no', () => {
    cargar('ROLE_ADMIN', factura({ estado: 'EMITIDA' }));
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Acciones fiscales');

    cargar('ROLE_VETERINARIO', factura({ estado: 'EMITIDA' }));
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Acciones fiscales');

    cargar('ROLE_DUENO', factura({ estado: 'AUTORIZADA' }));
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Acciones fiscales');
  });

  it('DUENO solo ve el botón de descarga XML_AUTORIZADO aunque el backend listara más tipos', () => {
    cargar('ROLE_DUENO', factura({ estado: 'AUTORIZADA', documentosDisponibles: ['XML_GENERADO', 'XML_FIRMADO', 'XML_AUTORIZADO'] }));
    const botones = Array.from(fixture.nativeElement.querySelectorAll('button')).map((b: any) => b.textContent.trim());
    expect(botones.some((t: string) => t.includes('XML autorizado'))).toBeTrue();
    expect(botones.some((t: string) => t.includes('XML generado'))).toBeFalse();
    expect(botones.some((t: string) => t.includes('XML firmado'))).toBeFalse();
  });

  it('Emitir: pide punto de emisión, envía SOLO puntoEmisionId (sin ambiente) y refresca la factura con la respuesta real', () => {
    cargar('ROLE_ADMIN', factura({ estado: 'BORRADOR' }));

    const botonEmitir = Array.from(fixture.nativeElement.querySelectorAll('button')).find(
      (b: any) => b.textContent.trim() === 'Emitir'
    ) as HTMLButtonElement;
    botonEmitir.click();
    fixture.detectChanges();

    httpMock
      .expectOne((r) => r.url === '/api/facturacion/puntos-emision')
      .flush([{ id: 11, emisorFiscalId: 1, establecimiento: '001', puntoEmision: '001', direccionEstablecimiento: null, activo: true }]);
    fixture.detectChanges();

    (fixture.componentInstance as any).puntoEmisionSeleccionado = 11;
    fixture.detectChanges();
    const botonConfirmar = Array.from(fixture.nativeElement.querySelectorAll('button')).find(
      (b: any) => b.textContent.includes('Sí, emitir factura')
    ) as HTMLButtonElement;
    botonConfirmar.click();
    fixture.detectChanges();

    const req = httpMock.expectOne((r) => r.url === '/api/facturas/7/emitir' && r.method === 'POST');
    expect(req.request.body).toEqual({ puntoEmisionId: 11 });
    expect(req.request.body.ambiente).toBeUndefined();
    req.flush(factura({ estado: 'EMITIDA', establecimiento: '001', puntoEmision: '001', secuencial: 1 }));
    fixture.detectChanges();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Factura emitida correctamente.');
    expect(texto).toContain('001-001-000000001');
  });

  it('un 409 al firmar se muestra tal cual el detail del backend, sin que el componente cambie el estado localmente', () => {
    cargar('ROLE_ADMIN', factura({ estado: 'EMITIDA', claveAcceso: 'clave-ficticia', documentosDisponibles: ['XML_GENERADO'] }));

    const botonFirmar = Array.from(fixture.nativeElement.querySelectorAll('button')).find(
      (b: any) => b.textContent.trim() === 'Firmar'
    ) as HTMLButtonElement;
    botonFirmar.click();
    fixture.detectChanges();

    httpMock
      .expectOne((r) => r.url === '/api/facturas/7/firmar')
      .flush({ detail: 'La factura no está en un estado que permita firmar.' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('La factura no está en un estado que permita firmar.');
    expect((fixture.componentInstance as any).factura().estado).toBe('EMITIDA');
  });

  it('un 502 del SRI al enviar nunca se muestra como "rechazada": se pinta el detail real del backend', () => {
    cargar('ROLE_ADMIN', factura({ estado: 'EMITIDA', claveAcceso: 'clave-ficticia', documentosDisponibles: ['XML_GENERADO', 'XML_FIRMADO'] }));

    const botonEnviar = Array.from(fixture.nativeElement.querySelectorAll('button')).find(
      (b: any) => b.textContent.trim() === 'Enviar al SRI'
    ) as HTMLButtonElement;
    botonEnviar.click();
    fixture.detectChanges();

    const botonConfirmar = Array.from(fixture.nativeElement.querySelectorAll('button')).find(
      (b: any) => b.textContent.includes('Sí, enviar al SRI')
    ) as HTMLButtonElement;
    botonConfirmar.click();
    fixture.detectChanges();

    httpMock.expectOne((r) => r.url === '/api/facturas/7/enviar-sri').flush(
      { detail: 'No se pudo completar la comunicación con el SRI. La factura conserva su numeración y puede reintentarse más tarde.' },
      { status: 502, statusText: 'Bad Gateway' }
    );
    fixture.detectChanges();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('conserva su numeración');
    expect(texto.toLowerCase()).not.toContain('rechazada');
  });

  // ==================================================================
  // Corrección pre-commit: gating real por estado + documentosDisponibles
  // ==================================================================

  function accionesVisibles(): string[] {
    const seccion = fixture.nativeElement.querySelector('section[aria-labelledby="acciones-titulo"]');
    if (!seccion) return [];
    return Array.from(seccion.querySelectorAll('.toolbar button')).map((b: any) => b.textContent.trim());
  }

  it('EMITIDA sin XML_GENERADO: solo Generar XML (ni Firmar, ni Enviar, aunque haya clave de acceso)', () => {
    cargar('ROLE_ADMIN', factura({ estado: 'EMITIDA', claveAcceso: 'clave-ficticia', documentosDisponibles: [] }));
    const acciones = accionesVisibles();
    expect(acciones.some((a) => a.includes('Generar XML'))).toBeTrue();
    expect(acciones.some((a) => a.includes('Firmar'))).toBeFalse();
    expect(acciones.some((a) => a.includes('Enviar al SRI'))).toBeFalse();
  });

  it('EMITIDA con XML_GENERADO: aparece Firmar, ya NO Generar XML, y todavía no Enviar', () => {
    cargar('ROLE_ADMIN', factura({ estado: 'EMITIDA', claveAcceso: 'clave-ficticia', documentosDisponibles: ['XML_GENERADO'] }));
    const acciones = accionesVisibles();
    expect(acciones.some((a) => a.includes('Generar XML'))).toBeFalse();
    expect(acciones.some((a) => a.includes('Firmar'))).toBeTrue();
    expect(acciones.some((a) => a.includes('Enviar al SRI'))).toBeFalse();
  });

  it('EMITIDA con XML_FIRMADO: aparece Enviar al SRI, ya NO Firmar ni Generar XML', () => {
    cargar('ROLE_ADMIN', factura({ estado: 'EMITIDA', claveAcceso: 'clave-ficticia', documentosDisponibles: ['XML_GENERADO', 'XML_FIRMADO'] }));
    const acciones = accionesVisibles();
    expect(acciones.some((a) => a.includes('Generar XML'))).toBeFalse();
    expect(acciones.some((a) => a.includes('Firmar'))).toBeFalse();
    expect(acciones.some((a) => a.includes('Enviar al SRI'))).toBeTrue();
  });

  it('AUTORIZADA: ninguna acción mutante (ni Emitir, ni XML, ni Firmar, ni Enviar, ni Sincronizar)', () => {
    cargar('ROLE_ADMIN', factura({ estado: 'AUTORIZADA', claveAcceso: 'clave-ficticia', numeroAutorizacion: '123', documentosDisponibles: ['XML_GENERADO', 'XML_FIRMADO', 'XML_AUTORIZADO'] }));
    expect(accionesVisibles()).toEqual([]);
  });

  it('Sincronizar SRI se muestra para EMITIDA y para RECHAZADA con clave de acceso (nunca solo bajo PPR), y nunca para AUTORIZADA ni sin clave', () => {
    // EMITIDA, sin ningún intento todavía registrado (estadoRecepcion/estadoAutorizacion null):
    // el backend igual acepta sincronizar (FacturaSriEstadoService#prepararSincronizacion
    // solo exige estado != BORRADOR + clave de acceso), así que la UI debe ofrecerlo.
    cargar('ROLE_ADMIN', factura({ estado: 'EMITIDA', claveAcceso: 'clave-ficticia', estadoRecepcion: null, estadoAutorizacion: null }));
    expect(accionesVisibles().some((a) => a.includes('Sincronizar SRI'))).toBeTrue();

    // PPR sigue estando cubierto, por supuesto.
    cargar('ROLE_ADMIN', factura({ estado: 'EMITIDA', claveAcceso: 'clave-ficticia', estadoRecepcion: 'RECIBIDA', estadoAutorizacion: 'PPR' }));
    expect(accionesVisibles().some((a) => a.includes('Sincronizar SRI'))).toBeTrue();

    // RECHAZADA (p. ej. NAT): el backend tampoco lo excluye (solo corta en AUTORIZADA).
    cargar('ROLE_ADMIN', factura({ estado: 'RECHAZADA', claveAcceso: 'clave-ficticia', estadoRecepcion: 'RECIBIDA', estadoAutorizacion: 'NAT' }));
    expect(accionesVisibles().some((a) => a.includes('Sincronizar SRI'))).toBeTrue();

    // AUTORIZADA: FacturaSriService#sincronizar es un no-op ahí; no se ofrece.
    cargar('ROLE_ADMIN', factura({ estado: 'AUTORIZADA', claveAcceso: 'clave-ficticia', estadoAutorizacion: 'AUT' }));
    expect(accionesVisibles().some((a) => a.includes('Sincronizar SRI'))).toBeFalse();

    // BORRADOR: nunca hay clave de acceso; FacturaSriEstadoService#prepararSincronizacion lo rechaza.
    cargar('ROLE_ADMIN', factura({ estado: 'BORRADOR', claveAcceso: null }));
    expect(accionesVisibles().some((a) => a.includes('Sincronizar SRI'))).toBeFalse();
  });

  it('descarga de documento pide un Blob y lo entrega al navegador tal cual, sin parsear el XML', () => {
    cargar('ROLE_ADMIN', factura({ estado: 'AUTORIZADA', documentosDisponibles: ['XML_AUTORIZADO'] }));

    const creado = spyOn(URL, 'createObjectURL').and.returnValue('blob:ficticio');
    const revocado = spyOn(URL, 'revokeObjectURL');

    const botonDescargar = Array.from(fixture.nativeElement.querySelectorAll('button')).find((b: any) =>
      b.textContent.includes('XML autorizado')
    ) as HTMLButtonElement;
    botonDescargar.click();
    fixture.detectChanges();

    const req = httpMock.expectOne((r) => r.url === '/api/facturas/7/documentos/XML_AUTORIZADO');
    expect(req.request.responseType).toBe('blob');
    const contenido = new Blob(['<xml>contenido real</xml>']);
    req.flush(contenido);

    expect(creado).toHaveBeenCalledWith(contenido);
    expect(revocado).toHaveBeenCalledWith('blob:ficticio');
  });

  // ==================================================================
  // Fase 10: RIDE (PDF)
  // ==================================================================

  it('"Descargar RIDE (PDF)" solo aparece con la factura AUTORIZADA, para ADMIN/AUXILIAR/DUENO (nunca VETERINARIO)', () => {
    cargar('ROLE_ADMIN', factura({ estado: 'EMITIDA', claveAcceso: 'clave-ficticia' }));
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Descargar RIDE (PDF)');

    cargar('ROLE_ADMIN', factura({ estado: 'AUTORIZADA' }));
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Descargar RIDE (PDF)');

    cargar('ROLE_AUXILIAR', factura({ estado: 'AUTORIZADA' }));
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Descargar RIDE (PDF)');

    cargar('ROLE_DUENO', factura({ estado: 'AUTORIZADA' }));
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Descargar RIDE (PDF)');

    cargar('ROLE_VETERINARIO', factura({ estado: 'AUTORIZADA' }));
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Descargar RIDE (PDF)');
  });

  it('el RIDE se pide como Blob a /ride y se entrega al navegador sin generarlo ni parsearlo en Angular', () => {
    cargar('ROLE_ADMIN', factura({ estado: 'AUTORIZADA', documentosDisponibles: [] }));

    const creado = spyOn(URL, 'createObjectURL').and.returnValue('blob:ride-ficticio');
    spyOn(URL, 'revokeObjectURL');

    const botonRide = Array.from(fixture.nativeElement.querySelectorAll('button')).find((b: any) =>
      b.textContent.includes('Descargar RIDE (PDF)')
    ) as HTMLButtonElement;
    botonRide.click();
    fixture.detectChanges();

    const req = httpMock.expectOne((r) => r.url === '/api/facturas/7/ride' && r.method === 'GET');
    expect(req.request.responseType).toBe('blob');
    const pdfFicticio = new Blob(['%PDF-ficticio']);
    req.flush(pdfFicticio);

    expect(creado).toHaveBeenCalledWith(pdfFicticio);
  });

  // ==================================================================
  // Fix funcional: eliminar factura en BORRADOR (DELETE /api/facturas/{id})
  // ==================================================================

  function botonEliminarBorrador(): HTMLButtonElement | undefined {
    return Array.from(fixture.nativeElement.querySelectorAll('button')).find(
      (b: any) => b.textContent.trim() === 'Eliminar borrador'
    ) as HTMLButtonElement | undefined;
  }

  it('ADMIN y AUXILIAR ven "Eliminar borrador" en un BORRADOR; VETERINARIO y DUENO nunca lo ven', () => {
    cargar('ROLE_ADMIN', factura({ estado: 'BORRADOR' }));
    expect(botonEliminarBorrador()).toBeDefined();

    cargar('ROLE_AUXILIAR', factura({ estado: 'BORRADOR' }));
    expect(botonEliminarBorrador()).toBeDefined();

    cargar('ROLE_VETERINARIO', factura({ estado: 'BORRADOR' }));
    expect(botonEliminarBorrador()).toBeUndefined();

    cargar('ROLE_DUENO', factura({ estado: 'BORRADOR' }));
    expect(botonEliminarBorrador()).toBeUndefined();
  });

  it('nunca se ofrece "Eliminar borrador" para EMITIDA, AUTORIZADA ni RECHAZADA, ni con ese texto ni con "Anular"/"Cancelar factura SRI"', () => {
    for (const estado of ['EMITIDA', 'AUTORIZADA', 'RECHAZADA'] as const) {
      cargar('ROLE_ADMIN', factura({ estado, claveAcceso: 'clave-ficticia' }));
      const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
      expect(botonEliminarBorrador()).toBeUndefined();
      expect(texto).not.toContain('Anular factura');
      expect(texto).not.toContain('Cancelar factura SRI');
    }
  });

  it('al pulsar "Eliminar borrador" pide confirmación explícita antes de llamar al backend', () => {
    cargar('ROLE_ADMIN', factura({ estado: 'BORRADOR' }));

    botonEliminarBorrador()!.click();
    fixture.detectChanges();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('¿Eliminar este borrador? Esta acción eliminará sus detalles y pagos y no se puede deshacer.');
    httpMock.expectNone((r) => r.method === 'DELETE');
  });

  it('confirmar la eliminación llama a DELETE /api/facturas/{id} y navega a /facturas', () => {
    cargar('ROLE_ADMIN', factura({ estado: 'BORRADOR' }));
    const router = TestBed.inject(Router);
    const navegar = spyOn(router, 'navigate').and.resolveTo(true);

    botonEliminarBorrador()!.click();
    fixture.detectChanges();

    const botonConfirmar = Array.from(fixture.nativeElement.querySelectorAll('button')).find((b: any) =>
      b.textContent.includes('Sí, eliminar borrador')
    ) as HTMLButtonElement;
    botonConfirmar.click();
    fixture.detectChanges();

    const req = httpMock.expectOne((r) => r.url === '/api/facturas/7' && r.method === 'DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();

    expect(navegar).toHaveBeenCalledWith(['/facturas']);
  });

  it('un 409 al eliminar NO quita la factura de la vista: se muestra el error y la factura sigue intacta', () => {
    cargar('ROLE_ADMIN', factura({ estado: 'BORRADOR' }));
    const router = TestBed.inject(Router);
    const navegar = spyOn(router, 'navigate');

    botonEliminarBorrador()!.click();
    fixture.detectChanges();
    (Array.from(fixture.nativeElement.querySelectorAll('button')).find((b: any) =>
      b.textContent.includes('Sí, eliminar borrador')
    ) as HTMLButtonElement).click();
    fixture.detectChanges();

    httpMock
      .expectOne((r) => r.url === '/api/facturas/7' && r.method === 'DELETE')
      .flush({ detail: 'La factura 7 esta en estado EMITIDA y solo puede modificarse mientras sea BORRADOR.' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('La factura 7 esta en estado EMITIDA y solo puede modificarse mientras sea BORRADOR.');
    expect((fixture.componentInstance as any).factura()).not.toBeNull();
    expect((fixture.componentInstance as any).factura().id).toBe(7);
    expect(navegar).not.toHaveBeenCalled();
  });
});
