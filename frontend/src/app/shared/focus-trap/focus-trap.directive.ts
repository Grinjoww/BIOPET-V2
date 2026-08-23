import { AfterViewInit, Directive, ElementRef, OnDestroy } from '@angular/core';

/**
 * Atrapa el foco de teclado dentro del elemento host mientras exista en
 * el DOM. Pensado para montarse/desmontarse junto a un *ngIf (modal de
 * confirmación, drawer de navegación móvil): al crearse mueve el foco
 * dentro del elemento y recuerda qué tenía el foco antes; al destruirse
 * (el *ngIf pasa a false) restaura el foco a ese elemento original.
 *
 * El propio host es responsable de:
 * - `role="dialog"` / `role="alertdialog"` + `aria-modal="true"`;
 * - `(keydown.escape)` para cerrar (este directive no decide cuándo cerrar,
 *   solo qué pasa con el foco mientras está abierto).
 */
@Directive({
  selector: '[appFocusTrap]',
  standalone: true,
})
export class FocusTrapDirective implements AfterViewInit, OnDestroy {
  private previouslyFocused: HTMLElement | null = null;
  private readonly handleKeydown = (event: KeyboardEvent) => {
    if (event.key !== 'Tab') return;

    const focusable = this.getFocusable();
    if (focusable.length === 0) return;

    const first = focusable[0];
    const last = focusable[focusable.length - 1];

    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  };

  constructor(private readonly el: ElementRef<HTMLElement>) {}

  ngAfterViewInit(): void {
    this.previouslyFocused = document.activeElement as HTMLElement | null;
    this.focusFirst();
    this.el.nativeElement.addEventListener('keydown', this.handleKeydown);
  }

  ngOnDestroy(): void {
    this.el.nativeElement.removeEventListener('keydown', this.handleKeydown);
    this.previouslyFocused?.focus();
  }

  private focusFirst(): void {
    const focusable = this.getFocusable();
    (focusable[0] ?? this.el.nativeElement).focus();
  }

  private getFocusable(): HTMLElement[] {
    return Array.from(
      this.el.nativeElement.querySelectorAll<HTMLElement>(
        'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
      )
    );
  }
}
