import { Component, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';

import { ProblemDetailService } from '../core/problem-detail.service';
import { AuditoriaApiService, AuditoriaEvento, AuditoriaOpciones, FiltrosAuditoria } from './auditoria-api.service';
import { PageHeaderComponent } from '../shared/page-header/page-header.component';

const TAMANIO_PAGINA = 20;
const OPCION_TODOS = '';

/**
 * /auditoria — SOLO ADMIN (roleGuard en app.routes.ts): AuditoriaController
 * exige hasRole('ADMIN') en sus 2 rutas de solo lectura
 * (/api/admin/auditoria, /api/admin/auditoria/opciones).
 *
 * Los 3 selects de filtro (Usuario/Acción/Módulo) se pueblan con valores
 * REALMENTE presentes en la auditoría (GET .../opciones), no con una lista
 * fija: si nadie ha hecho login todavía, por ejemplo, el select de acciones
 * no ofrecerá "LOGIN_SUCCESS" como opción vacía sin sentido.
 *
 * El rango de fechas es por DÍA (input type="date"): "desde" se interpreta
 * como el inicio de ese día en UTC y "hasta" como el final, para que el
 * ADMIN nunca tenga que escribir una hora exacta para una consulta de
 * auditoría (uso pensado: "¿qué pasó el martes?", no "¿qué pasó a las
 * 14:32:07?").
 */
@Component({
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, PageHeaderComponent],
  template: `
  <app-page-header eyebrow="Administración" title="Auditoría" [hasActions]="false"></app-page-header>

  <p class="alert alert--danger" role="alert" aria-live="assertive" *ngIf="error()">
    <strong>Error:</strong> {{ error() }}
  </p>

  <form class="toolbar" [formGroup]="filtroForm" (ngSubmit)="buscar()" novalidate>
    <div class="field">
      <label for="f-usuario">Usuario</label>
      <select id="f-usuario" formControlName="usuario">
        <option [value]="opcionTodos">Todos</option>
        <option *ngFor="let u of opciones()?.usuarios" [value]="u">{{ u }}</option>
      </select>
    </div>
    <div class="field">
      <label for="f-accion">Acción</label>
      <select id="f-accion" formControlName="accion">
        <option [value]="opcionTodos">Todas</option>
        <option *ngFor="let a of opciones()?.acciones" [value]="a">{{ a }}</option>
      </select>
    </div>
    <div class="field">
      <label for="f-modulo">Módulo</label>
      <select id="f-modulo" formControlName="modulo">
        <option [value]="opcionTodos">Todos</option>
        <option *ngFor="let m of opciones()?.modulos" [value]="m">{{ m }}</option>
      </select>
    </div>
    <div class="field">
      <label for="f-desde">Desde</label>
      <input id="f-desde" type="date" formControlName="desde" />
    </div>
    <div class="field">
      <label for="f-hasta">Hasta</label>
      <input id="f-hasta" type="date" formControlName="hasta" />
    </div>
    <div class="field" style="align-self: flex-end;">
      <button type="submit" class="btn btn--primary">Filtrar</button>
      <button type="button" class="btn btn--secondary" (click)="limpiarFiltros()">Limpiar</button>
    </div>
  </form>

  <div class="table-wrap">
    <table class="table-clinico">
      <caption class="sr-only">Historial de auditoría</caption>
      <thead>
        <tr>
          <th scope="col">Fecha</th>
          <th scope="col">Usuario</th>
          <th scope="col">Acción</th>
          <th scope="col">Módulo / recurso</th>
          <th scope="col">Método</th>
          <th scope="col">Resultado</th>
        </tr>
      </thead>
      <tbody>
        <tr class="table-clinico__empty-row" *ngIf="!cargando() && eventos().length === 0">
          <td class="table-clinico__empty-cell" colspan="6">Sin eventos de auditoría para estos filtros.</td>
        </tr>
        <tr *ngFor="let e of eventos()">
          <td data-label="Fecha" class="data">{{ e.fechaHora | date: "d 'de' MMMM y, HH:mm:ss" }}</td>
          <td data-label="Usuario">{{ e.usuarioEmail ?? '—' }}</td>
          <td data-label="Acción" class="data">{{ e.accion }}</td>
          <td data-label="Módulo / recurso">
            {{ e.modulo }}<span *ngIf="e.recurso" class="field-hint"> — {{ e.recurso }}</span>
          </td>
          <td data-label="Método" class="data">{{ e.metodoHttp ?? '—' }}</td>
          <td data-label="Resultado">
            <span class="chip" [ngClass]="chipClaseResultado(e.resultado)">
              {{ e.resultado }}<span *ngIf="e.statusHttp"> ({{ e.statusHttp }})</span>
            </span>
          </td>
        </tr>
      </tbody>
    </table>
  </div>

  <nav class="pagination" aria-label="Paginación de auditoría" *ngIf="!cargando() && totalPaginas() > 0">
    <button type="button" class="btn btn--secondary btn--sm" (click)="irAPagina(pagina() - 1)" [disabled]="pagina() === 0">
      Anterior
    </button>
    <span class="pagination__status" aria-live="polite">
      Página {{ pagina() + 1 }} de {{ totalPaginas() }} ({{ totalElementos() }} eventos)
    </span>
    <button type="button" class="btn btn--secondary btn--sm" (click)="irAPagina(pagina() + 1)" [disabled]="pagina() + 1 >= totalPaginas()">
      Siguiente
    </button>
  </nav>
  `,
})
export class AuditoriaComponent implements OnInit {
  readonly opcionTodos = OPCION_TODOS;

  error = signal('');
  cargando = signal(false);
  opciones = signal<AuditoriaOpciones | null>(null);

  eventos = signal<AuditoriaEvento[]>([]);
  pagina = signal(0);
  totalPaginas = signal(0);
  totalElementos = signal(0);

  filtroForm = this.fb.group({
    usuario: OPCION_TODOS,
    accion: OPCION_TODOS,
    modulo: OPCION_TODOS,
    desde: '',
    hasta: '',
  });

  constructor(
    private fb: FormBuilder,
    private api: AuditoriaApiService,
    private problemDetail: ProblemDetailService
  ) {}

  ngOnInit(): void {
    this.cargarOpciones();
    this.cargarEventos();
  }

  private cargarOpciones(): void {
    this.api.opciones().subscribe({
      next: (o) => this.opciones.set(o),
      error: (err: HttpErrorResponse) => this.error.set(this.problemDetail.mensaje(err)),
    });
  }

  buscar(): void {
    this.pagina.set(0);
    this.cargarEventos();
  }

  limpiarFiltros(): void {
    this.filtroForm.reset({ usuario: OPCION_TODOS, accion: OPCION_TODOS, modulo: OPCION_TODOS, desde: '', hasta: '' });
    this.buscar();
  }

  irAPagina(nueva: number): void {
    if (nueva < 0 || nueva >= this.totalPaginas()) return;
    this.pagina.set(nueva);
    this.cargarEventos();
  }

  private cargarEventos(): void {
    this.error.set('');
    this.cargando.set(true);
    const v = this.filtroForm.getRawValue();
    const filtros: FiltrosAuditoria = {
      usuario: v.usuario || undefined,
      accion: v.accion || undefined,
      modulo: v.modulo || undefined,
      // "desde" (YYYY-MM-DD) -> inicio del dia en UTC; "hasta" -> final del dia en UTC.
      desde: v.desde ? `${v.desde}T00:00:00Z` : undefined,
      hasta: v.hasta ? `${v.hasta}T23:59:59Z` : undefined,
    };
    this.api.buscar(filtros, this.pagina(), TAMANIO_PAGINA).subscribe({
      next: (res) => {
        this.eventos.set(res.content ?? []);
        this.totalPaginas.set(res.totalPages ?? 0);
        this.totalElementos.set(res.totalElements ?? 0);
        this.cargando.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.cargando.set(false);
        this.error.set(this.problemDetail.mensaje(err));
      },
    });
  }

  chipClaseResultado(resultado: string): string {
    switch (resultado) {
      case 'SUCCESS':
        return 'chip--success';
      case 'BLOCKED':
        return 'chip--warning';
      default:
        return 'chip--danger';
    }
  }
}
