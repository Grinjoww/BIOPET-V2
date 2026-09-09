import { Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Observable, Subject, Subscription, of } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, switchMap } from 'rxjs/operators';

/** Una opción de resultado: lo mínimo para distinguir dos entidades parecidas sin adivinar su forma real. */
export interface OpcionBusqueda {
  id: number;
  principal: string;
  secundario?: string;
}

const MIN_CARACTERES = 2;
const DEBOUNCE_MS = 300;

/**
 * Selector buscable genérico, para reemplazar un {@code <select>} que
 * cargaría cientos o miles de opciones (dueño, veterinario, mascota...).
 * Auditoría de usabilidad con datos masivos (biopet_db_1m_v2: 2.002
 * usuarios, 10.000 mascotas): un {@code <select>} con esos volúmenes es
 * inusable, y en el caso de mascotas directamente rompía la selección (el
 * formulario solo cargaba las primeras 200 de 10.000, alfabéticas —el resto
 * no se podía elegir).
 *
 * <p>No depende de qué entidad busca: el padre inyecta la función
 * {@link buscar} (normalmente una llamada paginada real al backend,
 * {@code GET .../recurso?q=...&size=20}) y este componente solo se encarga
 * de la interacción -debounce, mínimo de caracteres, estados de carga/vacío,
 * navegación con flechas, mostrar y limpiar la selección-. Reutilizado tal
 * cual en factura-nueva, mascotas, citas, consultas y vacunas.
 *
 * <p>No es un {@code ControlValueAccessor} de Reactive Forms a propósito:
 * el resto de BIOPET ya mezcla controles reactivos con campos sueltos
 * {@code [(ngModel)]} en la misma pantalla (ver conceptoSeleccionado en
 * factura-nueva.component.ts); este componente sigue ese mismo patrón ya
 * establecido -emite la opción elegida y el padre decide cómo guardarla
 * (normalmente {@code form.patchValue({...})})-, evitando la complejidad de
 * un CVA completo para una pantalla que no lo necesitaba antes.
 */
@Component({
  selector: 'app-entity-search-select',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
  <div class="field entity-search-select">
    <label *ngIf="label" [attr.for]="controlId">{{ label }}<span *ngIf="requerido" class="required-mark" aria-hidden="true">*</span></label>

    <!-- ===== Ya hay una selección: chip + botón para cambiarla ===== -->
    <div class="entity-search-select__actual" *ngIf="seleccionActual">
      <span class="chip chip--neutral">{{ seleccionActual.principal }}<span *ngIf="seleccionActual.secundario"> — {{ seleccionActual.secundario }}</span></span>
      <button type="button" class="btn btn--ghost btn--sm" (click)="cambiar()" [disabled]="disabled">Cambiar</button>
      <button type="button" class="btn-icon" (click)="limpiar()" [disabled]="disabled" aria-label="Quitar selección">✕</button>
    </div>

    <!-- ===== Buscador ===== -->
    <div class="entity-search-select__buscador" *ngIf="!seleccionActual" style="position: relative;">
      <input
        [id]="controlId"
        type="text"
        role="combobox"
        aria-autocomplete="list"
        [attr.aria-expanded]="mostrarLista()"
        [attr.aria-controls]="controlId + '-listbox'"
        [attr.aria-activedescendant]="indiceActivo >= 0 ? (controlId + '-opt-' + indiceActivo) : null"
        [placeholder]="placeholder"
        [disabled]="disabled"
        [ngModel]="texto"
        [ngModelOptions]="{ standalone: true }"
        (ngModelChange)="onTextoCambiado($event)"
        (keydown)="onKeydown($event)"
        (focus)="onFocus()"
        (blur)="onBlurConRetardo()"
        autocomplete="off" />

      <ul
        [id]="controlId + '-listbox'"
        role="listbox"
        class="entity-search-select__lista"
        *ngIf="mostrarLista()">
        <li *ngIf="cargando" class="entity-search-select__estado" role="status" aria-live="polite">Buscando…</li>
        <li *ngIf="!cargando && texto.trim().length > 0 && texto.trim().length < minCaracteres" class="entity-search-select__estado">
          Escriba al menos {{ minCaracteres }} caracteres para buscar.
        </li>
        <li *ngIf="!cargando && texto.trim().length >= minCaracteres && resultados.length === 0" class="entity-search-select__estado">
          Sin resultados para «{{ texto.trim() }}».
        </li>
        <li
          *ngFor="let opcion of resultados; let i = index"
          [id]="controlId + '-opt-' + i"
          role="option"
          [attr.aria-selected]="i === indiceActivo"
          class="entity-search-select__opcion"
          [class.entity-search-select__opcion--activa]="i === indiceActivo"
          (mousedown)="elegir(opcion)">
          <strong>{{ opcion.principal }}</strong>
          <span *ngIf="opcion.secundario" class="field-hint"> — {{ opcion.secundario }}</span>
        </li>
      </ul>
    </div>

    <p class="field-hint" *ngIf="hint && !seleccionActual">{{ hint }}</p>
  </div>
  `,
  styles: [`
    .entity-search-select__actual {
      display: flex;
      align-items: center;
      gap: var(--space-2);
      flex-wrap: wrap;
    }
    .entity-search-select__lista {
      position: absolute;
      z-index: 20;
      top: calc(100% + 2px);
      left: 0;
      right: 0;
      margin: 0;
      padding: 4px 0;
      list-style: none;
      background: var(--color-surface);
      border: 1px solid var(--color-border-strong);
      border-radius: var(--radius-sm);
      max-height: 260px;
      overflow-y: auto;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
    }
    .entity-search-select__opcion {
      padding: 8px 14px;
      cursor: pointer;
      font: var(--text-body);
    }
    .entity-search-select__opcion--activa,
    .entity-search-select__opcion:hover {
      background: var(--color-surface-sunken);
    }
    .entity-search-select__estado {
      padding: 8px 14px;
      color: var(--color-muted);
      font: var(--text-body);
    }
  `],
})
export class EntitySearchSelectComponent implements OnChanges, OnDestroy {
  /**
   * OJO: se llama `controlId`, NO `id` -si un consumidor escribe
   * `id="f-xxx"` en la etiqueta del host, Angular deja ESE atributo en el
   * DOM del propio `<app-entity-search-select>` además de pasarlo como
   * input, y el resultado son DOS elementos con el mismo id en la página
   * (el host y el `<input>` interno), lo que rompe `getElementById`,
   * `querySelector('#id')` y cualquier asociación por id. Usar `controlId`
   * -que no colisiona con el atributo nativo `id`- evita el problema.
   */
  @Input() controlId = 'entity-search';
  @Input() label = '';
  @Input() placeholder = 'Escriba para buscar…';
  @Input() hint = '';
  @Input() requerido = false;
  @Input() disabled = false;
  @Input() minCaracteres = MIN_CARACTERES;
  /** Función de búsqueda real inyectada por el padre: normalmente una llamada paginada al backend. */
  @Input() buscar!: (q: string) => Observable<OpcionBusqueda[]>;
  /** Selección ya conocida (modo edición): se muestra sin necesidad de volver a buscar. */
  @Input() seleccionInicial: OpcionBusqueda | null = null;
  @Output() seleccion = new EventEmitter<OpcionBusqueda | null>();

  texto = '';
  cargando = false;
  resultados: OpcionBusqueda[] = [];
  indiceActivo = -1;
  seleccionActual: OpcionBusqueda | null = null;
  private enfocado = false;

  private readonly busqueda$ = new Subject<string>();
  private suscripcion?: Subscription;

  constructor() {
    this.suscripcion = this.busqueda$
      .pipe(
        debounceTime(DEBOUNCE_MS),
        distinctUntilChanged(),
        switchMap((q) => {
          if (q.trim().length < this.minCaracteres) {
            return of<OpcionBusqueda[]>([]);
          }
          this.cargando = true;
          return this.buscar(q.trim()).pipe(catchError(() => of<OpcionBusqueda[]>([])));
        })
      )
      .subscribe((res) => {
        this.resultados = res ?? [];
        this.cargando = false;
        this.indiceActivo = -1;
      });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['seleccionInicial']) {
      this.seleccionActual = this.seleccionInicial;
    }
  }

  ngOnDestroy(): void {
    this.suscripcion?.unsubscribe();
  }

  mostrarLista(): boolean {
    return this.enfocado && this.texto.trim().length > 0;
  }

  onFocus(): void {
    this.enfocado = true;
  }

  onTextoCambiado(valor: string): void {
    this.texto = valor;
    this.busqueda$.next(valor);
  }

  onBlurConRetardo(): void {
    // Retardo corto para que el (mousedown) de "elegir" se dispare ANTES
    // de que el blur cierre la lista -si fuera (click), el blur ganaría
    // la carrera y la opción nunca se seleccionaría.
    setTimeout(() => {
      this.enfocado = false;
    }, 150);
  }

  onKeydown(evento: KeyboardEvent): void {
    if (!this.mostrarLista() || this.resultados.length === 0) return;
    if (evento.key === 'ArrowDown') {
      evento.preventDefault();
      this.indiceActivo = Math.min(this.indiceActivo + 1, this.resultados.length - 1);
    } else if (evento.key === 'ArrowUp') {
      evento.preventDefault();
      this.indiceActivo = Math.max(this.indiceActivo - 1, 0);
    } else if (evento.key === 'Enter' && this.indiceActivo >= 0) {
      evento.preventDefault();
      this.elegir(this.resultados[this.indiceActivo]);
    } else if (evento.key === 'Escape') {
      this.enfocado = false;
    }
  }

  elegir(opcion: OpcionBusqueda): void {
    this.seleccionActual = opcion;
    this.texto = '';
    this.resultados = [];
    this.enfocado = false;
    this.seleccion.emit(opcion);
  }

  limpiar(): void {
    this.seleccionActual = null;
    this.texto = '';
    this.resultados = [];
    this.seleccion.emit(null);
  }

  cambiar(): void {
    this.seleccionActual = null;
    this.texto = '';
  }
}
