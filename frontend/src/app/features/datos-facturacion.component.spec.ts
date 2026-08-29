import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { AuthService } from '../core/auth.service';
import { DatosFacturacionComponent } from './datos-facturacion.component';

describe('DatosFacturacionComponent (ownership propio, sin selector de usuario)', () => {
  let fixture: ComponentFixture<DatosFacturacionComponent>;
  let httpMock: HttpTestingController;
  let auth: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [DatosFacturacionComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    fixture = TestBed.createComponent(DatosFacturacionComponent);
    auth = TestBed.inject(AuthService);
    auth.usuarioActual.set({ id: 8, nombre: 'Dueño Real', email: 'd@biopet.test', rol: 'ROLE_DUENO', activo: true });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('lista SIEMPRE con el usuarioId de la sesión propia, nunca otro', () => {
    fixture.detectChanges();
    const req = httpMock.expectOne((r) => r.method === 'GET' && r.url === '/api/usuarios/8/datos-facturacion');
    req.flush([
      { id: 1, tipoIdentificacion: 'CEDULA', identificacion: '0000000000', razonSocial: 'Dueño Real', direccion: null, telefono: null, emailFacturacion: null, predeterminado: true, activo: true },
    ]);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Dueño Real');
  });

  it('crear, marcar predeterminado y desactivar usan siempre el mismo usuarioId propio en la URL', () => {
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url === '/api/usuarios/8/datos-facturacion').flush([]);
    fixture.detectChanges();

    fixture.componentInstance.abrirCrear();
    fixture.componentInstance.form.setValue({
      tipoIdentificacion: 'CEDULA',
      identificacion: '0000000000',
      razonSocial: 'Dueño Real',
      direccion: '',
      telefono: '',
      emailFacturacion: '',
    });
    fixture.componentInstance.guardar();

    const reqCrear = httpMock.expectOne((r) => r.method === 'POST' && r.url === '/api/usuarios/8/datos-facturacion');
    expect(reqCrear.request.url).toBe('/api/usuarios/8/datos-facturacion');
    reqCrear.flush({ id: 5, tipoIdentificacion: 'CEDULA', identificacion: '0000000000', razonSocial: 'Dueño Real', direccion: null, telefono: null, emailFacturacion: null, predeterminado: false, activo: true });
    httpMock.expectOne((r) => r.url === '/api/usuarios/8/datos-facturacion').flush([]);

    fixture.componentInstance.marcarPredeterminado({ id: 5 } as any);
    const reqPredeterminado = httpMock.expectOne((r) => r.method === 'PATCH' && r.url === '/api/usuarios/8/datos-facturacion/5/predeterminado');
    expect(reqPredeterminado.request.url).toBe('/api/usuarios/8/datos-facturacion/5/predeterminado');
    reqPredeterminado.flush({});
    httpMock.expectOne((r) => r.url === '/api/usuarios/8/datos-facturacion').flush([]);

    fixture.componentInstance.desactivar({ id: 5, razonSocial: 'Dueño Real' } as any);
    const reqDesactivar = httpMock.expectOne((r) => r.method === 'DELETE' && r.url === '/api/usuarios/8/datos-facturacion/5');
    expect(reqDesactivar.request.url).toBe('/api/usuarios/8/datos-facturacion/5');
    reqDesactivar.flush(null);
    httpMock.expectOne((r) => r.url === '/api/usuarios/8/datos-facturacion').flush([]);
  });
});
