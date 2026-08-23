import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { FocusTrapDirective } from './focus-trap.directive';

@Component({
  standalone: true,
  imports: [CommonModule, FocusTrapDirective],
  template: `
    <button id="fuera">Fuera del trap</button>
    <div id="mostrar" *ngIf="mostrar" appFocusTrap>
      <button id="primero">Primero</button>
      <button id="segundo">Segundo</button>
    </div>
  `,
})
class HostComponent {
  mostrar = false;
}

describe('FocusTrapDirective (foco real de navegador, no simulado)', () => {
  let fixture: ComponentFixture<HostComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HostComponent] });
    fixture = TestBed.createComponent(HostComponent);
  });

  it('al aparecer, mueve el foco al primer elemento focusable dentro del host y, al desaparecer, restaura el foco anterior', () => {
    fixture.detectChanges();
    const fuera = fixture.nativeElement.querySelector('#fuera') as HTMLButtonElement;
    fuera.focus();
    expect(document.activeElement).toBe(fuera);

    fixture.componentInstance.mostrar = true;
    fixture.detectChanges();
    const primero = fixture.nativeElement.querySelector('#primero') as HTMLButtonElement;
    expect(document.activeElement).toBe(primero);

    fixture.componentInstance.mostrar = false;
    fixture.detectChanges();
    expect(document.activeElement).toBe(fuera);
  });
});
