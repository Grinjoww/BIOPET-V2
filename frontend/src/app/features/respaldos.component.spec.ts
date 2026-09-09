import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { RespaldosComponent } from './respaldos.component';
import { RespaldoConfiguracion, RespaldoHistorial } from './respaldos-api.service';
import { PageResponse } from './mascota-api.service';

function configuracion(overrides: Partial<RespaldoConfiguracion> = {}): RespaldoConfiguracion {
  return {
    activo: false,
    diaSemana: 'MONDAY',
    hora: '02:00:00',
    ultimoRespaldoEn: null,
    proximoRespaldoEn: null,
    actualizadoEn: '2026-09-08T12:00:00Z',
    actualizadoPor: null,
    ...overrides,
  };
}

function historialFila(overrides: Partial<RespaldoHistorial> = {}): RespaldoHistorial {
  return {
    id: 1,
    tipo: 'MANUAL',
    estado: 'EXITOSO',
    iniciadoEn: '2026-09-08T12:00:00Z',
    finalizadoEn: '2026-09-08T12:00:05Z',
    duracionMs: 5000,
    nombreArchivo: 'biopet-respaldo-20260908T120000Z-1.dump',
    tamanoBytes: 2_500_000,
    mensajeErrorSeguro: null,
    ejecutadoPor: 'admin@biopet.ec',
    ...overrides,
  };
}

function paginaHistorial(filas: RespaldoHistorial[], extra: Partial<PageResponse<RespaldoHistorial>> = {}):
    PageResponse<RespaldoHistorial> {
  return {
    content: filas,
    totalElements: filas.length,
    totalPages: filas.length > 0 ? 1 : 0,
    number: 0,
    size: 20,
    first: true,
    last: true,
    empty: filas.length === 0,
    ...extra,
  };
}

describe('RespaldosComponent (integración ligera: TestBed + HttpTestingController)', () => {
  let fixture: ComponentFixture<RespaldosComponent>;
  let httpMock: HttpTestingController;

  function crear() {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [RespaldosComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    fixture = TestBed.createComponent(RespaldosComponent);
    httpMock = TestBed.inject(HttpTestingController);
  }

  function flushCargaInicial(cfg: RespaldoConfiguracion, filas: RespaldoHistorial[] = []) {
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === '/api/admin/respaldos/configuracion' && r.method === 'GET').flush(cfg);
    httpMock.expectOne((r) => r.url === '/api/admin/respaldos/historial' && r.method === 'GET')
        .flush(paginaHistorial(filas));
    fixture.detectChanges();
  }

  afterEach(() => httpMock.verify());

  it('carga inicial: pinta el estado real de la configuración (inactivo, lunes 02:00, sin último respaldo)', () => {
    crear();
    flushCargaInicial(configuracion());

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Todavía no se ha generado ninguno');
    expect(texto).toContain('No programado');

    const checkbox: HTMLInputElement = fixture.nativeElement.querySelector('input[type="checkbox"]');
    expect(checkbox.checked).toBe(false);
    const dia: HTMLSelectElement = fixture.nativeElement.querySelector('#r-dia');
    expect(dia.value).toBe('MONDAY');
    const hora: HTMLInputElement = fixture.nativeElement.querySelector('#r-hora');
    // El backend manda "02:00:00" (LocalTime); el <input type="time"> solo acepta "HH:mm".
    expect(hora.value).toBe('02:00');
  });

  it('muestra último y próximo respaldo cuando la configuración ya tiene ejecuciones', () => {
    crear();
    flushCargaInicial(
      configuracion({
        activo: true,
        ultimoRespaldoEn: '2026-09-08T10:00:00Z',
        proximoRespaldoEn: '2026-09-08T11:00:00Z',
      })
    );

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).not.toContain('Todavía no se ha generado ninguno');
    expect(texto).not.toContain('No programado');
  });

  it('guardar configuración envía el PUT con los valores del formulario y muestra confirmación', () => {
    crear();
    flushCargaInicial(configuracion());

    const dia: HTMLSelectElement = fixture.nativeElement.querySelector('#r-dia');
    dia.value = 'FRIDAY';
    dia.dispatchEvent(new Event('change'));
    const hora: HTMLInputElement = fixture.nativeElement.querySelector('#r-hora');
    hora.value = '15:00';
    hora.dispatchEvent(new Event('input'));
    const checkbox: HTMLInputElement = fixture.nativeElement.querySelector('input[type="checkbox"]');
    checkbox.click();
    fixture.detectChanges();

    const boton: HTMLButtonElement = fixture.nativeElement.querySelector('button[type="submit"]');
    boton.click();

    const req = httpMock.expectOne((r) => r.url === '/api/admin/respaldos/configuracion' && r.method === 'PUT');
    expect(req.request.body).toEqual({ activo: true, diaSemana: 'FRIDAY', hora: '15:00' });
    req.flush(configuracion({ activo: true, diaSemana: 'FRIDAY', hora: '15:00:00' }));
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Configuración de respaldos guardada.');
  });

  it('hora vacía no envía la petición y marca el campo como inválido', () => {
    crear();
    flushCargaInicial(configuracion());

    const hora: HTMLInputElement = fixture.nativeElement.querySelector('#r-hora');
    hora.value = '';
    hora.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const boton: HTMLButtonElement = fixture.nativeElement.querySelector('button[type="submit"]');
    boton.click();
    fixture.detectChanges();

    httpMock.expectNone((r) => r.url === '/api/admin/respaldos/configuracion' && r.method === 'PUT');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Seleccione una hora válida.');
  });

  it('generar respaldo ahora dispara el POST, confirma y recarga el historial', () => {
    crear();
    flushCargaInicial(configuracion());

    const botones = fixture.nativeElement.querySelectorAll('button');
    const botonGenerar: HTMLButtonElement = Array.from(botones as NodeListOf<HTMLButtonElement>)
        .find((b) => b.textContent?.includes('Generar respaldo ahora'))!;
    botonGenerar.click();

    const reqPost = httpMock.expectOne((r) => r.url === '/api/admin/respaldos/ejecutar' && r.method === 'POST');
    reqPost.flush(historialFila({ estado: 'EN_PROCESO' }), { status: 202, statusText: 'Accepted' });
    fixture.detectChanges();

    // Tras el 202, la pantalla recarga configuracion + historial.
    httpMock.expectOne((r) => r.url === '/api/admin/respaldos/configuracion' && r.method === 'GET')
        .flush(configuracion());
    httpMock.expectOne((r) => r.url === '/api/admin/respaldos/historial' && r.method === 'GET')
        .flush(paginaHistorial([historialFila({ estado: 'EN_PROCESO' })]));
    fixture.detectChanges();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Respaldo iniciado. Revisa el historial para ver su resultado.');
    expect(texto).toContain('En proceso');
  });

  it('un 409 (respaldo ya en proceso) muestra el detalle del error tal cual, sin tocar el historial', () => {
    crear();
    flushCargaInicial(configuracion());

    const botones = fixture.nativeElement.querySelectorAll('button');
    const botonGenerar: HTMLButtonElement = Array.from(botones as NodeListOf<HTMLButtonElement>)
        .find((b) => b.textContent?.includes('Generar respaldo ahora'))!;
    botonGenerar.click();

    httpMock
      .expectOne((r) => r.url === '/api/admin/respaldos/ejecutar' && r.method === 'POST')
      .flush(
        { detail: 'Ya hay un respaldo en proceso. Espere a que finalice antes de generar otro.' },
        { status: 409, statusText: 'Conflict' }
      );
    fixture.detectChanges();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Ya hay un respaldo en proceso.');
    httpMock.expectNone((r) => r.url === '/api/admin/respaldos/historial');
  });

  it('la tabla de historial pinta fecha, tipo, estado, duración y tamaño legibles', () => {
    crear();
    flushCargaInicial(
      configuracion(),
      [historialFila({ tipo: 'AUTOMATICO', estado: 'FALLIDO', duracionMs: 65_000, tamanoBytes: null })]
    );

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Automático');
    expect(texto).toContain('Fallido');
    expect(texto).toContain('1 min 5 s');
    expect(texto).toContain('—'); // tamaño ausente (fallo antes de escribir el archivo)
  });

  it('empty state real: sin historial muestra el mensaje vacío, sin filas inventadas', () => {
    crear();
    flushCargaInicial(configuracion(), []);

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Sin respaldos generados todavía.');
    expect(fixture.nativeElement.querySelectorAll('tbody tr').length).toBe(1);
  });

  it('paginación: "Siguiente" pide la página 1 del historial', () => {
    crear();
    flushCargaInicial(configuracion(), [historialFila()]);
    // Forzamos 2 páginas reflushando con totalPages=2 vía una nueva carga inicial no es trivial;
    // en su lugar comprobamos directamente que irAPagina(1) pide page=1.
    (fixture.componentInstance as RespaldosComponent).totalPaginas.set(2);
    fixture.detectChanges();

    const siguiente: HTMLButtonElement = Array.from(
      fixture.nativeElement.querySelectorAll('.pagination button') as NodeListOf<HTMLButtonElement>
    ).find((b) => b.textContent?.includes('Siguiente'))!;
    siguiente.click();

    const req = httpMock.expectOne(
      (r) => r.url === '/api/admin/respaldos/historial' && r.params.get('page') === '1'
    );
    expect(req.request.params.get('page')).toBe('1');
    req.flush(paginaHistorial([]));
  });
});
