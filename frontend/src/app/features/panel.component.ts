import { Component, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ProblemDetailService } from '../core/problem-detail.service';
import { DashboardResumen, PanelApiService } from './panel-api.service';
import { Consulta, ConsultaApiService } from './consulta-api.service';
import { PageHeaderComponent } from '../shared/page-header/page-header.component';

const TAMANIO_ACTIVIDAD = 5;

type Preset = '7' | '30' | 'custom';

/**
 * IMPORTANTE — de dónde viene cada dato de esta pantalla:
 *
 *  - Los 5 indicadores vienen literalmente de GET /api/dashboard/resumen
 *    (fn_reporte_dashboard). `mascotasActivas`, `citasProgramadas` y
 *    `mascotasSinConsulta` son agregados GLOBALES: no cambian con el
 *    rango elegido (ver DashboardResumenResponse.java). Solo
 *    `consultasEnRango` y `vacunasEnRango` están acotados por
 *    desde/hasta. La plantilla agrupa los números exactamente según
 *    esta distinción real — nunca los presenta como si los 5
 *    pertenecieran al período seleccionado.
 *  - "Consultas recientes" reutiliza GET /api/consultas ordenado
 *    `fechaConsulta,desc`, página 0: como `fechaConsulta` nunca es
 *    futura (@PastOrPresent en el backend), esa página SIEMPRE contiene
 *    exactamente las N consultas más recientes de toda la clínica, sin
 *    importar cuántas existan en total — es una afirmación honesta.
 *  - Deliberadamente NO hay "Próximas citas" ni "Vacunas próximas": ni
 *    /api/citas ni /api/vacunas soportan filtrar por estado o por
 *    "fecha futura" en el servidor, así que una página ordenada no
 *    puede garantizar mostrar realmente las próximas — solo mostraría
 *    lo que sea más antiguo o más reciente del total, mezclando
 *    pasado/futuro y todos los estados. Ver informe de esta fase.
 */
@Component({
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, PageHeaderComponent],
  template: `
  <app-page-header eyebrow="Resumen operativo" title="Panel" [description]="descripcion()" [hasActions]="false">
  </app-page-header>

  <div class="toolbar" role="toolbar" aria-label="Acciones del panel">
    <button type="button" class="btn btn--ghost btn--sm" (click)="cargar()" [disabled]="cargando()">
      Actualizar
    </button>
  </div>

  <div class="panel-rango" role="group" aria-label="Rango del período">
    <button
      type="button"
      class="btn btn--sm"
      [ngClass]="preset() === '7' ? 'btn--primary' : 'btn--secondary'"
      [attr.aria-pressed]="preset() === '7'"
      (click)="elegirPreset('7')">
      Últimos 7 días
    </button>
    <button
      type="button"
      class="btn btn--sm"
      [ngClass]="preset() === '30' ? 'btn--primary' : 'btn--secondary'"
      [attr.aria-pressed]="preset() === '30'"
      (click)="elegirPreset('30')">
      Últimos 30 días
    </button>
    <button
      type="button"
      class="btn btn--sm"
      [ngClass]="preset() === 'custom' ? 'btn--primary' : 'btn--secondary'"
      [attr.aria-pressed]="preset() === 'custom'"
      (click)="abrirPersonalizado()">
      Rango personalizado
    </button>
  </div>

  <form
    *ngIf="mostrarPersonalizado()"
    class="panel panel--record"
    [formGroup]="formPersonalizado"
    (ngSubmit)="aplicarPersonalizado()"
    novalidate>
    <div class="field">
      <label for="f-desde">Desde</label>
      <input id="f-desde" type="date" formControlName="desde" [attr.max]="hoyIso" />
    </div>
    <div class="field">
      <label for="f-hasta">Hasta</label>
      <input id="f-hasta" type="date" formControlName="hasta" [attr.max]="hoyIso" />
    </div>
    <p class="field-error" *ngIf="errorRango()">{{ errorRango() }}</p>
    <button type="submit" class="btn btn--primary btn--sm">Aplicar rango</button>
  </form>

  <p class="alert alert--danger" role="alert" aria-live="assertive" *ngIf="error()">
    <strong>Error:</strong> {{ error() }}
    <button type="button" class="btn btn--ghost btn--sm" (click)="cargar()">Reintentar</button>
  </p>

  <span class="sr-only" role="status" aria-live="polite" *ngIf="cargando()">Actualizando indicadores del período…</span>

  <!-- ===== General: agregados globales, no dependen del rango ===== -->
  <section class="panel panel--record" aria-labelledby="titulo-general">
    <div class="panel__title">
      <h2 id="titulo-general">General</h2>
      <p class="field-hint">En toda la clínica — no depende del período seleccionado.</p>
    </div>
    <div class="panel-stats">
      <div class="panel-stat">
        <span class="panel-stat__valor skeleton-block" *ngIf="cargando() && !resumen()" aria-hidden="true"></span>
        <span class="panel-stat__valor" *ngIf="!(cargando() && !resumen())">{{ resumen()?.mascotasActivas ?? '—' }}</span>
        <span class="panel-stat__etiqueta">Mascotas activas</span>
      </div>
      <div class="panel-stat">
        <span class="panel-stat__valor skeleton-block" *ngIf="cargando() && !resumen()" aria-hidden="true"></span>
        <span class="panel-stat__valor" *ngIf="!(cargando() && !resumen())">{{ resumen()?.citasProgramadas ?? '—' }}</span>
        <span class="panel-stat__etiqueta">Citas programadas</span>
      </div>
    </div>
  </section>

  <!-- ===== En el período: SOLO estos 2 números están acotados por el rango ===== -->
  <section class="panel panel--record" aria-labelledby="titulo-periodo">
    <div class="panel__title">
      <h2 id="titulo-periodo">En el período</h2>
      <p class="field-hint" *ngIf="resumen() as r">
        Del <span class="data">{{ comoFechaLocal(r.desde) | date: "d 'de' MMMM y" }}</span>
        al <span class="data">{{ comoFechaLocal(r.hasta) | date: "d 'de' MMMM y" }}</span>.
      </p>
    </div>
    <div class="panel-stats">
      <div class="panel-stat">
        <span class="panel-stat__valor skeleton-block" *ngIf="cargando() && !resumen()" aria-hidden="true"></span>
        <span class="panel-stat__valor" *ngIf="!(cargando() && !resumen())">{{ resumen()?.consultasEnRango ?? '—' }}</span>
        <span class="panel-stat__etiqueta">Consultas en el período</span>
      </div>
      <div class="panel-stat">
        <span class="panel-stat__valor skeleton-block" *ngIf="cargando() && !resumen()" aria-hidden="true"></span>
        <span class="panel-stat__valor" *ngIf="!(cargando() && !resumen())">{{ resumen()?.vacunasEnRango ?? '—' }}</span>
        <span class="panel-stat__etiqueta">Vacunas aplicadas en el período</span>
      </div>
    </div>
  </section>

  <!-- ===== Mascotas sin consulta: tratamiento distinto, no es un KPI más ===== -->
  <p class="alert alert--warning" role="status" *ngIf="!cargando() && resumen() as r">
    <span *ngIf="r.mascotasSinConsulta > 0">
      <strong>{{ r.mascotasSinConsulta }}</strong>
      {{ r.mascotasSinConsulta === 1 ? 'mascota activa nunca ha tenido' : 'mascotas activas nunca han tenido' }}
      una consulta registrada. No depende del período seleccionado: cuenta todo el historial.
    </span>
    <span *ngIf="r.mascotasSinConsulta === 0">
      Todas las mascotas activas tienen al menos una consulta registrada.
    </span>
  </p>

  <!-- ===== Actividad: solo Consultas recientes — ver aviso de la clase sobre por qué no hay Citas/Vacunas aquí ===== -->
  <section class="panel panel--record" aria-labelledby="titulo-actividad">
    <div class="panel__title">
      <h2 id="titulo-actividad">Consultas recientes</h2>
      <p class="field-hint">Las {{ tamanioActividad }} consultas más recientes de toda la clínica.</p>
    </div>

    <p class="alert alert--danger" role="alert" *ngIf="errorActividad()">
      <strong>Error:</strong> {{ errorActividad() }}
      <button type="button" class="btn btn--ghost btn--sm" (click)="cargarActividad()">Reintentar</button>
    </p>

    <div *ngIf="cargandoActividad()" aria-hidden="true">
      <div class="panel-actividad-item" *ngFor="let fila of [1, 2, 3]">
        <span class="skeleton-block" style="width:60%"></span>
      </div>
    </div>

    <div *ngIf="!cargandoActividad() && !errorActividad()">
      <p class="field-hint" *ngIf="actividadConsultas().length === 0">Sin consultas registradas todavía.</p>

      <div class="panel-actividad-item" *ngFor="let c of actividadConsultas()">
        <div>
          <a class="record-link" [routerLink]="['/mascotas', c.mascotaId]">{{ c.mascotaNombre }}</a>
          <span class="panel-actividad-item__motivo"> — {{ c.motivo }}</span>
        </div>
        <time class="data" [attr.datetime]="c.fechaConsulta">{{ c.fechaConsulta | date: "d MMM y" }}</time>
      </div>
    </div>

    <a routerLink="/consultas" class="btn btn--ghost btn--sm">Ver todas las consultas</a>
  </section>
  `,
})
export class PanelComponent implements OnInit {
  resumen = signal<DashboardResumen | null>(null);
  cargando = signal(false);
  error = signal('');

  preset = signal<Preset>('30');
  mostrarPersonalizado = signal(false);
  errorRango = signal('');
  readonly hoyIso = this.formatearFechaIso(new Date());

  private desdeAplicado = '';
  private hastaAplicado = '';

  actividadConsultas = signal<Consulta[]>([]);
  cargandoActividad = signal(false);
  errorActividad = signal('');
  readonly tamanioActividad = TAMANIO_ACTIVIDAD;

  formPersonalizado = this.fb.group({
    desde: [''],
    hasta: [''],
  });

  constructor(
    private api: PanelApiService,
    private consultaApi: ConsultaApiService,
    private problemDetail: ProblemDetailService,
    private fb: FormBuilder
  ) {}

  ngOnInit(): void {
    this.elegirPreset('30');
    this.cargarActividad();
  }

  descripcion(): string {
    if (this.preset() === '7') return 'Estado general de la clínica y actividad de los últimos 7 días.';
    if (this.preset() === '30') return 'Estado general de la clínica y actividad de los últimos 30 días.';
    return 'Estado general de la clínica y actividad del rango seleccionado.';
  }

  // ---------- Rango ----------

  elegirPreset(p: '7' | '30'): void {
    this.preset.set(p);
    this.mostrarPersonalizado.set(false);
    this.errorRango.set('');
    const hasta = this.hoyIso;
    const desde = this.restarDiasIso(hasta, p === '7' ? 6 : 29);
    this.cargar(desde, hasta);
  }

  abrirPersonalizado(): void {
    this.preset.set('custom');
    this.mostrarPersonalizado.set(true);
    this.errorRango.set('');
    this.formPersonalizado.reset({
      desde: this.desdeAplicado || this.hoyIso,
      hasta: this.hastaAplicado || this.hoyIso,
    });
  }

  aplicarPersonalizado(): void {
    const { desde, hasta } = this.formPersonalizado.getRawValue();
    if (!desde || !hasta) {
      this.errorRango.set('Selecciona ambas fechas.');
      return;
    }
    if (desde > hasta) {
      this.errorRango.set("La fecha 'desde' no puede ser posterior a 'hasta'.");
      return;
    }
    this.errorRango.set('');
    this.cargar(desde, hasta);
  }

  // ---------- Carga ----------

  cargar(desde = this.desdeAplicado, hasta = this.hastaAplicado): void {
    if (!desde || !hasta) return;
    this.desdeAplicado = desde;
    this.hastaAplicado = hasta;
    this.error.set('');
    this.cargando.set(true);
    this.api.resumen(desde, hasta).subscribe({
      next: (res) => {
        this.resumen.set(res);
        this.cargando.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.cargando.set(false);
        this.error.set(this.problemDetail.mensaje(err));
      },
    });
  }

  cargarActividad(): void {
    this.cargandoActividad.set(true);
    this.errorActividad.set('');
    this.consultaApi.listar(0, TAMANIO_ACTIVIDAD, 'fechaConsulta,desc').subscribe({
      next: (res) => {
        this.actividadConsultas.set(res.content ?? []);
        this.cargandoActividad.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.cargandoActividad.set(false);
        this.errorActividad.set(this.problemDetail.mensaje(err));
      },
    });
  }

  // ---------- Presentación ----------

  /**
   * `resumen().desde`/`.hasta` son `LocalDate` (yyyy-MM-dd) sin hora. Se
   * parsean forzando medianoche LOCAL (mismo truco que calcularEdad/
   * estadoVacuna en shared/presentacion.ts) para que el DatePipe nunca
   * muestre un día distinto por interpretar la fecha como UTC.
   */
  comoFechaLocal(iso: string): Date {
    return new Date(`${iso}T00:00:00`);
  }

  private formatearFechaIso(d: Date): string {
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  }

  private restarDiasIso(hastaIso: string, dias: number): string {
    const d = new Date(`${hastaIso}T00:00:00`);
    d.setDate(d.getDate() - dias);
    return this.formatearFechaIso(d);
  }
}
