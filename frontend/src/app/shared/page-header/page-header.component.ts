import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { RouterLink } from '@angular/router';

export interface BreadcrumbItem {
  label: string;
  path?: string;
}

/**
 * Banda de página: cabecera de superficie blanca a ancho completo del
 * área de contenido, separada del contenido operativo (beige) por un
 * borde inferior, no por una sombra grande. Se reutiliza en cada pantalla
 * dentro del shell porque solo cada pantalla conoce su propia acción
 * principal real (que además depende del rol — ver `puedeGestionar` en
 * MascotasComponent).
 *
 * `eyebrow` es la categoría de sección (PACIENTES, AGENDA, REGISTRO
 * CLÍNICO…) y trae consigo la regla de registro bermellón — el elemento
 * firma de la identidad BIOPET. `breadcrumb` solo debe pasarse cuando
 * existe jerarquía real (p. ej. "Mascotas / Firulais").
 */
@Component({
  selector: 'app-page-header',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <header class="page-header">
      <div class="page-header__main">
        <span class="eyebrow page-header__eyebrow" *ngIf="eyebrow">{{ eyebrow }}</span>
        <span class="registration-rule" *ngIf="eyebrow" aria-hidden="true"></span>
        <nav *ngIf="breadcrumb?.length" class="page-header__breadcrumb" aria-label="Ruta">
          <ng-container *ngFor="let item of breadcrumb; let last = last">
            <a *ngIf="item.path && !last" [routerLink]="item.path">{{ item.label }}</a>
            <span *ngIf="!item.path || last" [attr.aria-current]="last ? 'page' : null">{{ item.label }}</span>
            <span *ngIf="!last" aria-hidden="true"> / </span>
          </ng-container>
        </nav>
        <h1 class="page-header__title">{{ title }}</h1>
        <p *ngIf="description" class="page-header__description">{{ description }}</p>
      </div>
      <div class="page-header__actions" *ngIf="hasActions">
        <ng-content></ng-content>
      </div>
    </header>
  `,
})
export class PageHeaderComponent {
  @Input({ required: true }) title!: string;
  @Input() eyebrow?: string;
  @Input() description?: string;
  @Input() breadcrumb?: BreadcrumbItem[];

  /**
   * No hay una forma limpia de detectar contenido proyectado vacío desde
   * el propio componente sin ViewChild + ngAfterContentChecked, lo que es
   * más coste del que vale para este caso. Cada pantalla que no tenga
   * acción principal simplemente pasa `[hasActions]="false"`.
   */
  @Input() hasActions = true;
}
