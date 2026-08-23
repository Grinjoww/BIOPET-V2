import { Component, Input } from '@angular/core';

/**
 * Monograma de identidad de BIOPET: una «B» geométrica construida con dos
 * arcos idénticos sobre una barra vertical — sin placa, sin silueta
 * animal, sin cruz médica, sin corazón. Deliberadamente tipográfico y
 * sobrio, tal como pidió la corrección del QA visual.
 *
 * Es un componente separado de IconComponent a propósito: los iconos de
 * `icon-registry.ts` son wayfinding funcional (trazo 1.5px, sin relleno,
 * 15 nombres tipados); este es la marca (relleno sólido en currentColor),
 * y solo se usa dos veces en toda la aplicación — sidebar y login — nunca
 * como decoración repetida.
 */
@Component({
  selector: 'app-monogram',
  standalone: true,
  template: `
    <svg [attr.width]="size" [attr.height]="size" viewBox="0 0 32 32" fill="currentColor" aria-hidden="true">
      <rect x="7" y="4" width="5" height="24" rx="2.5" />
      <path d="M12 4 H18 A6 6 0 0 1 18 16 H12 Z" />
      <path d="M12 16 H18 A6 6 0 0 1 18 28 H12 Z" />
    </svg>
  `,
})
export class MonogramComponent {
  @Input() size = 32;
}
