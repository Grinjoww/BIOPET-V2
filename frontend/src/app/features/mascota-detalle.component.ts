import { Component, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { ProblemDetailService } from '../core/problem-detail.service';
import { inicialesDe } from '../core/roles';
import { Mascota, MascotaApiService } from './mascota-api.service';
import { Vacuna, VacunaApiService } from './vacuna-api.service';
import { EspecieApiService, InfoEspecie } from './especie-api.service';
import { Cita, CitaApiService } from './cita-api.service';
import { Consulta, ConsultaApiService } from './consulta-api.service';
import { PageHeaderComponent } from '../shared/page-header/page-header.component';
import { IconComponent } from '../shared/icons/icon.component';
import {
  calcularEdad,
  chipClaseEstadoCita,
  chipClaseEstadoVacuna,
  etiquetaEstadoVacuna,
  estadoVacuna,
  formatearExpediente,
} from '../shared/presentacion';

type TabFicha = 'vacunas' | 'especie' | 'citas' | 'consultas';

const TAMANIO_PAGINA_VACUNAS = 10;
const TAMANIO_PAGINA_CITAS = 10;
const TAMANIO_PAGINA_CONSULTAS = 5;

/**
 * Ficha de mascota (/mascotas/:id). Solo lectura en esta fase: el
 * expediente clínico digital de un paciente. Cada dato que muestra viene
 * de un endpoint real ya verificado:
 *
 *   - cabecera:  GET /api/mascotas/{id}             (MascotaResponse)
 *   - vacunas:   GET /api/vacunas/mascota/{id}       (VacunaResponse[])
 *   - citas:     GET /api/citas/mascota/{id}         (CitaResponse[])
 *   - consultas: GET /api/consultas/mascota/{id}     (ConsultaResponse[])
 *   - especie:   GET /api/externa/especies?especie=  (ExternalApiResponse)
 *
 * Citas y Consultas se cargan de forma perezosa (como Especie), solo al
 * activar la pestaña por primera vez, para no disparar 4 peticiones en
 * cada carga de la ficha cuando el usuario puede que solo mire Vacunas
 * (la pestaña por defecto, que sí se carga de inmediato).
 */
@Component({
  standalone: true,
  imports: [CommonModule, RouterLink, PageHeaderComponent, IconComponent],
  template: `
  <app-page-header
    eyebrow="Pacientes"
    [breadcrumb]="[{ label: 'Mascotas', path: '/mascotas' }, { label: mascota()?.nombre ?? 'Expediente' }]"
    [title]="mascota()?.nombre ?? 'Expediente'"
    [description]="mascota() ? (formatearExpediente(mascota()!.id) + ' · ' + mascota()!.especie + ' · ' + mascota()!.raza) : undefined"
    [hasActions]="false">
  </app-page-header>

  <span class="sr-only" role="status" aria-live="polite" *ngIf="cargando()">Cargando expediente…</span>

  <div class="panel" *ngIf="cargando()" aria-hidden="true">
    <div class="record-header">
      <span class="skeleton-block" style="width:64px;height:64px;border-radius:8px"></span>
      <div class="record-meta-grid">
        <div *ngFor="let i of [1,2,3,4,5,6]"><span class="skeleton-block"></span></div>
      </div>
    </div>
  </div>

  <div class="panel" *ngIf="!cargando() && error()">
    <p class="alert alert--danger" role="alert" aria-live="assertive">
      <strong>Error:</strong> {{ error() }}
    </p>
    <a routerLink="/mascotas" class="btn btn--secondary">
      <app-icon name="chevron-left"></app-icon>
      Volver a Mascotas
    </a>
  </div>

  <ng-container *ngIf="!cargando() && !error() && mascota() as m">
    <!-- ===== Cabecera de paciente ===== -->
    <section class="panel panel--record" aria-labelledby="resumen-titulo">
      <h2 id="resumen-titulo" class="sr-only">Resumen del expediente</h2>
      <div class="record-header">
        <!-- Sin foto: MascotaResponse no tiene ningún campo de imagen (auditado
             contra entidad, DTOs y schema — ver informe de esta fase). El
             fallback son las iniciales, nunca una silueta genérica de stock. -->
        <span class="record-header__avatar" aria-hidden="true" title="Sin foto registrada">{{ inicialesDe(m.nombre) }}</span>
        <dl class="record-meta-grid">
          <div>
            <dt>Expediente</dt>
            <dd class="data">{{ formatearExpediente(m.id) }}</dd>
          </div>
          <div>
            <dt>Especie</dt>
            <dd>{{ m.especie }}</dd>
          </div>
          <div>
            <dt>Raza</dt>
            <dd>{{ m.raza }}</dd>
          </div>
          <div>
            <dt>Nace</dt>
            <dd class="data">{{ m.fechaNacimiento }}</dd>
          </div>
          <div>
            <dt>Edad</dt>
            <dd>{{ calcularEdad(m.fechaNacimiento) }}</dd>
          </div>
          <div>
            <dt>Dueño</dt>
            <dd>{{ m.duenioNombre }}</dd>
          </div>
        </dl>
      </div>
    </section>

    <!-- ===== Tabs ===== -->
    <div class="tabs">
      <div class="tabs__list" role="tablist" aria-label="Secciones del expediente">
        <button
          type="button"
          role="tab"
          id="tab-vacunas"
          class="tab-btn"
          [attr.aria-selected]="tabActiva() === 'vacunas'"
          aria-controls="panel-vacunas"
          (click)="irATab('vacunas')">
          <app-icon name="vacunas" [size]="18"></app-icon>
          Vacunas
        </button>
        <button
          type="button"
          role="tab"
          id="tab-especie"
          class="tab-btn"
          [attr.aria-selected]="tabActiva() === 'especie'"
          aria-controls="panel-especie"
          (click)="irATab('especie')">
          Especie
        </button>
        <button
          type="button"
          role="tab"
          id="tab-citas"
          class="tab-btn"
          [attr.aria-selected]="tabActiva() === 'citas'"
          aria-controls="panel-citas"
          (click)="irATab('citas')">
          <app-icon name="citas" [size]="18"></app-icon>
          Citas
        </button>
        <button
          type="button"
          role="tab"
          id="tab-consultas"
          class="tab-btn"
          [attr.aria-selected]="tabActiva() === 'consultas'"
          aria-controls="panel-consultas"
          (click)="irATab('consultas')">
          <app-icon name="consultas" [size]="18"></app-icon>
          Consultas
        </button>
      </div>

      <!-- ===== Vacunas ===== -->
      <div id="panel-vacunas" class="tab-panel" role="tabpanel" aria-labelledby="tab-vacunas" *ngIf="tabActiva() === 'vacunas'">
        <span class="sr-only" role="status" aria-live="polite" *ngIf="cargandoVacunas()">Cargando vacunas…</span>

        <p class="alert alert--danger" role="alert" *ngIf="errorVacunas()">
          <strong>Error:</strong> {{ errorVacunas() }}
        </p>

        <div class="table-wrap" *ngIf="!errorVacunas() && (cargandoVacunas() || vacunas().length > 0)">
          <table class="table-clinico">
            <caption class="sr-only">Vacunas registradas de {{ m.nombre }}</caption>
            <thead>
              <tr>
                <th scope="col">Tipo</th>
                <th scope="col">Aplicada</th>
                <th scope="col">Próxima dosis</th>
                <th scope="col">Veterinario</th>
                <th scope="col">Observaciones</th>
              </tr>
            </thead>
            <tbody>
              <ng-container *ngIf="cargandoVacunas()">
                <tr class="skeleton-row" *ngFor="let fila of [1,2,3]" aria-hidden="true">
                  <td><span class="skeleton-block"></span></td>
                  <td><span class="skeleton-block"></span></td>
                  <td><span class="skeleton-block"></span></td>
                  <td><span class="skeleton-block"></span></td>
                  <td><span class="skeleton-block"></span></td>
                </tr>
              </ng-container>
              <ng-container *ngIf="!cargandoVacunas()">
                <tr *ngFor="let v of vacunas()">
                  <td data-label="Tipo">{{ v.tipo }}</td>
                  <td data-label="Aplicada" class="data">{{ v.fechaAplicacion }}</td>
                  <td data-label="Próxima dosis">
                    <span class="data" *ngIf="v.proximaFecha">{{ v.proximaFecha }}</span>
                    <span class="chip" [ngClass]="chipClaseEstadoVacuna(estadoVacuna(v.proximaFecha))">
                      {{ etiquetaEstadoVacuna(estadoVacuna(v.proximaFecha)) }}
                    </span>
                  </td>
                  <td data-label="Veterinario">{{ v.veterinarioNombre ?? 'Sin veterinario asignado' }}</td>
                  <td data-label="Observaciones">{{ v.observaciones ?? '—' }}</td>
                </tr>
              </ng-container>
            </tbody>
          </table>
        </div>

        <div class="empty-state" *ngIf="!errorVacunas() && !cargandoVacunas() && vacunas().length === 0">
          <p>Sin vacunas registradas todavía.</p>
          <p>Cuando se registre una vacuna para {{ m.nombre }}, aparecerá aquí.</p>
        </div>

        <nav class="pagination" aria-label="Paginación de vacunas" *ngIf="!cargandoVacunas() && totalPaginasVacunas() > 1">
          <button type="button" class="btn btn--secondary btn--sm" (click)="irAPaginaVacunas(paginaVacunas() - 1)" [disabled]="paginaVacunas() === 0">
            Anterior
          </button>
          <span class="pagination__status" aria-live="polite">
            Página {{ paginaVacunas() + 1 }} de {{ totalPaginasVacunas() }}
          </span>
          <button type="button" class="btn btn--secondary btn--sm" (click)="irAPaginaVacunas(paginaVacunas() + 1)" [disabled]="paginaVacunas() + 1 >= totalPaginasVacunas()">
            Siguiente
          </button>
        </nav>
      </div>

      <!-- ===== Especie ===== -->
      <div id="panel-especie" class="tab-panel" role="tabpanel" aria-labelledby="tab-especie" *ngIf="tabActiva() === 'especie'">
        <span class="sr-only" role="status" aria-live="polite" *ngIf="cargandoEspecie()">Consultando información de la especie…</span>

        <div class="record-meta-grid" *ngIf="infoEspecie() as info">
          <div>
            <dt>Nombre científico</dt>
            <dd><em>{{ info.nombreCientifico ?? 'No disponible' }}</em></dd>
          </div>
          <div>
            <dt>Hábitat</dt>
            <dd>{{ info.habitat ?? 'No disponible' }}</dd>
          </div>
          <div>
            <dt>Dieta</dt>
            <dd>{{ info.dieta ?? 'No disponible' }}</dd>
          </div>
          <div>
            <dt>Consultado</dt>
            <dd class="data">{{ info.consultadoEn | date: 'short' }} · {{ info.origen }}</dd>
          </div>
        </div>

        <div class="unavailable-card" *ngIf="especieNoDisponible() && !cargandoEspecie()">
          <p class="unavailable-card__title">Información no disponible para «{{ m.especie }}»</p>
          <p>No pudimos obtener información externa de esta especie en este momento.</p>
          <p class="unavailable-card__note">
            Esta sección depende de un servicio externo (API Ninjas) que puede no
            reconocer todos los términos o estar temporalmente no disponible. No
            afecta al resto del expediente.
          </p>
        </div>
      </div>

      <!-- ===== Citas ===== -->
      <div id="panel-citas" class="tab-panel" role="tabpanel" aria-labelledby="tab-citas" *ngIf="tabActiva() === 'citas'">
        <span class="sr-only" role="status" aria-live="polite" *ngIf="cargandoCitas()">Cargando citas…</span>

        <p class="alert alert--danger" role="alert" *ngIf="errorCitas()">
          <strong>Error:</strong> {{ errorCitas() }}
        </p>

        <div class="table-wrap" *ngIf="!errorCitas() && (cargandoCitas() || citas().length > 0)">
          <table class="table-clinico">
            <caption class="sr-only">Citas de {{ m.nombre }}</caption>
            <thead>
              <tr>
                <th scope="col">Fecha y hora</th>
                <th scope="col">Estado</th>
                <th scope="col">Veterinario</th>
                <th scope="col">Motivo</th>
              </tr>
            </thead>
            <tbody>
              <ng-container *ngIf="cargandoCitas()">
                <tr class="skeleton-row" *ngFor="let fila of [1,2,3]" aria-hidden="true">
                  <td><span class="skeleton-block"></span></td>
                  <td><span class="skeleton-block"></span></td>
                  <td><span class="skeleton-block"></span></td>
                  <td><span class="skeleton-block"></span></td>
                </tr>
              </ng-container>
              <ng-container *ngIf="!cargandoCitas()">
                <tr *ngFor="let c of citas()">
                  <td data-label="Fecha y hora" class="data">{{ c.fechaHora | date: 'medium' }}</td>
                  <td data-label="Estado">
                    <span class="chip" [ngClass]="chipClaseEstadoCita(c.estado)">{{ c.estado }}</span>
                  </td>
                  <td data-label="Veterinario">{{ c.veterinarioNombre }}</td>
                  <td data-label="Motivo">{{ c.motivo ?? '—' }}</td>
                </tr>
              </ng-container>
            </tbody>
          </table>
        </div>

        <div class="empty-state" *ngIf="!errorCitas() && !cargandoCitas() && citas().length === 0">
          <p>Sin citas registradas todavía.</p>
          <p>Cuando se agende una cita para {{ m.nombre }}, aparecerá aquí.</p>
        </div>

        <nav class="pagination" aria-label="Paginación de citas" *ngIf="!cargandoCitas() && totalPaginasCitas() > 1">
          <button type="button" class="btn btn--secondary btn--sm" (click)="irAPaginaCitas(paginaCitas() - 1)" [disabled]="paginaCitas() === 0">
            Anterior
          </button>
          <span class="pagination__status" aria-live="polite">
            Página {{ paginaCitas() + 1 }} de {{ totalPaginasCitas() }}
          </span>
          <button type="button" class="btn btn--secondary btn--sm" (click)="irAPaginaCitas(paginaCitas() + 1)" [disabled]="paginaCitas() + 1 >= totalPaginasCitas()">
            Siguiente
          </button>
        </nav>
      </div>

      <!-- ===== Consultas =====
           Registro clínico, no agenda: cada consulta se presenta como una
           entrada sellada con sus propios pares etiqueta/valor, no como
           fila de tabla — visualmente distinta de Citas a propósito. -->
      <div id="panel-consultas" class="tab-panel" role="tabpanel" aria-labelledby="tab-consultas" *ngIf="tabActiva() === 'consultas'">
        <span class="sr-only" role="status" aria-live="polite" *ngIf="cargandoConsultas()">Cargando consultas…</span>

        <p class="alert alert--danger" role="alert" *ngIf="errorConsultas()">
          <strong>Error:</strong> {{ errorConsultas() }}
        </p>

        <div *ngIf="cargandoConsultas()" aria-hidden="true">
          <div class="panel panel--record record-entry" *ngFor="let fila of [1,2]">
            <span class="skeleton-block" style="width:40%"></span>
          </div>
        </div>

        <div *ngIf="!cargandoConsultas() && !errorConsultas()">
          <article class="panel panel--record record-entry" *ngFor="let c of consultas()">
            <div class="record-header">
              <div class="record-meta-grid">
                <div>
                  <dt>Fecha</dt>
                  <dd class="data">{{ c.fechaConsulta | date: 'medium' }}</dd>
                </div>
                <div>
                  <dt>Veterinario</dt>
                  <dd>{{ c.veterinarioNombre }}</dd>
                </div>
                <div>
                  <dt>Motivo</dt>
                  <dd>{{ c.motivo }}</dd>
                </div>
                <div>
                  <dt>Diagnóstico</dt>
                  <dd>{{ c.diagnostico ?? 'No registrado' }}</dd>
                </div>
                <div>
                  <dt>Tratamiento</dt>
                  <dd>{{ c.tratamiento ?? 'No registrado' }}</dd>
                </div>
                <div>
                  <dt>Observaciones</dt>
                  <dd>{{ c.observaciones ?? 'No registradas' }}</dd>
                </div>
              </div>
            </div>
          </article>
        </div>

        <div class="empty-state" *ngIf="!errorConsultas() && !cargandoConsultas() && consultas().length === 0">
          <p>Sin consultas registradas todavía.</p>
          <p>Cuando se registre una consulta para {{ m.nombre }}, aparecerá aquí.</p>
        </div>

        <nav class="pagination" aria-label="Paginación de consultas" *ngIf="!cargandoConsultas() && totalPaginasConsultas() > 1">
          <button type="button" class="btn btn--secondary btn--sm" (click)="irAPaginaConsultas(paginaConsultas() - 1)" [disabled]="paginaConsultas() === 0">
            Anterior
          </button>
          <span class="pagination__status" aria-live="polite">
            Página {{ paginaConsultas() + 1 }} de {{ totalPaginasConsultas() }}
          </span>
          <button type="button" class="btn btn--secondary btn--sm" (click)="irAPaginaConsultas(paginaConsultas() + 1)" [disabled]="paginaConsultas() + 1 >= totalPaginasConsultas()">
            Siguiente
          </button>
        </nav>
      </div>
    </div>
  </ng-container>
  `,
})
export class MascotaDetalleComponent implements OnInit {
  readonly formatearExpediente = formatearExpediente;
  readonly calcularEdad = calcularEdad;
  readonly estadoVacuna = estadoVacuna;
  readonly etiquetaEstadoVacuna = etiquetaEstadoVacuna;
  readonly chipClaseEstadoVacuna = chipClaseEstadoVacuna;
  readonly chipClaseEstadoCita = chipClaseEstadoCita;
  readonly inicialesDe = inicialesDe;

  mascota = signal<Mascota | null>(null);
  cargando = signal(true);
  error = signal('');

  tabActiva = signal<TabFicha>('vacunas');

  vacunas = signal<Vacuna[]>([]);
  paginaVacunas = signal(0);
  totalPaginasVacunas = signal(0);
  cargandoVacunas = signal(false);
  errorVacunas = signal('');

  citas = signal<Cita[]>([]);
  paginaCitas = signal(0);
  totalPaginasCitas = signal(0);
  cargandoCitas = signal(false);
  errorCitas = signal('');
  private citasCargadas = false;

  consultas = signal<Consulta[]>([]);
  paginaConsultas = signal(0);
  totalPaginasConsultas = signal(0);
  cargandoConsultas = signal(false);
  errorConsultas = signal('');
  private consultasCargadas = false;

  infoEspecie = signal<InfoEspecie | null>(null);
  cargandoEspecie = signal(false);
  especieNoDisponible = signal(false);
  private especieCargadaPara: string | null = null;

  constructor(
    private route: ActivatedRoute,
    private mascotaApi: MascotaApiService,
    private vacunaApi: VacunaApiService,
    private citaApi: CitaApiService,
    private consultaApi: ConsultaApiService,
    private especieApi: EspecieApiService,
    private problemDetail: ProblemDetailService
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.cargarMascota(id);
  }

  private cargarMascota(id: number): void {
    this.cargando.set(true);
    this.error.set('');
    this.mascotaApi.buscar(id).subscribe({
      next: (m) => {
        this.mascota.set(m);
        this.cargando.set(false);
        this.cargarVacunas(m.id, 0);
      },
      error: (err: HttpErrorResponse) => {
        this.cargando.set(false);
        this.error.set(this.problemDetail.mensaje(err));
      },
    });
  }

  irATab(tab: TabFicha): void {
    this.tabActiva.set(tab);
    const m = this.mascota();
    if (!m) return;

    if (tab === 'especie' && this.especieCargadaPara !== m.especie) {
      this.cargarEspecie(m.especie);
    } else if (tab === 'citas' && !this.citasCargadas) {
      this.cargarCitas(m.id, 0);
    } else if (tab === 'consultas' && !this.consultasCargadas) {
      this.cargarConsultas(m.id, 0);
    }
  }

  // ---------- Vacunas ----------

  private cargarVacunas(mascotaId: number, pagina: number): void {
    this.cargandoVacunas.set(true);
    this.errorVacunas.set('');
    this.vacunaApi.listarPorMascota(mascotaId, pagina, TAMANIO_PAGINA_VACUNAS).subscribe({
      next: (res) => {
        this.vacunas.set(res.content ?? []);
        this.totalPaginasVacunas.set(res.totalPages ?? 0);
        this.paginaVacunas.set(pagina);
        this.cargandoVacunas.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.cargandoVacunas.set(false);
        this.errorVacunas.set(this.problemDetail.mensaje(err));
      },
    });
  }

  irAPaginaVacunas(nuevaPagina: number): void {
    const m = this.mascota();
    if (!m || nuevaPagina < 0 || nuevaPagina >= this.totalPaginasVacunas()) return;
    this.cargarVacunas(m.id, nuevaPagina);
  }

  // ---------- Citas (GET /api/citas/mascota/{id}, solo lectura) ----------

  private cargarCitas(mascotaId: number, pagina: number): void {
    this.cargandoCitas.set(true);
    this.errorCitas.set('');
    this.citaApi.listarPorMascota(mascotaId, pagina, TAMANIO_PAGINA_CITAS).subscribe({
      next: (res) => {
        this.citas.set(res.content ?? []);
        this.totalPaginasCitas.set(res.totalPages ?? 0);
        this.paginaCitas.set(pagina);
        this.citasCargadas = true;
        this.cargandoCitas.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.cargandoCitas.set(false);
        this.errorCitas.set(this.problemDetail.mensaje(err));
      },
    });
  }

  irAPaginaCitas(nuevaPagina: number): void {
    const m = this.mascota();
    if (!m || nuevaPagina < 0 || nuevaPagina >= this.totalPaginasCitas()) return;
    this.cargarCitas(m.id, nuevaPagina);
  }

  // ---------- Consultas (GET /api/consultas/mascota/{id}, solo lectura) ----------

  private cargarConsultas(mascotaId: number, pagina: number): void {
    this.cargandoConsultas.set(true);
    this.errorConsultas.set('');
    this.consultaApi.listarPorMascota(mascotaId, pagina, TAMANIO_PAGINA_CONSULTAS).subscribe({
      next: (res) => {
        this.consultas.set(res.content ?? []);
        this.totalPaginasConsultas.set(res.totalPages ?? 0);
        this.paginaConsultas.set(pagina);
        this.consultasCargadas = true;
        this.cargandoConsultas.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.cargandoConsultas.set(false);
        this.errorConsultas.set(this.problemDetail.mensaje(err));
      },
    });
  }

  irAPaginaConsultas(nuevaPagina: number): void {
    const m = this.mascota();
    if (!m || nuevaPagina < 0 || nuevaPagina >= this.totalPaginasConsultas()) return;
    this.cargarConsultas(m.id, nuevaPagina);
  }

  // ---------- Especie ----------

  /**
   * Complementaria: un 502 aquí (verificado en vivo contra el backend
   * real con "Perro", "Gato", "dog" y "cat" — los cuatro fallaron de
   * forma idéntica porque este entorno no tiene APP_EXTERNAL_API_KEY
   * configurada) nunca debe tratarse como el error general de la ficha.
   * Se captura aparte y se degrada a una tarjeta "no disponible", no a
   * un texto de error crudo.
   */
  private cargarEspecie(especie: string): void {
    this.cargandoEspecie.set(true);
    this.especieNoDisponible.set(false);
    this.especieApi.buscar(especie).subscribe({
      next: (info) => {
        this.infoEspecie.set(info);
        this.especieCargadaPara = especie;
        this.cargandoEspecie.set(false);
      },
      error: () => {
        this.infoEspecie.set(null);
        this.especieNoDisponible.set(true);
        this.especieCargadaPara = especie;
        this.cargandoEspecie.set(false);
      },
    });
  }
}
