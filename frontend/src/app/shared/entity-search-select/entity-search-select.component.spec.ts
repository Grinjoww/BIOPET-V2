import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { SimpleChange } from '@angular/core';
import { Observable, of, throwError } from 'rxjs';

import { EntitySearchSelectComponent, OpcionBusqueda } from './entity-search-select.component';

function opcion(id: number, principal: string, secundario?: string): OpcionBusqueda {
  return { id, principal, secundario };
}

describe('EntitySearchSelectComponent (selector buscable genérico, auditoría de usabilidad con datos masivos)', () => {
  let fixture: ComponentFixture<EntitySearchSelectComponent>;
  let component: EntitySearchSelectComponent;

  function enfocarYEscribir(texto: string): void {
    const input: HTMLInputElement = fixture.nativeElement.querySelector('input');
    input.dispatchEvent(new Event('focus'));
    input.value = texto;
    input.dispatchEvent(new Event('input'));
  }

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [EntitySearchSelectComponent] });
    fixture = TestBed.createComponent(EntitySearchSelectComponent);
    component = fixture.componentInstance;
  });

  it('NO busca nada hasta 2+ caracteres (prohibido cargar todo el catálogo), y respeta debounce', fakeAsync(() => {
    const buscar = jasmine.createSpy('buscar').and.returnValue(of([opcion(1, 'Ana')]));
    component.buscar = buscar;
    fixture.detectChanges();

    enfocarYEscribir('a');
    tick(300);
    expect(buscar).not.toHaveBeenCalled();

    enfocarYEscribir('an');
    tick(100); // menos que el debounce: todavía no debe llamar
    expect(buscar).not.toHaveBeenCalled();
    tick(200); // completa los 300ms
    expect(buscar).toHaveBeenCalledWith('an');
  }));

  it('muestra los resultados reales (máximo lo que el padre devuelva) y "Sin resultados" cuando no hay ninguno', fakeAsync(() => {
    component.buscar = (q: string): Observable<OpcionBusqueda[]> =>
      q === 'ana' ? of([opcion(1, 'Ana Dueña', 'ana@biopet.com'), opcion(2, 'Ana Pérez', 'anap@biopet.com')]) : of([]);
    fixture.detectChanges();

    enfocarYEscribir('ana');
    tick(300);
    fixture.detectChanges();

    let filas = fixture.nativeElement.querySelectorAll('.entity-search-select__opcion');
    expect(filas.length).toBe(2);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Ana Dueña');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('ana@biopet.com');

    enfocarYEscribir('zz');
    tick(300);
    fixture.detectChanges();

    filas = fixture.nativeElement.querySelectorAll('.entity-search-select__opcion');
    expect(filas.length).toBe(0);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Sin resultados');
  }));

  it('un error del backend no rompe el componente: se resuelve como "sin resultados"', fakeAsync(() => {
    component.buscar = () => throwError(() => new Error('fallo de red'));
    fixture.detectChanges();

    enfocarYEscribir('ana');
    tick(300);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Sin resultados');
  }));

  it('al elegir una opción: la muestra como selección visible, limpia el buscador y emite el id real (no el texto)', fakeAsync(() => {
    component.buscar = () => of([opcion(9, 'Beto Dueño', 'beto@biopet.com')]);
    let emitido: OpcionBusqueda | null | undefined;
    component.seleccion.subscribe((v) => (emitido = v));
    fixture.detectChanges();

    enfocarYEscribir('beto');
    tick(300);
    fixture.detectChanges();

    const fila: HTMLElement = fixture.nativeElement.querySelector('.entity-search-select__opcion');
    fila.dispatchEvent(new Event('mousedown'));
    fixture.detectChanges();

    expect(emitido).toEqual(opcion(9, 'Beto Dueño', 'beto@biopet.com'));
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Beto Dueño');
    expect(fixture.nativeElement.querySelector('input')).toBeNull(); // el buscador se oculta tras elegir

    const limpiar: HTMLButtonElement = fixture.nativeElement.querySelector('.btn-icon');
    limpiar.click();
    fixture.detectChanges();

    expect(emitido).toBeNull();
    expect(fixture.nativeElement.querySelector('input')).toBeTruthy(); // vuelve a mostrar el buscador
  }));

  it('seleccionInicial precarga la selección (modo edición) sin disparar ninguna búsqueda', fakeAsync(() => {
    const buscar = jasmine.createSpy('buscar').and.returnValue(of([]));
    component.buscar = buscar;
    const valor = opcion(3, 'Ana Dueña', 'ana@biopet.com');
    component.seleccionInicial = valor;
    // TestBed.createComponent no pasa por un padre real con [seleccionInicial]="...":
    // ngOnChanges solo lo dispara un binding de plantilla, así que aquí se simula
    // directamente (igual que Angular lo invocaría desde un componente consumidor real).
    component.ngOnChanges({ seleccionInicial: new SimpleChange(null, valor, true) });
    fixture.detectChanges();

    expect(buscar).not.toHaveBeenCalled();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Ana Dueña');
  }));

  it('navegación con flechas: ArrowDown mueve el resaltado y Enter elige la opción activa', fakeAsync(() => {
    component.buscar = () => of([opcion(1, 'Ana'), opcion(2, 'Beto')]);
    let emitido: OpcionBusqueda | null | undefined;
    component.seleccion.subscribe((v) => (emitido = v));
    fixture.detectChanges();

    enfocarYEscribir('a');
    tick(0); // aún no llega al mínimo con 1 char, forzamos búsqueda real con 2+
    enfocarYEscribir('an');
    tick(300);
    fixture.detectChanges();

    const input: HTMLInputElement = fixture.nativeElement.querySelector('input');
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown' }));
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown' }));
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    fixture.detectChanges();

    expect(emitido).toEqual(opcion(2, 'Beto'));
  }));
});
