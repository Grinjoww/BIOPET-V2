import { Component, OnInit, ViewChild, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { AuthService } from '../core/auth.service';
import { ProblemDetailService } from '../core/problem-detail.service';
import { UsuarioSeleccionableApiService } from '../core/usuario-seleccionable-api.service';
import { MascotaApiService } from './mascota-api.service';
import { FiltrosVacunas, Vacuna, VacunaApiService, VacunaRequestPayload } from './vacuna-api.service';
import { PageHeaderComponent } from '../shared/page-header/page-header.component';
import { IconComponent } from '../shared/icons/icon.component';
import { FocusTrapDirective } from '../shared/focus-trap/focus-trap.directive';
import { EntitySearchSelectComponent, OpcionBusqueda } from '../shared/entity-search-select/entity-search-select.component';
import {
  chipClaseEstadoVacuna,
  estadoVacuna,
  etiquetaEstadoVacuna,
  formatearExpediente,
} from '../shared/presentacion';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

const TAMANIO_PAGINA = 10;
const DURACION_MENSAJE_EXITO_MS = 4000;
const FILAS_SKELETON = 4;

/**
 * IMPORTANTE: este componente NO decide qué vacunas puede ver cada rol.
 * El backend (VacunaService.listar) ya filtra por dueño cuando el rol es
 * ROLE_DUENO (solo vacunas de sus propias mascotas) y devuelve el listado
 * completo para ADMIN/VETERINARIO/AUXILIAR. Aquí solo consumimos tal cual
 * lo que llega en `content`.
 *
 * Lo único que decide este componente es la VISIBILIDAD de los controles
 * de acción (crear/editar/eliminar): mejora de UX, no control de
 * seguridad. El backend sigue rechazando con 403 vía @PreAuthorize aunque
 * el DOM sea manipulado.
 */
@Component({
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule, RouterLink, PageHeaderComponent, IconComponent, FocusTrapDirective, EntitySearchSelectComponent],
  template: `
  <app-page-header
    eyebrow="Inmunización"
    title="Vacunas"
    [description]="esDueno ? 'Registro de vacunación de tus mascotas.' : 'Registro de vacunación de las mascotas del sistema.'"
    [hasActions]="puedeGestionar">
    <button type="button" class="btn btn--primary" (click)="abrirCrear()">
      <app-icon name="anadir"></app-icon>
      Registrar vacuna
    </button>
  </app-page-header>

  <div class="toolbar" role="toolbar" aria-label="Acciones de vacunas">
    <button type="button" class="btn btn--ghost btn--sm" (click)="cargar()" [disabled]="cargando()">
      Actualizar
    </button>
    <button type="button" class="btn btn--secondary btn--sm" (click)="alternarFiltros()" [attr.aria-expanded]="mostrarFiltros()">
      {{ mostrarFiltros() ? 'Ocultar filtros' : 'Filtros' }}
    </button>
  </div>

  <!-- ===== Filtros (auditoría de usabilidad + corrección de listados, fase "demo local") ===== -->
  <section *ngIf="mostrarFiltros()" class="panel" aria-labelledby="filtros-vacunas-titulo">
    <h2 id="filtros-vacunas-titulo" class="panel__title-inline">Filtros</h2>
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
        <label for="f-filtro-tipo">Tipo</label>
        <input id="f-filtro-tipo" type="text" [(ngModel)]="filtroTipo" [ngModelOptions]="{ standalone: true }" placeholder="p. ej. «antirrábica»" />
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
      <h2 id="form-titulo">{{ editando() ? 'Editar vacuna' : 'Registrar vacuna' }}</h2>
      <p class="field-hint" *ngIf="editando() as v">
        Expediente <span class="data">{{ formatearExpediente(v.mascotaId) }}</span> · {{ v.mascotaNombre }}
      </p>
    </div>

    <form [formGroup]="form" (ngSubmit)="guardar()" novalidate>
      <span class="label form-section-label">Vacuna</span>
      <div class="field">
        <label for="f-tipo">Tipo<span class="required-mark" aria-hidden="true">*</span></label>
        <input
          id="f-tipo"
          type="text"
          formControlName="tipo"
          [attr.aria-invalid]="tieneError('tipo')"
          [attr.aria-describedby]="tieneError('tipo') ? 'err-tipo' : null" />
        <p class="field-error" id="err-tipo" *ngIf="tieneError('tipo')">{{ mensajeError('tipo') }}</p>
      </div>

      <span class="label form-section-label">Aplicación</span>
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
        label="Veterinario (opcional)"
        placeholder="Nombre o email del veterinario (mín. 2 caracteres)…"
        [buscar]="buscarVeterinarios"
        [seleccionInicial]="veterinarioSeleccionInicial"
        (seleccion)="onVeterinarioSeleccionado($event)">
      </app-entity-search-select>

      <div class="field">
        <label for="f-fechaAplicacion">Fecha de aplicación<span class="required-mark" aria-hidden="true">*</span></label>
        <input
          id="f-fechaAplicacion"
          type="date"
          formControlName="fechaAplicacion"
          [attr.aria-invalid]="tieneError('fechaAplicacion')"
          [attr.aria-describedby]="tieneError('fechaAplicacion') ? 'err-fechaAplicacion' : null" />
        <p class="field-error" id="err-fechaAplicacion" *ngIf="tieneError('fechaAplicacion')">
          {{ mensajeError('fechaAplicacion') }}
        </p>
      </div>

      <div class="field">
        <label for="f-proximaFecha">Próxima dosis</label>
        <input id="f-proximaFecha" type="date" formControlName="proximaFecha" />
      </div>

      <span class="label form-section-label">Notas</span>
      <div class="field">
        <label for="f-observaciones">Observaciones</label>
        <textarea id="f-observaciones" formControlName="observaciones"></textarea>
      </div>

      <div class="modal-panel__actions">
        <button type="submit" class="btn btn--primary" [disabled]="guardando()">
          {{ guardando() ? 'Guardando…' : (editando() ? 'Guardar cambios' : 'Registrar vacuna') }}
        </button>
        <button type="button" class="btn btn--secondary" (click)="cerrarFormulario()" [disabled]="guardando()">
          Cancelar
        </button>
      </div>
    </form>
  </section>

  <!-- ===== Confirmación de borrado ===== -->
  <div class="modal-overlay" *ngIf="vacunaAEliminar() as v">
    <div
      class="modal-panel"
      appFocusTrap
      (keydown.escape)="cancelarEliminar()"
      role="alertdialog"
      aria-modal="true"
      aria-labelledby="confirm-titulo"
      aria-describedby="confirm-texto">
      <h2 id="confirm-titulo">Confirmar eliminación</h2>
      <p id="confirm-texto">
        ¿Eliminar la vacuna <strong>{{ v.tipo }}</strong> de {{ v.mascotaNombre }}? Esta acción no se puede deshacer.
      </p>
      <div class="modal-panel__actions">
        <button type="button" class="btn btn--danger-solid" (click)="confirmarEliminar()" [disabled]="eliminando()">
          {{ eliminando() ? 'Eliminando…' : 'Sí, eliminar' }}
        </button>
        <button type="button" class="btn btn--secondary" (click)="cancelarEliminar()" [disabled]="eliminando()">
          Cancelar
        </button>
      </div>
    </div>
  </div>

  <!-- ===== Listado ===== -->
  <span class="sr-only" role="status" aria-live="polite" *ngIf="cargando()">Cargando vacunas…</span>

  <div class="table-wrap">
    <table class="table-clinico">
      <caption class="sr-only">Listado de vacunas</caption>
      <thead>
        <tr>
          <th scope="col">Mascota</th>
          <th scope="col">Tipo</th>
          <th scope="col">Aplicada</th>
          <th scope="col">Próxima dosis</th>
          <th scope="col">Veterinario</th>
          <th scope="col" *ngIf="puedeGestionar"><span class="sr-only">Acciones</span></th>
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
            <td *ngIf="puedeGestionar"></td>
          </tr>
        </ng-container>

        <tr class="table-clinico__empty-row" *ngIf="!cargando() && !error() && vacunas().length === 0">
          <td class="table-clinico__empty-cell" [attr.colspan]="puedeGestionar ? 6 : 5">
            <p class="table-clinico__empty-title">Sin vacunas registradas</p>
            <p class="table-clinico__empty-text" *ngIf="puedeGestionar">
              Registra la primera vacuna para empezar el control de inmunización.
            </p>
            <p class="table-clinico__empty-text" *ngIf="!puedeGestionar">
              Cuando la clínica registre una vacuna para tu mascota, aparecerá aquí.
            </p>
            <button type="button" class="btn btn--primary" *ngIf="puedeGestionar" (click)="abrirCrear()">
              <app-icon name="anadir"></app-icon>
              Registrar vacuna
            </button>
          </td>
        </tr>

        <ng-container *ngIf="!cargando()">
          <tr *ngFor="let v of vacunas()">
            <td data-label="Mascota">
              <a class="record-link" [routerLink]="['/mascotas', v.mascotaId]" [attr.aria-label]="'Ver expediente de ' + v.mascotaNombre">
                {{ v.mascotaNombre }}
              </a>
            </td>
            <td data-label="Tipo">{{ v.tipo }}</td>
            <td data-label="Aplicada" class="data">{{ v.fechaAplicacion }}</td>
            <td data-label="Próxima dosis">
              <span class="data" *ngIf="v.proximaFecha">{{ v.proximaFecha }}</span>
              <span class="chip" [ngClass]="chipClaseEstadoVacuna(estadoVacuna(v.proximaFecha))">
                {{ etiquetaEstadoVacuna(estadoVacuna(v.proximaFecha)) }}
              </span>
            </td>
            <td data-label="Veterinario">{{ v.veterinarioNombre ?? 'Sin veterinario asignado' }}</td>
            <td data-label="" class="actions" *ngIf="puedeGestionar">
              <button type="button" class="btn-icon" (click)="abrirEditar(v)" [attr.aria-label]="'Editar vacuna ' + v.tipo + ' de ' + v.mascotaNombre">
                <app-icon name="editar"></app-icon>
              </button>
              <button type="button" class="btn-icon btn-icon--danger" (click)="pedirConfirmacionEliminar(v)" [attr.aria-label]="'Eliminar vacuna ' + v.tipo + ' de ' + v.mascotaNombre">
                <app-icon name="eliminar"></app-icon>
              </button>
            </td>
          </tr>
        </ng-container>
      </tbody>
    </table>
  </div>

  <!-- ===== Paginación ===== -->
  <nav class="pagination" aria-label="Paginación de vacunas" *ngIf="!cargando() && totalPaginas() > 0">
    <button type="button" class="btn btn--secondary btn--sm" (click)="irAPagina(pagina() - 1)" [disabled]="pagina() === 0">
      Anterior
    </button>
    <span class="pagination__status" aria-live="polite">
      Página {{ pagina() + 1 }} de {{ totalPaginas() }} ({{ totalElementos() }} vacunas)
    </span>
    <button type="button" class="btn btn--secondary btn--sm" (click)="irAPagina(pagina() + 1)" [disabled]="pagina() + 1 >= totalPaginas()">
      Siguiente
    </button>
  </nav>
  `,
})
export class VacunasComponent implements OnInit {
  readonly formatearExpediente = formatearExpediente;
  readonly estadoVacuna = estadoVacuna;
  readonly etiquetaEstadoVacuna = etiquetaEstadoVacuna;
  readonly chipClaseEstadoVacuna = chipClaseEstadoVacuna;
  readonly filasSkeleton = Array.from({ length: FILAS_SKELETON });

  vacunas = signal<Vacuna[]>([]);
  pagina = signal(0);
  totalPaginas = signal(0);
  totalElementos = signal(0);

  cargando = signal(false);
  error = signal('');
  mensajeExito = signal('');

  // ---------- Filtros (corrección de listados, fase "demo local") ----------
  mostrarFiltros = signal(false);
  filtroTipo = '';
  filtroDesde = ''; // yyyy-MM-dd
  filtroHasta = ''; // yyyy-MM-dd
  private filtroMascotaId: number | null = null;
  private filtroVeterinarioId: number | null = null;
  mascotaFiltroSeleccionInicial: OpcionBusqueda | null = null;
  veterinarioFiltroSeleccionInicial: OpcionBusqueda | null = null;
  private filtrosAplicados: FiltrosVacunas = {};
  @ViewChild('filtroMascotaSelector') private filtroMascotaSelector?: EntitySearchSelectComponent;
  @ViewChild('filtroVeterinarioSelector') private filtroVeterinarioSelector?: EntitySearchSelectComponent;

  mostrarFormulario = signal(false);
  editando = signal<Vacuna | null>(null);
  guardando = signal(false);
  private erroresServidor: Record<string, string[]> | null = null;

  vacunaAEliminar = signal<Vacuna | null>(null);
  eliminando = signal(false);

  // ---------- Selectores buscables del formulario (auditoría de usabilidad con datos masivos) ----------
  mascotaSeleccionInicial: OpcionBusqueda | null = null;
  veterinarioSeleccionInicial: OpcionBusqueda | null = null;

  form = this.fb.group({
    mascotaId: [null as number | null, [Validators.required, Validators.min(1)]],
    veterinarioId: [null as number | null],
    tipo: ['', [Validators.required, Validators.maxLength(60)]],
    fechaAplicacion: ['', [Validators.required]],
    proximaFecha: [''],
    observaciones: ['', [Validators.maxLength(255)]],
  });

  constructor(
    private api: VacunaApiService,
    private mascotaApi: MascotaApiService,
    private usuarioSeleccionableApi: UsuarioSeleccionableApiService,
    private auth: AuthService,
    private problemDetail: ProblemDetailService,
    private fb: FormBuilder
  ) {}

  ngOnInit(): void {
    this.cargar();
  }

  /**
   * ROLE_DUENO no puede crear, editar ni eliminar (regla del backend en
   * VacunaController: @PreAuthorize("hasAnyRole('ADMIN','VETERINARIO',
   * 'AUXILIAR')") en POST/PUT/DELETE). Esto solo oculta los controles;
   * la regla real vive en el backend.
   */
  get puedeGestionar(): boolean {
    const rol = this.auth.usuarioActual()?.rol;
    return rol === 'ROLE_ADMIN' || rol === 'ROLE_VETERINARIO' || rol === 'ROLE_AUXILIAR';
  }

  get esDueno(): boolean {
    return this.auth.usuarioActual()?.rol === 'ROLE_DUENO';
  }

  private mostrarExito(mensaje: string): void {
    this.mensajeExito.set(mensaje);
    setTimeout(() => {
      if (this.mensajeExito() === mensaje) this.mensajeExito.set('');
    }, DURACION_MENSAJE_EXITO_MS);
  }

  // ---------- Listado / paginación ----------

  cargar(): void {
    this.error.set('');
    this.cargando.set(true);
    this.api.listar(this.pagina(), TAMANIO_PAGINA, this.filtrosAplicados).subscribe({
      next: (res) => {
        this.vacunas.set(res.content ?? []);
        this.totalPaginas.set(res.totalPages ?? 0);
        this.totalElementos.set(res.totalElements ?? 0);
        this.cargando.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.vacunas.set([]);
        this.cargando.set(false);
        this.error.set(this.problemDetail.mensaje(err));
      },
    });
  }

  /** ADMIN/AUXILIAR ven todas las vacunas; solo ellos filtran por un veterinario ajeno (un VETERINARIO ya ve únicamente las suyas). */
  get puedeFiltrarPorVeterinario(): boolean {
    const rol = this.auth.usuarioActual()?.rol;
    return rol === 'ROLE_ADMIN' || rol === 'ROLE_AUXILIAR';
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
      tipo: this.filtroTipo.trim() || undefined,
      desde: this.filtroDesde || undefined,
      hasta: this.filtroHasta || undefined,
    };
    this.pagina.set(0);
    this.cargar();
  }

  limpiarFiltros(): void {
    this.filtroMascotaId = null;
    this.filtroVeterinarioId = null;
    this.filtroTipo = '';
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

  // ---------- Selectores ----------

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
  }

  // ---------- Formulario crear/editar ----------

  abrirCrear(): void {
    this.editando.set(null);
    this.erroresServidor = null;
    this.form.reset({ mascotaId: null, veterinarioId: null, tipo: '', fechaAplicacion: '', proximaFecha: '', observaciones: '' });
    this.mascotaSeleccionInicial = null;
    this.veterinarioSeleccionInicial = null;
    this.mostrarFormulario.set(true);
    this.enfocarPrimerCampo();
  }

  abrirEditar(v: Vacuna): void {
    this.editando.set(v);
    this.erroresServidor = null;
    this.form.reset({
      mascotaId: v.mascotaId,
      veterinarioId: v.veterinarioId,
      tipo: v.tipo,
      fechaAplicacion: v.fechaAplicacion,
      proximaFecha: v.proximaFecha ?? '',
      observaciones: v.observaciones ?? '',
    });
    this.mascotaSeleccionInicial = { id: v.mascotaId, principal: v.mascotaNombre };
    this.veterinarioSeleccionInicial = v.veterinarioId ? { id: v.veterinarioId, principal: v.veterinarioNombre ?? '' } : null;
    this.mostrarFormulario.set(true);
    this.enfocarPrimerCampo();
  }

  cerrarFormulario(): void {
    this.mostrarFormulario.set(false);
    this.editando.set(null);
    this.erroresServidor = null;
  }

  private enfocarPrimerCampo(): void {
    queueMicrotask(() => document.getElementById('f-tipo')?.focus());
  }

  private enfocarPrimerCampoInvalido(): void {
    const orden = ['tipo', 'mascotaId', 'fechaAplicacion'];
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
    const payload: VacunaRequestPayload = {
      mascotaId: v.mascotaId as number,
      veterinarioId: v.veterinarioId ?? null,
      tipo: v.tipo as string,
      fechaAplicacion: v.fechaAplicacion as string,
      proximaFecha: v.proximaFecha || null,
      observaciones: v.observaciones || null,
    };

    this.guardando.set(true);
    const actual = this.editando();
    const peticion = actual ? this.api.actualizar(actual.id, payload) : this.api.crear(payload);

    peticion.subscribe({
      next: () => {
        this.guardando.set(false);
        this.mostrarExito(actual ? 'Vacuna actualizada correctamente.' : 'Vacuna registrada correctamente.');
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
    if (control?.hasError('min')) return 'Selecciona una mascota válida.';
    if (control?.hasError('maxlength')) {
      const max = control.getError('maxlength')?.requiredLength;
      return `Máximo ${max} caracteres.`;
    }
    return 'Valor inválido.';
  }

  // ---------- Eliminar ----------

  pedirConfirmacionEliminar(v: Vacuna): void {
    this.error.set('');
    this.vacunaAEliminar.set(v);
  }

  cancelarEliminar(): void {
    this.vacunaAEliminar.set(null);
  }

  confirmarEliminar(): void {
    const v = this.vacunaAEliminar();
    if (!v) return;

    this.eliminando.set(true);
    this.api.eliminar(v.id).subscribe({
      next: () => {
        this.eliminando.set(false);
        this.vacunaAEliminar.set(null);
        this.mostrarExito(`Vacuna "${v.tipo}" fue eliminada.`);
        if (this.vacunas().length === 1 && this.pagina() > 0) {
          this.pagina.set(this.pagina() - 1);
        }
        this.cargar();
      },
      error: (err: HttpErrorResponse) => {
        this.eliminando.set(false);
        this.vacunaAEliminar.set(null);
        this.error.set(this.problemDetail.mensaje(err));
      },
    });
  }
}
