import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PageHeaderComponent } from './page-header.component';

@Component({
  standalone: true,
  imports: [PageHeaderComponent],
  template: `
    <app-page-header eyebrow="Agenda" title="Citas" description="Texto de prueba" [hasActions]="hasActions">
      <button id="accion-proyectada">Acción</button>
    </app-page-header>
  `,
})
class HostComponent {
  hasActions = true;
}

describe('PageHeaderComponent', () => {
  let fixture: ComponentFixture<HostComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HostComponent] });
    fixture = TestBed.createComponent(HostComponent);
  });

  it('renderiza eyebrow, título y descripción recibidos por @Input', () => {
    fixture.detectChanges();
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';

    expect(texto).toContain('Agenda');
    expect(texto).toContain('Citas');
    expect(texto).toContain('Texto de prueba');
  });

  it('proyecta el contenido de acción solo cuando hasActions es true', () => {
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#accion-proyectada')).toBeTruthy();

    fixture.componentInstance.hasActions = false;
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#accion-proyectada')).toBeFalsy();
  });
});
