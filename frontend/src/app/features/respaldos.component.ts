import { Component, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ProblemDetailService } from '../core/problem-detail.service';
import {
  DiaSemana,
  EstadoRespaldo,
  RespaldoConfiguracion,
  RespaldoHistorial,
  RespaldosApiService,
} from './respaldos-api.service';
import { PageHeaderComponent } from '../shared/page-header/page-header.component';
import { IconComponent } from '../shared/icons/icon.component';

const TAMANIO_PAGINA = 20;

/**
 * /respaldos — SOLO ADMIN (roleGuard en app.routes.ts): RespaldoController
 * exige hasRole('ADMIN') en las 4 rutas de /api/admin/respaldos/**, sin
 * excepción para ningún otro rol.
 *
 * El respaldo real ocurre siempre en segundo plano en el backend
 * (RespaldoEjecucionService): "Generar respaldo ahora" solo confirma que
 * quedó EN_PROCESO (202) y esta pantalla vuelve a cargar el historial para
 * que el ADMIN vea la fila avanzar hacia EXITOSO/FALLIDO -no hay ningún
 * WebSocket ni polling automático: se refresca al guardar/generar, igual
 * que el resto de pantallas administrativas del proyecto.
 */
@Component({
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, PageHeaderComponent, IconComponent],
  template: `
  <app-page-header eyebrow="Administración" title="Respaldos" [hasActions]="false"></app-page-header>

  <p class="alert alert--danger" role="alert" aria-live="assertive" *ngIf="error()">
    <strong>Error:</strong> {{ error() }}
  </p>
  <p class="alert alert--success" role="status" aria-live="polite" *ngIf="mensajeExito()">
    <strong>Listo:</strong> {{ mensajeExito() }}
  </p>

  <span class="sr-only" role="status" aria-live="polite" *ngIf="cargandoConfiguracion()">Cargando configuración…</span>

  <div class="panel" *ngIf="!cargandoConfiguracion()">
    <form [formGroup]="configForm" (ngSubmit)="guardarConfiguracion()" novalidate>
      <div class="field">
        <label class="checkbox-row">
          <input type="checkbox" formControlName="activo" />
          Respaldos automáticos
        </label>
        <p class="field-hint">
          {{ configForm.value.activo ? 'Activados: se generan solos cada semana, el día y la hora de abajo.' : 'Desactivados: solo "Generar respaldo ahora" crea nuevos respaldos.' }}
        </p>
      </div>

      <div style="display: flex; gap: var(--space-3); align-items: flex-start;">
        <div class="field">
          <label for="r-dia">Día</label>
          <select id="r-dia" formControlName="diaSemana">
            <option *ngFor="let d of diasSemana" [value]="d">{{ etiquetaDia(d) }}</option>
          </select>
        </div>
        <div class="field">
          <label for="r-hora">Hora</label>
          <input id="r-hora" type="time" formControlName="hora" />
        </div>
      </div>
      <p class="field-hint" *ngIf="configForm.get('hora')?.invalid && configForm.get('hora')?.touched">
        Seleccione una hora válida.
      </p>

      <dl class="record-meta-grid" style="margin-top: var(--space-4);">
        <div>
          <dt>Último respaldo</dt>
          <dd class="data">{{ configuracion()?.ultimoRespaldoEn ? (configuracion()!.ultimoRespaldoEn | date: "d 'de' MMMM y, HH:mm") : 'Todavía no se ha generado ninguno' }}</dd>
        </div>
        <div>
          <dt>Próximo respaldo</dt>
          <dd class="data">{{ configuracion()?.proximoRespaldoEn ? (configuracion()!.proximoRespaldoEn | date: "d 'de' MMMM y, HH:mm") : 'No programado' }}</dd>
        </div>
      </dl>

      <div class="modal-panel__actions" style="margin-top: var(--space-4);">
        <button type="submit" class="btn btn--primary" [disabled]="guardandoConfiguracion()">
          {{ guardandoConfiguracion() ? 'Guardando…' : 'Guardar configuración' }}
        </button>
        <button type="button" class="btn btn--secondary" (click)="generarRespaldoAhora()" [disabled]="generandoRespaldo()">
          <app-icon name="respaldos"></app-icon>
          {{ generandoRespaldo() ? 'Generando…' : 'Generar respaldo ahora' }}
        </button>
      </div>
    </form>
  </div>

  <h2 id="historial-titulo" class="panel__title-inline" style="margin-top: var(--space-6);">Historial de respaldos</h2>

  <div class="table-wrap">
    <table class="table-clinico">
      <caption class="sr-only">Historial de respaldos</caption>
      <thead>
        <tr>
          <th scope="col">Fecha</th>
          <th scope="col">Tipo</th>
          <th scope="col">Estado</th>
          <th scope="col">Duración</th>
          <th scope="col">Tamaño</th>
        </tr>
      </thead>
      <tbody>
        <tr class="table-clinico__empty-row" *ngIf="!cargandoHistorial() && historial().length === 0">
          <td class="table-clinico__empty-cell" colspan="5">Sin respaldos generados todavía.</td>
        </tr>
        <tr *ngFor="let h of historial()">
          <td data-label="Fecha" class="data">{{ h.iniciadoEn | date: "d 'de' MMMM y, HH:mm" }}</td>
          <td data-label="Tipo">{{ etiquetaTipo(h.tipo) }}</td>
          <td data-label="Estado">
            <span class="chip" [ngClass]="chipClaseEstado(h.estado)">{{ etiquetaEstado(h.estado) }}</span>
          </td>
          <td data-label="Duración" class="data">{{ formatearDuracion(h.duracionMs) }}</td>
          <td data-label="Tamaño" class="data">{{ formatearTamanio(h.tamanoBytes) }}</td>
        </tr>
      </tbody>
    </table>
  </div>

  <nav class="pagination" aria-label="Paginación del historial de respaldos" *ngIf="!cargandoHistorial() && totalPaginas() > 0">
    <button type="button" class="btn btn--secondary btn--sm" (click)="irAPagina(pagina() - 1)" [disabled]="pagina() === 0">
      Anterior
    </button>
    <span class="pagination__status" aria-live="polite">
      Página {{ pagina() + 1 }} de {{ totalPaginas() }} ({{ totalElementos() }} respaldos)
    </span>
    <button type="button" class="btn btn--secondary btn--sm" (click)="irAPagina(pagina() + 1)" [disabled]="pagina() + 1 >= totalPaginas()">
      Siguiente
    </button>
  </nav>
  `,
})
export class RespaldosComponent implements OnInit {
  error = signal('');
  mensajeExito = signal('');

  configuracion = signal<RespaldoConfiguracion | null>(null);
  cargandoConfiguracion = signal(false);
  guardandoConfiguracion = signal(false);
  generandoRespaldo = signal(false);

  readonly diasSemana: DiaSemana[] = [
    'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY',
  ];

  configForm = this.fb.group({
    activo: [false],
    diaSemana: ['MONDAY' as DiaSemana, [Validators.required]],
    hora: ['02:00', [Validators.required]],
  });

  historial = signal<RespaldoHistorial[]>([]);
  cargandoHistorial = signal(false);
  pagina = signal(0);
  totalPaginas = signal(0);
  totalElementos = signal(0);

  constructor(
    private fb: FormBuilder,
    private api: RespaldosApiService,
    private problemDetail: ProblemDetailService
  ) {}

  ngOnInit(): void {
    this.cargarConfiguracion();
    this.cargarHistorial();
  }

  private mostrarExito(mensaje: string): void {
    this.mensajeExito.set(mensaje);
    setTimeout(() => {
      if (this.mensajeExito() === mensaje) this.mensajeExito.set('');
    }, 4000);
  }

  private cargarConfiguracion(): void {
    this.cargandoConfiguracion.set(true);
    this.api.obtenerConfiguracion().subscribe({
      next: (c) => {
        this.configuracion.set(c);
        this.configForm.reset({
          activo: c.activo,
          diaSemana: c.diaSemana,
          // El backend devuelve "HH:mm:ss" (java.time.LocalTime); <input type="time"> solo acepta "HH:mm".
          hora: c.hora.substring(0, 5),
        });
        this.cargandoConfiguracion.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.cargandoConfiguracion.set(false);
        this.error.set(this.problemDetail.mensaje(err));
      },
    });
  }

  guardarConfiguracion(): void {
    this.error.set('');
    if (this.configForm.invalid) {
      this.configForm.markAllAsTouched();
      return;
    }
    const v = this.configForm.getRawValue();
    this.guardandoConfiguracion.set(true);
    this.api
      .guardarConfiguracion({
        activo: !!v.activo,
        diaSemana: v.diaSemana as DiaSemana,
        hora: v.hora as string,
      })
      .subscribe({
        next: (c) => {
          this.guardandoConfiguracion.set(false);
          this.configuracion.set(c);
          this.mostrarExito('Configuración de respaldos guardada.');
        },
        error: (err: HttpErrorResponse) => {
          this.guardandoConfiguracion.set(false);
          this.error.set(this.problemDetail.mensaje(err));
        },
      });
  }

  generarRespaldoAhora(): void {
    this.error.set('');
    this.generandoRespaldo.set(true);
    this.api.generarRespaldoAhora().subscribe({
      next: () => {
        this.generandoRespaldo.set(false);
        this.mostrarExito('Respaldo iniciado. Revisa el historial para ver su resultado.');
        this.pagina.set(0);
        this.cargarHistorial();
        this.cargarConfiguracion();
      },
      error: (err: HttpErrorResponse) => {
        this.generandoRespaldo.set(false);
        // 409 (ya hay uno en proceso) llega con el mismo formato de
        // problem+json que cualquier otro error: el mensaje ya es
        // suficientemente claro para mostrarlo tal cual.
        this.error.set(this.problemDetail.mensaje(err));
      },
    });
  }

  private cargarHistorial(): void {
    this.cargandoHistorial.set(true);
    this.api.historial(this.pagina(), TAMANIO_PAGINA).subscribe({
      next: (res) => {
        this.historial.set(res.content ?? []);
        this.totalPaginas.set(res.totalPages ?? 0);
        this.totalElementos.set(res.totalElements ?? 0);
        this.cargandoHistorial.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.cargandoHistorial.set(false);
        this.error.set(this.problemDetail.mensaje(err));
      },
    });
  }

  irAPagina(nueva: number): void {
    if (nueva < 0 || nueva >= this.totalPaginas()) return;
    this.pagina.set(nueva);
    this.cargarHistorial();
  }

  etiquetaDia(dia: DiaSemana): string {
    switch (dia) {
      case 'MONDAY':
        return 'Lunes';
      case 'TUESDAY':
        return 'Martes';
      case 'WEDNESDAY':
        return 'Miércoles';
      case 'THURSDAY':
        return 'Jueves';
      case 'FRIDAY':
        return 'Viernes';
      case 'SATURDAY':
        return 'Sábado';
      case 'SUNDAY':
        return 'Domingo';
    }
  }

  etiquetaTipo(tipo: RespaldoHistorial['tipo']): string {
    return tipo === 'MANUAL' ? 'Manual' : 'Automático';
  }

  etiquetaEstado(estado: EstadoRespaldo): string {
    switch (estado) {
      case 'EN_PROCESO':
        return 'En proceso';
      case 'EXITOSO':
        return 'Exitoso';
      case 'FALLIDO':
        return 'Fallido';
    }
  }

  chipClaseEstado(estado: EstadoRespaldo): string {
    switch (estado) {
      case 'EN_PROCESO':
        return 'chip--warning';
      case 'EXITOSO':
        return 'chip--success';
      case 'FALLIDO':
        return 'chip--danger';
    }
  }

  formatearDuracion(duracionMs: number | null): string {
    if (duracionMs == null) return '—';
    const segundos = duracionMs / 1000;
    if (segundos < 60) return `${segundos.toFixed(1)} s`;
    const minutos = Math.floor(segundos / 60);
    const resto = Math.round(segundos % 60);
    return `${minutos} min ${resto} s`;
  }

  formatearTamanio(bytes: number | null): string {
    if (bytes == null) return '—';
    if (bytes < 1024) return `${bytes} B`;
    const kb = bytes / 1024;
    if (kb < 1024) return `${kb.toFixed(1)} KB`;
    const mb = kb / 1024;
    if (mb < 1024) return `${mb.toFixed(1)} MB`;
    return `${(mb / 1024).toFixed(2)} GB`;
  }
}
