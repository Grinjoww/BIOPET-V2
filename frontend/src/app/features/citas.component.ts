import { Component, OnInit, ViewChild, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { AuthService } from '../core/auth.service';
import { ProblemDetailService } from '../core/problem-detail.service';
import { UsuarioSeleccionableApiService } from '../core/usuario-seleccionable-api.service';
import { MascotaApiService } from './mascota-api.service';
import { Cita, CitaApiService, CitaRequestPayload, EstadoCita, FiltrosCitas } from './cita-api.service';
import { PageHeaderComponent } from '../shared/page-header/page-header.component';
import { IconComponent } from '../shared/icons/icon.component';
import { FocusTrapDirective } from '../shared/focus-trap/focus-trap.directive';
import { EntitySearchSelectComponent, OpcionBusqueda } from '../shared/entity-search-select/entity-search-select.component';
import {
  chipClaseEstadoCita,
  etiquetaEstadoCita,
  fechaHoraLocalAInstant,
  formatearExpediente,
  instantAFechaHoraLocal,
} from '../shared/presentacion';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

const TAMANIO_PAGINA = 10;
const DURACION_MENSAJE_EXITO_MS = 4000;
const FILAS_SKELETON = 4;

/**
 * IMPORTANTE — reglas reales de CitaController/CitaService (auditadas en
 * esta fase, no inventadas):
 *
 *  - GET /api/citas (listar) y GET /api/citas/{id}: los 4 roles pueden
 *    leer. ROLE_DUENO ve solo citas de sus propias mascotas (filtrado en
 *    el servidor, CitaService.listar). ADMIN/VETERINARIO/AUXILIAR ven el
 *    listado completo de la clínica.
 *  - POST /api/citas (crear): solo ADMIN y AUXILIAR. Nunca VETERINARIO,
 *    nunca DUENO. El backend ignora cualquier `estado` recibido y fuerza
 *    siempre PROGRAMADA (CitaRequest, javadoc de CitaService.crear).
 *  - PUT /api/citas/{id} (actualizar): ADMIN, AUXILIAR y VETERINARIO,
 *    pero un VETERINARIO solo puede modificar citas donde él es el
 *    veterinario ya asignado (CitaService.verificarPermisoEscritura).
 *    Es el único endpoint real para cambiar `estado`: no existe un
 *    endpoint dedicado "completar"/"cancelar", así que esas acciones se
 *    implementan aquí como un PUT que reenvía los mismos datos de la
 *    cita cambiando únicamente `estado`.
 *  - DELETE /api/citas/{id}: solo ADMIN. Es una baja lógica real
 *    (`activo=false`, ver CitaService.eliminar), un concepto distinto de
 *    "cancelar" (que es solo poner `estado=CANCELADA` sin tocar
 *    `activo`) — el backend distingue ambos y esta UI también.
 *
 * Este componente no decide qué citas ve cada rol ni si una escritura es
 * válida: solo oculta controles que el backend rechazaría de todas
 * formas (mejora de UX, no control de seguridad — el 403 real sigue
 * viviendo en @PreAuthorize / CitaService).
 */
@Component({
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule, RouterLink, PageHeaderComponent, IconComponent, FocusTrapDirective, EntitySearchSelectComponent],
  template: `
  <app-page-header
    eyebrow="Agenda"
    title="Citas"
    [description]="descripcion"
    [hasActions]="puedeCrear">
    <button type="button" class="btn btn--primary" (click)="abrirCrear()">
      <app-icon name="anadir"></app-icon>
      Nueva cita
    </button>
  </app-page-header>

  <div class="toolbar" role="toolbar" aria-label="Acciones de citas">
    <button type="button" class="btn btn--ghost btn--sm" (click)="cargar()" [disabled]="cargando()">
      Actualizar
    </button>
    <button type="button" class="btn btn--secondary btn--sm" (click)="alternarFiltros()" [attr.aria-expanded]="mostrarFiltros()">
      {{ mostrarFiltros() ? 'Ocultar filtros' : 'Filtros' }}
    </button>
  </div>

  <!-- ===== Filtros (auditoría de usabilidad + corrección de listados, fase "demo local") ===== -->
  <section *ngIf="mostrarFiltros()" class="panel" aria-labelledby="filtros-citas-titulo">
    <h2 id="filtros-citas-titulo" class="panel__title-inline">Filtros</h2>
    <div class="toolbar">
      <app-entity-search-select
        #filtroMascotaSelector
        [controlId]="'f-filtro-mascota'"
        label="Mascota"
        placeholder="Nombre de la mascota…"
        [buscar]="buscarMascotas"
        [seleccionInicial]="mascotaFiltroSeleccionInicial"
        (seleccion)="onMascotaFiltroSeleccionada($event)">
      </app-entity-search-select>
      <app-entity-search-select
        #filtroVeterinarioSelector
        *ngIf="puedeFiltrarPorVeterinario"
        [controlId]="'f-filtro-veterinario'"
        label="Veterinario"
        placeholder="Nombre o email…"
        [buscar]="buscarVeterinarios"
        [seleccionInicial]="veterinarioFiltroSeleccionInicial"
        (seleccion)="onVeterinarioFiltroSeleccionado($event)">
      </app-entity-search-select>
      <div class="field">
        <label for="f-filtro-estado">Estado</label>
        <select id="f-filtro-estado" [(ngModel)]="filtroEstado" [ngModelOptions]="{ standalone: true }">
          <option [ngValue]="null">Todos</option>
          <option value="PROGRAMADA">Programada</option>
          <option value="COMPLETADA">Completada</option>
          <option value="CANCELADA">Cancelada</option>
        </select>
      </div>
      <div class="field">
        <label for="f-filtro-desde">Desde</label>
        <input id="f-filtro-desde" type="date" [(ngModel)]="filtroDesde" [ngModelOptions]="{ standalone: true }" />
      </div>
      <div class="field">
        <label for="f-filtro-hasta">Hasta</label>
        <input id="f-filtro-hasta" type="date" [(ngModel)]="filtroHasta" [ngModelOptions]="{ standalone: true }" />
      </div>
      <div class="field" style="align-self: flex-end;">
        <button type="button" class="btn btn--primary btn--sm" (click)="aplicarFiltros()">Filtrar</button>
        <button type="button" class="btn btn--secondary btn--sm" (click)="limpiarFiltros()">Limpiar</button>
      </div>
    </div>
  </section>

  <p class="alert alert--danger" role="alert" aria-live="assertive" *ngIf="error()">
    <strong>Error:</strong> {{ error() }}
  </p>
  <p class="alert alert--success" role="status" aria-live="polite" *ngIf="mensajeExito()">
    <strong>Listo:</strong> {{ mensajeExito() }}
  </p>

  <!-- ===== Formulario crear/editar ===== -->
  <section *ngIf="mostrarFormulario()" class="panel panel--record" aria-labelledby="form-titulo">
    <div class="panel__title">
      <h2 id="form-titulo">{{ editando() ? 'Editar cita' : 'Nueva cita' }}</h2>
      <p class="field-hint" *ngIf="editando() as c">
        Expediente <span class="data">{{ formatearExpediente(c.mascotaId) }}</span> · {{ c.mascotaNombre }} ·
        Estado actual
        <span class="chip" [ngClass]="chipClaseEstadoCita(c.estado)">{{ etiquetaEstadoCita(c.estado) }}</span>
      </p>
      <p class="field-hint" *ngIf="editando()">
        Este formulario no cambia el estado de la cita. Usa «Completar» o «Cancelar» en el listado para eso.
      </p>
    </div>

    <form [formGroup]="form" (ngSubmit)="guardar()" novalidate>
      <span class="label form-section-label">Agenda</span>
      <app-entity-search-select
        [controlId]="'f-mascotaId'"
        label="Mascota"
        [requerido]="true"
        placeholder="Nombre de la mascota (mín. 2 caracteres)…"
        [buscar]="buscarMascotas"
        [seleccionInicial]="mascotaSeleccionInicial"
        (seleccion)="onMascotaSeleccionada($event)">
      </app-entity-search-select>
      <p class="field-error" id="err-mascotaId" *ngIf="tieneError('mascotaId')">{{ mensajeError('mascotaId') }}</p>

      <app-entity-search-select
        [controlId]="'f-veterinarioId'"
        label="Veterinario"
        [requerido]="true"
        placeholder="Nombre o email del veterinario (mín. 2 caracteres)…"
        [buscar]="buscarVeterinarios"
        [seleccionInicial]="veterinarioSeleccionInicial"
        (seleccion)="onVeterinarioSeleccionado($event)">
      </app-entity-search-select>
      <p class="field-error" id="err-veterinarioId" *ngIf="tieneError('veterinarioId')">{{ mensajeError('veterinarioId') }}</p>

      <div class="field">
        <label for="f-fechaHora">Fecha y hora<span class="required-mark" aria-hidden="true">*</span></label>
        <input
          id="f-fechaHora"
          type="datetime-local"
          formControlName="fechaHora"
          [attr.aria-invalid]="tieneError('fechaHora')"
          [attr.aria-describedby]="tieneError('fechaHora') ? 'err-fechaHora' : null" />
        <p class="field-error" id="err-fechaHora" *ngIf="tieneError('fechaHora')">{{ mensajeError('fechaHora') }}</p>
      </div>

      <span class="label form-section-label">Motivo</span>
      <div class="field">
        <label for="f-motivo">Motivo de la cita</label>
        <textarea id="f-motivo" formControlName="motivo" [attr.aria-invalid]="tieneError('motivo')"></textarea>
        <p class="field-error" id="err-motivo" *ngIf="tieneError('motivo')">{{ mensajeError('motivo') }}</p>
      </div>

      <div class="modal-panel__actions">
        <button type="submit" class="btn btn--primary" [disabled]="guardando()">
          {{ guardando() ? 'Guardando…' : (editando() ? 'Guardar cambios' : 'Agendar cita') }}
        </button>
        <button type="button" class="btn btn--secondary" (click)="cerrarFormulario()" [disabled]="guardando()">
          Cancelar
        </button>
      </div>
    </form>
  </section>

  <!-- ===== Confirmación de acciones (completar / cancelar / baja) ===== -->
  <div class="modal-overlay" *ngIf="accionConfirmar() as accion">
    <div
      class="modal-panel"
      appFocusTrap
      (keydown.escape)="cancelarConfirmacion()"
      role="alertdialog"
      aria-modal="true"
      aria-labelledby="confirm-titulo"
      aria-describedby="confirm-texto">
      <ng-container [ngSwitch]="accion.tipo">
        <h2 id="confirm-titulo" *ngSwitchCase="'completar'">Completar cita</h2>
        <h2 id="confirm-titulo" *ngSwitchCase="'cancelar'">Cancelar cita</h2>
        <h2 id="confirm-titulo" *ngSwitchCase="'baja'">Dar de baja la cita</h2>
      </ng-container>
      <p id="confirm-texto">
        <ng-container [ngSwitch]="accion.tipo">
          <span *ngSwitchCase="'completar'">
            ¿Marcar como completada la cita de <strong>{{ accion.cita.mascotaNombre }}</strong> del
            <span class="data">{{ accion.cita.fechaHora | date: "d 'de' MMMM y, HH:mm" }}</span>?
          </span>
          <span *ngSwitchCase="'cancelar'">
            ¿Cancelar la cita de <strong>{{ accion.cita.mascotaNombre }}</strong> del
            <span class="data">{{ accion.cita.fechaHora | date: "d 'de' MMMM y, HH:mm" }}</span>? Quedará marcada como
            cancelada, sin eliminarse del historial.
          </span>
          <span *ngSwitchCase="'baja'">
            ¿Dar de baja la cita de <strong>{{ accion.cita.mascotaNombre }}</strong> del
            <span class="data">{{ accion.cita.fechaHora | date: "d 'de' MMMM y, HH:mm" }}</span>? Esta acción no se
            puede deshacer desde la interfaz.
          </span>
        </ng-container>
      </p>
      <div class="modal-panel__actions">
        <button
          type="button"
          [ngClass]="accion.tipo === 'baja' ? 'btn btn--danger-solid' : (accion.tipo === 'cancelar' ? 'btn btn--danger' : 'btn btn--primary')"
          (click)="confirmarAccion()"
          [disabled]="procesandoAccion()">
          {{ procesandoAccion() ? 'Procesando…' : etiquetaConfirmar(accion.tipo) }}
        </button>
        <button type="button" class="btn btn--secondary" (click)="cancelarConfirmacion()" [disabled]="procesandoAccion()">
          Volver
        </button>
      </div>
    </div>
  </div>

  <!-- ===== Listado ===== -->
  <span class="sr-only" role="status" aria-live="polite" *ngIf="cargando()">Cargando citas…</span>

  <div class="table-wrap">
    <table class="table-clinico">
      <caption class="sr-only">Listado de citas</caption>
      <thead>
        <tr>
          <th scope="col">Fecha y hora</th>
          <th scope="col">Mascota</th>
          <th scope="col">Veterinario</th>
          <th scope="col">Motivo</th>
          <th scope="col">Estado</th>
          <th scope="col"><span class="sr-only">Acciones</span></th>
        </tr>
      </thead>
      <tbody>
        <ng-container *ngIf="cargando()">
          <tr class="skeleton-row" *ngFor="let fila of filasSkeleton" aria-hidden="true">
            <td><span class="skeleton-block"></span></td>
            <td><span class="skeleton-block"></span></td>
            <td><span class="skeleton-block"></span></td>
            <td><span class="skeleton-block"></span></td>
            <td><span class="skeleton-block"></span></td>
            <td></td>
          </tr>
        </ng-container>

        <tr class="table-clinico__empty-row" *ngIf="!cargando() && !error() && citas().length === 0">
          <td class="table-clinico__empty-cell" colspan="6">
            <p class="table-clinico__empty-title">No hay citas registradas</p>
            <p class="table-clinico__empty-text" *ngIf="puedeCrear">
              Agenda la primera cita para empezar a organizar la atención veterinaria.
            </p>
            <p class="table-clinico__empty-text" *ngIf="esDueno">
              Cuando la clínica agende una cita para tu mascota, aparecerá aquí.
            </p>
            <p class="table-clinico__empty-text" *ngIf="!puedeCrear && !esDueno">
              Cuando se agende una cita en la clínica, aparecerá aquí.
            </p>
            <button type="button" class="btn btn--primary" *ngIf="puedeCrear" (click)="abrirCrear()">
              <app-icon name="anadir"></app-icon>
              Nueva cita
            </button>
          </td>
        </tr>

        <ng-container *ngIf="!cargando()">
          <tr *ngFor="let c of citas()">
            <td data-label="Fecha y hora">
              <time class="data cita-fecha" [attr.datetime]="c.fechaHora">{{ c.fechaHora | date: "d MMM y" }}</time>
              <span class="data cita-hora">{{ c.fechaHora | date: 'HH:mm' }}</span>
            </td>
            <td data-label="Mascota">
              <a class="record-link" [routerLink]="['/mascotas', c.mascotaId]" [attr.aria-label]="'Ver expediente de ' + c.mascotaNombre">
                {{ c.mascotaNombre }}
              </a>
            </td>
            <td data-label="Veterinario">{{ c.veterinarioNombre }}</td>
            <td data-label="Motivo">{{ c.motivo || 'Sin motivo registrado' }}</td>
            <td data-label="Estado">
              <span class="chip" [ngClass]="chipClaseEstadoCita(c.estado)">{{ etiquetaEstadoCita(c.estado) }}</span>
            </td>
            <td data-label="" class="actions">
              <button
                type="button"
                class="btn-icon"
                *ngIf="puedeEditar(c)"
                (click)="abrirEditar(c)"
                [attr.aria-label]="'Editar cita de ' + c.mascotaNombre">
                <app-icon name="editar"></app-icon>
              </button>
              <button
                type="button"
                class="btn-icon btn-icon--success"
                *ngIf="puedeCompletar(c)"
                (click)="pedirConfirmacion(c, 'completar')"
                [attr.aria-label]="'Completar cita de ' + c.mascotaNombre">
                <app-icon name="check"></app-icon>
              </button>
              <button
                type="button"
                class="btn-icon btn-icon--danger"
                *ngIf="puedeCancelar(c)"
                (click)="pedirConfirmacion(c, 'cancelar')"
                [attr.aria-label]="'Cancelar cita de ' + c.mascotaNombre">
                <app-icon name="close"></app-icon>
              </button>
              <button
                type="button"
                class="btn-icon btn-icon--danger"
                *ngIf="puedeDarDeBaja"
                (click)="pedirConfirmacion(c, 'baja')"
                [attr.aria-label]="'Dar de baja la cita de ' + c.mascotaNombre">
                <app-icon name="eliminar"></app-icon>
              </button>
            </td>
          </tr>
        </ng-container>
      </tbody>
    </table>
  </div>

  <!-- ===== Paginación ===== -->
  <nav class="pagination" aria-label="Paginación de citas" *ngIf="!cargando() && totalPaginas() > 0">
    <button type="button" class="btn btn--secondary btn--sm" (click)="irAPagina(pagina() - 1)" [disabled]="pagina() === 0">
      Anterior
    </button>
    <span class="pagination__status" aria-live="polite">
      Página {{ pagina() + 1 }} de {{ totalPaginas() }} ({{ totalElementos() }} citas)
    </span>
    <button type="button" class="btn btn--secondary btn--sm" (click)="irAPagina(pagina() + 1)" [disabled]="pagina() + 1 >= totalPaginas()">
      Siguiente
    </button>
  </nav>
  `,
})
export class CitasComponent implements OnInit {
  readonly formatearExpediente = formatearExpediente;
  readonly chipClaseEstadoCita = chipClaseEstadoCita;
  readonly etiquetaEstadoCita = etiquetaEstadoCita;
  readonly filasSkeleton = Array.from({ length: FILAS_SKELETON });

  citas = signal<Cita[]>([]);
  pagina = signal(0);
  totalPaginas = signal(0);
  totalElementos = signal(0);

  cargando = signal(false);
  error = signal('');
  mensajeExito = signal('');

  // ---------- Filtros (corrección de listados, fase "demo local") ----------
  mostrarFiltros = signal(false);
  filtroEstado: EstadoCita | null = null;
  filtroDesde = ''; // yyyy-MM-dd
  filtroHasta = ''; // yyyy-MM-dd
  private filtroMascotaId: number | null = null;
  private filtroVeterinarioId: number | null = null;
  mascotaFiltroSeleccionInicial: OpcionBusqueda | null = null;
  veterinarioFiltroSeleccionInicial: OpcionBusqueda | null = null;
  private filtrosAplicados: FiltrosCitas = {};
  @ViewChild('filtroMascotaSelector') private filtroMascotaSelector?: EntitySearchSelectComponent;
  @ViewChild('filtroVeterinarioSelector') private filtroVeterinarioSelector?: EntitySearchSelectComponent;

  mostrarFormulario = signal(false);
  editando = signal<Cita | null>(null);
  guardando = signal(false);
  private erroresServidor: Record<string, string[]> | null = null;

  accionConfirmar = signal<{ cita: Cita; tipo: 'completar' | 'cancelar' | 'baja' } | null>(null);
  procesandoAccion = signal(false);

  // ---------- Selectores buscables del formulario (auditoría de usabilidad con datos masivos) ----------
  mascotaSeleccionInicial: OpcionBusqueda | null = null;
  veterinarioSeleccionInicial: OpcionBusqueda | null = null;

  form = this.fb.group({
    mascotaId: [null as number | null, [Validators.required, Validators.min(1)]],
    veterinarioId: [null as number | null, [Validators.required, Validators.min(1)]],
    fechaHora: ['', [Validators.required]],
    motivo: ['', [Validators.maxLength(255)]],
  });

  constructor(
    private api: CitaApiService,
    private mascotaApi: MascotaApiService,
    private usuarioSeleccionableApi: UsuarioSeleccionableApiService,
    private auth: AuthService,
    private problemDetail: ProblemDetailService,
    private fb: FormBuilder
  ) {}

  ngOnInit(): void {
    this.cargar();
  }

  // ---------- Permisos (solo UX — la regla real vive en el backend) ----------

  /** POST /api/citas: @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR')"). */
  get puedeCrear(): boolean {
    const rol = this.auth.usuarioActual()?.rol;
    return rol === 'ROLE_ADMIN' || rol === 'ROLE_AUXILIAR';
  }

  get esDueno(): boolean {
    return this.auth.usuarioActual()?.rol === 'ROLE_DUENO';
  }

  get descripcion(): string {
    if (this.esDueno) return 'Consulta las citas programadas para tus mascotas.';
    if (this.puedeCrear) return 'Agenda y seguimiento de las citas de la clínica.';
    return 'Agenda de la clínica y gestión de las citas asignadas a ti.';
  }

  /**
   * PUT /api/citas/{id}: ADMIN y AUXILIAR pueden editar cualquier cita;
   * VETERINARIO solo la suya (CitaService.verificarPermisoEscritura).
   * DUENO nunca puede escribir citas.
   */
  puedeEditar(c: Cita): boolean {
    const usuario = this.auth.usuarioActual();
    if (!usuario) return false;
    if (usuario.rol === 'ROLE_ADMIN' || usuario.rol === 'ROLE_AUXILIAR') return true;
    if (usuario.rol === 'ROLE_VETERINARIO') return c.veterinarioId === usuario.id;
    return false;
  }

  /**
   * No existe endpoint dedicado: completar/cancelar son un PUT normal
   * con `estado` distinto, así que la misma regla de escritura de
   * puedeEditar aplica. Solo tiene sentido ofrecerlas sobre una cita
   * todavía PROGRAMADA (una COMPLETADA/CANCELADA no debe volver a
   * mostrar una acción que ya no representa una transición real).
   */
  puedeCompletar(c: Cita): boolean {
    return c.estado === 'PROGRAMADA' && this.puedeEditar(c);
  }

  puedeCancelar(c: Cita): boolean {
    return c.estado === 'PROGRAMADA' && this.puedeEditar(c);
  }

  /** DELETE /api/citas/{id}: @PreAuthorize("hasRole('ADMIN')"), sin restricción de estado. */
  get puedeDarDeBaja(): boolean {
    return this.auth.usuarioActual()?.rol === 'ROLE_ADMIN';
  }

  etiquetaConfirmar(tipo: 'completar' | 'cancelar' | 'baja'): string {
    switch (tipo) {
      case 'completar':
        return 'Sí, completar';
      case 'cancelar':
        return 'Sí, cancelar';
      case 'baja':
        return 'Sí, dar de baja';
    }
  }

  private mostrarExito(mensaje: string): void {
    this.mensajeExito.set(mensaje);
    setTimeout(() => {
      if (this.mensajeExito() === mensaje) this.mensajeExito.set('');
    }, DURACION_MENSAJE_EXITO_MS);
  }

  // ---------- Listado / paginación ----------

  /**
   * Orden cronológico ascendente por defecto: en una agenda clínica lo
   * más útil es ver primero lo que sigue, no lo más reciente creado.
   */
  cargar(): void {
    this.error.set('');
    this.cargando.set(true);
    // Orden por defecto: más reciente/próxima primero (antes 'asc' dejaba
    // una cita recién creada al final del listado -corrección "demo local").
    this.api.listar(this.pagina(), TAMANIO_PAGINA, this.filtrosAplicados, 'fechaHora,desc').subscribe({
      next: (res) => {
        this.citas.set(res.content ?? []);
        this.totalPaginas.set(res.totalPages ?? 0);
        this.totalElementos.set(res.totalElements ?? 0);
        this.cargando.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.citas.set([]);
        this.cargando.set(false);
        this.error.set(this.problemDetail.mensaje(err));
      },
    });
  }

  /** ADMIN/AUXILIAR ven la agenda completa; un VETERINARIO solo ve la suya (el filtro sería inerte). */
  get puedeFiltrarPorVeterinario(): boolean {
    return this.puedeCrear;
  }

  alternarFiltros(): void {
    this.mostrarFiltros.set(!this.mostrarFiltros());
  }

  onMascotaFiltroSeleccionada(opcion: OpcionBusqueda | null): void {
    this.filtroMascotaId = opcion?.id ?? null;
  }

  onVeterinarioFiltroSeleccionado(opcion: OpcionBusqueda | null): void {
    this.filtroVeterinarioId = opcion?.id ?? null;
  }

  aplicarFiltros(): void {
    this.filtrosAplicados = {
      mascotaId: this.filtroMascotaId ?? undefined,
      veterinarioId: this.filtroVeterinarioId ?? undefined,
      estado: this.filtroEstado ?? undefined,
      desde: this.filtroDesde ? `${this.filtroDesde}T00:00:00Z` : undefined,
      hasta: this.filtroHasta ? `${this.filtroHasta}T23:59:59Z` : undefined,
    };
    this.pagina.set(0);
    this.cargar();
  }

  limpiarFiltros(): void {
    this.filtroMascotaId = null;
    this.filtroVeterinarioId = null;
    this.filtroEstado = null;
    this.filtroDesde = '';
    this.filtroHasta = '';
    this.mascotaFiltroSeleccionInicial = null;
    this.veterinarioFiltroSeleccionInicial = null;
    this.filtroMascotaSelector?.limpiar();
    this.filtroVeterinarioSelector?.limpiar();
    this.filtrosAplicados = {};
    this.pagina.set(0);
    this.cargar();
  }

  irAPagina(nuevaPagina: number): void {
    if (nuevaPagina < 0 || nuevaPagina >= this.totalPaginas()) return;
    this.pagina.set(nuevaPagina);
    this.cargar();
  }

  // ---------- Selectores buscables ----------

  /**
   * Búsqueda real contra GET /api/mascotas?q=...&size=... (paginada en el
   * backend). Antes se precargaban 200 de las 10.000 mascotas reales de
   * biopet_db_1m_v2 -alfabéticas- y el resto quedaba, en la práctica,
   * imposible de elegir; ahora cada tecleo busca de verdad, sin traer todo.
   */
  buscarMascotas = (q: string): Observable<OpcionBusqueda[]> =>
    this.mascotaApi
      .buscarSeleccionables(q, 0, 15)
      .pipe(map((res) => res.content.map((m) => ({ id: m.id, principal: m.nombre, secundario: `${formatearExpediente(m.id)} · ${m.duenioNombre}` }))));

  buscarVeterinarios = (q: string): Observable<OpcionBusqueda[]> =>
    this.usuarioSeleccionableApi
      .buscarVeterinarios(q, 0, 15)
      .pipe(map((res) => res.content.map((v) => ({ id: v.id, principal: v.nombre, secundario: v.email }))));

  onMascotaSeleccionada(opcion: OpcionBusqueda | null): void {
    this.form.patchValue({ mascotaId: opcion?.id ?? null });
    this.form.get('mascotaId')?.markAsTouched();
  }

  onVeterinarioSeleccionado(opcion: OpcionBusqueda | null): void {
    this.form.patchValue({ veterinarioId: opcion?.id ?? null });
    this.form.get('veterinarioId')?.markAsTouched();
  }

  // ---------- Formulario crear/editar ----------

  abrirCrear(): void {
    this.editando.set(null);
    this.erroresServidor = null;
    this.form.reset({ mascotaId: null, veterinarioId: null, fechaHora: '', motivo: '' });
    this.mascotaSeleccionInicial = null;
    this.veterinarioSeleccionInicial = null;
    this.mostrarFormulario.set(true);
    this.enfocarPrimerCampo();
  }

  abrirEditar(c: Cita): void {
    this.editando.set(c);
    this.erroresServidor = null;
    this.form.reset({
      mascotaId: c.mascotaId,
      veterinarioId: c.veterinarioId,
      fechaHora: instantAFechaHoraLocal(c.fechaHora),
      motivo: c.motivo ?? '',
    });
    // Mascota y veterinario ya conocidos (edición): se muestran sin volver
    // a buscarlos.
    this.mascotaSeleccionInicial = { id: c.mascotaId, principal: c.mascotaNombre };
    this.veterinarioSeleccionInicial = { id: c.veterinarioId, principal: c.veterinarioNombre };
    this.mostrarFormulario.set(true);
    this.enfocarPrimerCampo();
  }

  cerrarFormulario(): void {
    this.mostrarFormulario.set(false);
    this.editando.set(null);
    this.erroresServidor = null;
  }

  private enfocarPrimerCampo(): void {
    queueMicrotask(() => document.getElementById('f-mascotaId')?.focus());
  }

  private enfocarPrimerCampoInvalido(): void {
    const orden = ['mascotaId', 'veterinarioId', 'fechaHora'];
    const primerInvalido = orden.find((c) => this.tieneError(c));
    if (primerInvalido) {
      queueMicrotask(() => document.getElementById(`f-${primerInvalido}`)?.focus());
    }
  }

  guardar(): void {
    this.erroresServidor = null;
    this.error.set('');

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.enfocarPrimerCampoInvalido();
      return;
    }

    const v = this.form.getRawValue();
    const actual = this.editando();
    const payload: CitaRequestPayload = {
      mascotaId: v.mascotaId as number,
      veterinarioId: v.veterinarioId as number,
      fechaHora: fechaHoraLocalAInstant(v.fechaHora as string),
      motivo: v.motivo || null,
      // Este formulario nunca cambia el estado (ver aviso en el propio
      // panel): al editar se reenvía el estado actual sin tocarlo; al
      // crear, PROGRAMADA, que es lo único que el backend aceptaría de
      // todas formas (ignora `estado` en POST).
      estado: (actual?.estado ?? 'PROGRAMADA') as EstadoCita,
    };

    this.guardando.set(true);
    const peticion = actual ? this.api.actualizar(actual.id, payload) : this.api.crear(payload);

    peticion.subscribe({
      next: () => {
        this.guardando.set(false);
        this.mostrarExito(actual ? 'Cita actualizada correctamente.' : 'Cita agendada correctamente.');
        this.cerrarFormulario();
        this.cargar();
      },
      error: (err: HttpErrorResponse) => {
        this.guardando.set(false);
        this.erroresServidor = this.problemDetail.erroresPorCampo(err);
        if (this.erroresServidor) {
          this.enfocarPrimerCampoInvalido();
        } else {
          this.error.set(this.problemDetail.mensaje(err));
        }
      },
    });
  }

  tieneError(campo: string): boolean {
    const control = this.form.get(campo);
    const clienteInvalido = !!control && control.invalid && (control.touched || control.dirty);
    const servidorInvalido = !!this.erroresServidor?.[campo]?.length;
    return clienteInvalido || servidorInvalido;
  }

  mensajeError(campo: string): string {
    const delServidor = this.erroresServidor?.[campo]?.[0];
    if (delServidor) return delServidor;

    const control = this.form.get(campo);
    if (control?.hasError('required')) return 'Este campo es obligatorio.';
    if (control?.hasError('min')) return 'Selecciona un valor válido.';
    if (control?.hasError('maxlength')) {
      const max = control.getError('maxlength')?.requiredLength;
      return `Máximo ${max} caracteres.`;
    }
    return 'Valor inválido.';
  }

  // ---------- Completar / cancelar / dar de baja ----------

  pedirConfirmacion(cita: Cita, tipo: 'completar' | 'cancelar' | 'baja'): void {
    this.error.set('');
    this.accionConfirmar.set({ cita, tipo });
  }

  cancelarConfirmacion(): void {
    this.accionConfirmar.set(null);
  }

  confirmarAccion(): void {
    const actual = this.accionConfirmar();
    if (!actual) return;
    const { cita, tipo } = actual;

    if (tipo === 'baja') {
      this.procesandoAccion.set(true);
      this.api.eliminar(cita.id).subscribe({
        next: () => {
          this.procesandoAccion.set(false);
          this.accionConfirmar.set(null);
          this.mostrarExito(`La cita de ${cita.mascotaNombre} fue dada de baja.`);
          if (this.citas().length === 1 && this.pagina() > 0) {
            this.pagina.set(this.pagina() - 1);
          }
          this.cargar();
        },
        error: (err: HttpErrorResponse) => {
          this.procesandoAccion.set(false);
          this.accionConfirmar.set(null);
          this.error.set(this.problemDetail.mensaje(err));
        },
      });
      return;
    }

    const nuevoEstado: EstadoCita = tipo === 'completar' ? 'COMPLETADA' : 'CANCELADA';
    const payload: CitaRequestPayload = {
      mascotaId: cita.mascotaId,
      veterinarioId: cita.veterinarioId,
      fechaHora: cita.fechaHora,
      motivo: cita.motivo,
      estado: nuevoEstado,
    };

    this.procesandoAccion.set(true);
    this.api.actualizar(cita.id, payload).subscribe({
      next: () => {
        this.procesandoAccion.set(false);
        this.accionConfirmar.set(null);
        this.mostrarExito(tipo === 'completar' ? 'Cita marcada como completada.' : 'Cita cancelada.');
        this.cargar();
      },
      error: (err: HttpErrorResponse) => {
        this.procesandoAccion.set(false);
        this.accionConfirmar.set(null);
        this.error.set(this.problemDetail.mensaje(err));
      },
    });
  }
}
