import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { ICONS, IconName } from './icon-registry';

/**
 * Icono SVG propio de BIOPET, sin dependencia externa.
 *
 * Decorativo por defecto (aria-hidden="true"): úsese así siempre que el
 * icono acompañe a un texto visible. Cuando el icono ES el único
 * contenido de un control (un botón icon-only), pasar `label` con una
 * descripción — el icono deja de ser aria-hidden y el consumidor sigue
 * siendo responsable de poner `aria-label` en el propio <button>, ya que
 * un <svg role="img"> anidado no sustituye la etiqueta accesible del
 * control interactivo que lo contiene.
 *
 * Ejemplo decorativo:  <app-icon name="mascotas" />
 * Ejemplo icon-only:   <button aria-label="Cerrar"><app-icon name="close" /></button>
 */
@Component({
  selector: 'app-icon',
  standalone: true,
  imports: [CommonModule],
  template: `
    <svg
      [attr.width]="size"
      [attr.height]="size"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.5"
      stroke-linecap="round"
      stroke-linejoin="round"
      [attr.aria-hidden]="label ? null : 'true'"
      [attr.role]="label ? 'img' : null"
      [attr.aria-label]="label ?? null"
    >
      <path *ngFor="let d of shape.paths" [attr.d]="d" />
      <line
        *ngFor="let l of shape.lines"
        [attr.x1]="l[0]"
        [attr.y1]="l[1]"
        [attr.x2]="l[2]"
        [attr.y2]="l[3]"
      />
      <circle
        *ngFor="let c of shape.circles"
        [attr.cx]="c[0]"
        [attr.cy]="c[1]"
        [attr.r]="c[2]"
      />
      <polyline *ngFor="let p of shape.polylines" [attr.points]="p" />
      <rect
        *ngFor="let r of shape.rects"
        [attr.x]="r[0]"
        [attr.y]="r[1]"
        [attr.width]="r[2]"
        [attr.height]="r[3]"
        [attr.rx]="r[4] ?? 0"
      />
    </svg>
  `,
})
export class IconComponent {
  @Input({ required: true }) name!: IconName;
  @Input() size = 20;
  @Input() label?: string;

  get shape() {
    return ICONS[this.name];
  }
}
