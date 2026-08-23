import { Component, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { AuthService } from '../core/auth.service';
import { ProblemDetailService } from '../core/problem-detail.service';
import { UsuarioSeleccionable, UsuarioSeleccionableApiService } from '../core/usuario-seleccionable-api.service';
import { Mascota, MascotaApiService } from './mascota-api.service';
import { Consulta, ConsultaApiService, ConsultaRequestPayload } from './consulta-api.service';
import { PageHeaderComponent } from '../shared/page-header/page-header.component';
import { IconComponent } from '../shared/icons/icon.component';
import { FocusTrapDirective } from '../shared/focus-trap/focus-trap.directive';
import { fechaHoraLocalAInstant, formatearExpediente, instantAFechaHoraLocal } from '../shared/presentacion';

const TAMANIO_PAGINA = 10;
const DURACION_MENSAJE_EXITO_MS = 4000;
const FILAS_SKELETON = 2;
/**
 * No existe un endpoint "selector" para mascotas: reutilizamos
 * GET /api/mascotas —ya auditado como seguro y sin filtrar para
 * ADMIN/VETERINARIO/AUXILIAR en Vacunas V2 y Citas V2— con una página
 * lo bastante grande para cubrir el listado completo de la clínica.
 */
const TAMANIO_PAGINA_SELECTOR_MASCOTAS = 200;

/**
 * IMPORTANTE — reglas reales de ConsultaController/ConsultaService
 * (auditadas en esta fase, no inventadas):
 *
 *  - GET /api/consultas (listar), GET /api/consultas/{id} y
 *    GET /api/consultas/mascota/{id}: los 4 roles pueden leer.
 *    ROLE_DUENO ve solo consultas de mascotas propias — filtrado real en
 *    el servidor vía `findAllByMascota_Duenio_IdAndActivoTrue` (la
 *    Corrección A de aislamiento de esta misma fase anterior, que este
 *    componente NO toca ni duplica en el cliente).
 *  - POST /api/consultas (crear): ADMIN, VETERINARIO y AUXILIAR. Nunca
 *    DUENO. `ConsultaService.crear` no exige que el VETERINARIO que crea
 *    sea el mismo `veterinarioId` del registro — cualquiera de los 3
 *    roles puede asignar cualquier veterinario real. No es una decisión
 *    de esta UI: es lo que el servicio realmente permite.
 *  - PUT /api/consultas/{id} y DELETE /api/consultas/{id}: los mismos 3
 *    roles, con `verificarAccesoMascota` — a diferencia de Citas, aquí
 *    NO hay restricción de "solo mis consultas asignadas" para
 *    VETERINARIO: cualquiera de los 3 roles clínicos puede editar o dar
 *    de baja cualquier consulta activa. Confirmado leyendo el método
 *    (no es una suposición por similitud con Citas).
 *  - DELETE es baja lógica real (`activo=false`, ConsultaService.eliminar):
 *    el registro deja de aparecer en cualquier listado/búsqueda de la
 *    API (incluida la pestaña Consultas de la ficha), pero no se borra
 *    de la base de datos. El copy de esta pantalla dice "dar de baja",
 *    nunca "eliminar definitivamente".
 *
 * Este componente no decide qué consultas ve cada rol ni si una
 * escritura es válida: solo oculta controles que el backend rechazaría
 * de todas formas. El 403 real sigue viviendo en @PreAuthorize /
 * ConsultaService.
 */
@Component({
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, PageHeaderComponent, IconComponent, FocusTrapDirective],
  template: `
  <app-page-header
    eyebrow="Registro clínico"
    title="Consultas"
    [description]="descripcion"
    [hasActions]="puedeCrear">
    <button type="button" class="btn btn--primary" (click)="abrirCrear()">
      <app-icon name="anadir"></app-icon>
      Nueva consulta
    </button>
  </app-page-header>

  <div class="toolbar" role="toolbar" aria-label="Acciones de consultas">
    <button type="button" class="btn btn--ghost btn--sm" (click)="cargar()" [disabled]="cargando()">
      Actualizar
    </button>
  </div>

  <p class="alert alert--danger" role="alert" aria-live="assertive" *ngIf="error()">
    <strong>Error:</strong> {{ error() }}
  </p>
  <p class="alert alert--success" role="status" aria-live="polite" *ngIf="mensajeExito()">
    <strong>Listo:</strong> {{ mensajeExito() }}
  </p>

  <!-- ===== Formulario crear/editar ===== -->
  <section *ngIf="mostrarFormulario()" class="panel panel--record" aria-labelledby="form-titulo">
    <div class="panel__title">
      <h2 id="form-titulo">{{ editando() ? 'Editar consulta' : 'Nueva consulta' }}</h2>
      <p class="field-hint" *ngIf="editando() as c">
        Expediente <span class="data">{{ formatearExpediente(c.mascotaId) }}</span> · {{ c.mascotaNombre }}
      </p>
    </div>

    <form [formGroup]="form" (ngSubmit)="guardar()" novalidate>
      <span class="label form-section-label">Atención</span>
      <div class="field">
        <label for="f-mascotaId">Mascota<span class="required-mark" aria-hidden="true">*</span></label>
        <select
          id="f-mascotaId"
          formControlName="mascotaId"
          [disabled]="cargandoMascotas()"
          [attr.aria-invalid]="tieneError('mascotaId')"
          [attr.aria-describedby]="tieneError('mascotaId') ? 'err-mascotaId' : (errorMascotas() ? 'hint-mascotaId' : null)">
          <option [ngValue]="null" disabled>
            {{ cargandoMascotas() ? 'Cargando mascotas…' : (mascotasSelect().length === 0 && !errorMascotas() ? 'No hay mascotas registradas' : 'Selecciona una mascota') }}
          </option>
          <option *ngFor="let m of mascotasSelect()" [ngValue]="m.id">
            {{ m.nombre }} — {{ formatearExpediente(m.id) }} — {{ m.duenioNombre }}
          </option>
        </select>
        <p class="field-error" id="err-mascotaId" *ngIf="tieneError('mascotaId')">{{ mensajeError('mascotaId') }}</p>
        <p class="field-hint" id="hint-mascotaId" *ngIf="!tieneError('mascotaId') && errorMascotas()">
          {{ errorMascotas() }}
          <button type="button" class="btn btn--ghost btn--sm" (click)="cargarMascotas(true)">Reintentar</button>
        </p>
      </div>

      <div class="field">
        <label for="f-veterinarioId">Veterinario<span class="required-mark" aria-hidden="true">*</span></label>
        <select
          id="f-veterinarioId"
          formControlName="veterinarioId"
          [disabled]="cargandoVeterinarios()"
          [attr.aria-invalid]="tieneError('veterinarioId')"
          [attr.aria-describedby]="tieneError('veterinarioId') ? 'err-veterinarioId' : (errorVeterinarios() ? 'hint-veterinarioId' : null)">
          <option [ngValue]="null" disabled>
            {{ cargandoVeterinarios() ? 'Cargando veterinarios…' : (veterinarios().length === 0 && !errorVeterinarios() ? 'No hay veterinarios registrados' : 'Selecciona un veterinario') }}
          </option>
          <option *ngFor="let vet of veterinarios()" [ngValue]="vet.id">{{ vet.nombre }} — {{ vet.email }}</option>
        </select>
        <p class="field-error" id="err-veterinarioId" *ngIf="tieneError('veterinarioId')">{{ mensajeError('veterinarioId') }}</p>
        <p class="field-hint" id="hint-veterinarioId" *ngIf="!tieneError('veterinarioId') && errorVeterinarios()">
          {{ errorVeterinarios() }}
          <button type="button" class="btn btn--ghost btn--sm" (click)="cargarVeterinarios(true)">Reintentar</button>
        </p>
      </div>

      <div class="field">
        <label for="f-fechaConsulta">Fecha y hora<span class="required-mark" aria-hidden="true">*</span></label>
        <input
          id="f-fechaConsulta"
          type="datetime-local"
          formControlName="fechaConsulta"
          [attr.max]="maxFechaLocal"
          [attr.aria-invalid]="tieneError('fechaConsulta')"
          [attr.aria-describedby]="tieneError('fechaConsulta') ? 'err-fechaConsulta' : 'hint-fechaConsulta'" />
        <p class="field-hint" id="hint-fechaConsulta" *ngIf="!tieneError('fechaConsulta')">No puede ser una fecha futura.</p>
        <p class="field-error" id="err-fechaConsulta" *ngIf="tieneError('fechaConsulta')">{{ mensajeError('fechaConsulta') }}</p>
      </div>

      <span class="label form-section-label">Motivo</span>
      <div class="field">
        <label for="f-motivo">Motivo de la consulta<span class="required-mark" aria-hidden="true">*</span></label>
        <input
          id="f-motivo"
          type="text"
          formControlName="motivo"
          [attr.aria-invalid]="tieneError('motivo')"
          [attr.aria-describedby]="tieneError('motivo') ? 'err-motivo' : null" />
        <p class="field-error" id="err-motivo" *ngIf="tieneError('motivo')">{{ mensajeError('motivo') }}</p>
      </div>

      <span class="label form-section-label">Evaluación</span>
      <div class="field">
        <label for="f-diagnostico">Diagnóstico</label>
        <textarea
          id="f-diagnostico"
          formControlName="diagnostico"
          [attr.aria-invalid]="tieneError('diagnostico')"
          [attr.aria-describedby]="tieneError('diagnostico') ? 'err-diagnostico' : null"></textarea>
        <p class="field-error" id="err-diagnostico" *ngIf="tieneError('diagnostico')">{{ mensajeError('diagnostico') }}</p>
      </div>

      <span class="label form-section-label">Indicaciones</span>
      <div class="field">
        <label for="f-tratamiento">Tratamiento</label>
        <textarea
          id="f-tratamiento"
          formControlName="tratamiento"
          [attr.aria-invalid]="tieneError('tratamiento')"
          [attr.aria-describedby]="tieneError('tratamiento') ? 'err-tratamiento' : null"></textarea>
        <p class="field-error" id="err-tratamiento" *ngIf="tieneError('tratamiento')">{{ mensajeError('tratamiento') }}</p>
      </div>

      <span class="label form-section-label">Notas clínicas</span>
      <div class="field">
        <label for="f-observaciones">Observaciones</label>
        <textarea
          id="f-observaciones"
          formControlName="observaciones"
          [attr.aria-invalid]="tieneError('observaciones')"
          [attr.aria-describedby]="tieneError('observaciones') ? 'err-observaciones' : null"></textarea>
        <p class="field-error" id="err-observaciones" *ngIf="tieneError('observaciones')">{{ mensajeError('observaciones') }}</p>
      </div>

      <div class="modal-panel__actions">
        <button type="submit" class="btn btn--primary" [disabled]="guardando()">
          {{ guardando() ? 'Guardando…' : (editando() ? 'Guardar cambios' : 'Registrar consulta') }}
        </button>
        <button type="button" class="btn btn--secondary" (click)="cerrarFormulario()" [disabled]="guardando()">
          Cancelar
        </button>
      </div>
    </form>
  </section>

  <!-- ===== Confirmación de baja ===== -->
  <div class="modal-overlay" *ngIf="consultaABajar() as c">
    <div
      class="modal-panel"
      appFocusTrap
      (keydown.escape)="cancelarBaja()"
      role="alertdialog"
      aria-modal="true"
      aria-labelledby="confirm-titulo"
      aria-describedby="confirm-texto">
      <h2 id="confirm-titulo">Dar de baja el registro</h2>
      <p id="confirm-texto">
        ¿Dar de baja la consulta de <strong>{{ c.mascotaNombre }}</strong> del
        <span class="data">{{ c.fechaConsulta | date: "d 'de' MMMM y, HH:mm" }}</span>? Dejará de aparecer en el
        historial clínico visible; los datos no se destruyen en la base de datos.
      </p>
      <div class="modal-panel__actions">
        <button type="button" class="btn btn--danger-solid" (click)="confirmarBaja()" [disabled]="dandoDeBaja()">
          {{ dandoDeBaja() ? 'Procesando…' : 'Sí, dar de baja' }}
        </button>
        <button type="button" class="btn btn--secondary" (click)="cancelarBaja()" [disabled]="dandoDeBaja()">
          Volver
        </button>
      </div>
    </div>
  </div>

  <!-- ===== Listado: registros clínicos compactos, no tabla ===== -->
  <span class="sr-only" role="status" aria-live="polite" *ngIf="cargando()">Cargando consultas…</span>

  <div *ngIf="cargando()" aria-hidden="true">
    <div class="panel panel--record record-entry" *ngFor="let fila of filasSkeleton">
      <span class="skeleton-block" style="width:40%"></span>
    </div>
  </div>

  <div class="empty-record" *ngIf="!cargando() && !error() && consultas().length === 0">
    <p class="table-clinico__empty-title">Sin consultas registradas</p>
    <p class="table-clinico__empty-text" *ngIf="puedeCrear">
      Registra la primera consulta para empezar el historial clínico.
    </p>
    <p class="table-clinico__empty-text" *ngIf="!puedeCrear">
      Cuando la clínica registre una consulta, aparecerá aquí.
    </p>
    <button type="button" class="btn btn--primary" *ngIf="puedeCrear" (click)="abrirCrear()">
      <app-icon name="anadir"></app-icon>
      Nueva consulta
    </button>
  </div>

  <div *ngIf="!cargando()">
    <article class="panel panel--record record-entry" *ngFor="let c of consultas()">
      <div class="record-entry__head">
        <div class="record-entry__titulo">
          <a class="record-link" [routerLink]="['/mascotas', c.mascotaId]" [attr.aria-label]="'Ver expediente de ' + c.mascotaNombre">
            {{ c.mascotaNombre }}
          </a>
          <time class="data" [attr.datetime]="c.fechaConsulta">{{ c.fechaConsulta | date: "d 'de' MMMM y, HH:mm" }}</time>
        </div>
        <div class="actions" *ngIf="puedeGestionar">
          <button
            type="button"
            class="btn-icon"
            (click)="abrirEditar(c)"
            [attr.aria-label]="'Editar consulta de ' + c.mascotaNombre + ' del ' + (c.fechaConsulta | date: 'd MMM y')">
            <app-icon name="editar"></app-icon>
          </button>
          <button
            type="button"
            class="btn-icon btn-icon--danger"
            (click)="pedirConfirmacionBaja(c)"
            [attr.aria-label]="'Dar de baja la consulta de ' + c.mascotaNombre + ' del ' + (c.fechaConsulta | date: 'd MMM y')">
            <app-icon name="eliminar"></app-icon>
          </button>
        </div>
      </div>

      <dl class="record-meta-grid">
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
      </dl>
    </article>
  </div>

  <!-- ===== Paginación ===== -->
  <nav class="pagination" aria-label="Paginación de consultas" *ngIf="!cargando() && totalPaginas() > 0">
    <button type="button" class="btn btn--secondary btn--sm" (click)="irAPagina(pagina() - 1)" [disabled]="pagina() === 0">
      Anterior
    </button>
    <span class="pagination__status" aria-live="polite">
      Página {{ pagina() + 1 }} de {{ totalPaginas() }} ({{ totalElementos() }} consultas)
    </span>
    <button type="button" class="btn btn--secondary btn--sm" (click)="irAPagina(pagina() + 1)" [disabled]="pagina() + 1 >= totalPaginas()">
      Siguiente
    </button>
  </nav>
  `,
})
export class ConsultasComponent implements OnInit {
  readonly formatearExpediente = formatearExpediente;
  readonly filasSkeleton = Array.from({ length: FILAS_SKELETON });

  consultas = signal<Consulta[]>([]);
  pagina = signal(0);
  totalPaginas = signal(0);
  totalElementos = signal(0);

  cargando = signal(false);
  error = signal('');
  mensajeExito = signal('');

  mostrarFormulario = signal(false);
  editando = signal<Consulta | null>(null);
  guardando = signal(false);
  private erroresServidor: Record<string, string[]> | null = null;

  consultaABajar = signal<Consulta | null>(null);
  dandoDeBaja = signal(false);

  // ---------- Selectores del formulario ----------
  mascotasSelect = signal<Mascota[]>([]);
  cargandoMascotas = signal(false);
  errorMascotas = signal('');
  private mascotasCargadas = false;

  veterinarios = signal<UsuarioSeleccionable[]>([]);
  cargandoVeterinarios = signal(false);
  errorVeterinarios = signal('');
  private veterinariosCargados = false;

  /**
   * `ConsultaRequest.fechaConsulta` es `@PastOrPresent`: el backend
   * rechaza cualquier fecha futura con 422. Se refleja aquí como `max`
   * del input —solo UX, evita un envío que el servidor rechazaría de
   * todas formas— calculado una vez al cargar el componente, en hora
   * local del navegador (mismo criterio que instantAFechaHoraLocal).
   */
  readonly maxFechaLocal = instantAFechaHoraLocal(new Date().toISOString());

  form = this.fb.group({
    mascotaId: [null as number | null, [Validators.required, Validators.min(1)]],
    veterinarioId: [null as number | null, [Validators.required, Validators.min(1)]],
    fechaConsulta: ['', [Validators.required]],
    motivo: ['', [Validators.required, Validators.maxLength(200)]],
    diagnostico: ['', [Validators.maxLength(500)]],
    tratamiento: ['', [Validators.maxLength(500)]],
    observaciones: ['', [Validators.maxLength(500)]],
  });

  constructor(
    private api: ConsultaApiService,
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

  /** POST /api/consultas: @PreAuthorize("hasAnyRole('ADMIN','VETERINARIO','AUXILIAR')"). */
  get puedeCrear(): boolean {
    const rol = this.auth.usuarioActual()?.rol;
    return rol === 'ROLE_ADMIN' || rol === 'ROLE_VETERINARIO' || rol === 'ROLE_AUXILIAR';
  }

  /**
   * PUT/DELETE /api/consultas/{id}: mismos 3 roles que puedeCrear, y a
   * diferencia de Citas, ConsultaService.verificarAccesoMascota no
   * restringe a "mis consultas asignadas" — cualquiera de los 3 puede
   * editar o dar de baja cualquier consulta activa. Por eso aquí es un
   * getter de rol, no una función por fila como en Citas.
   */
  get puedeGestionar(): boolean {
    return this.puedeCrear;
  }

  get esDueno(): boolean {
    return this.auth.usuarioActual()?.rol === 'ROLE_DUENO';
  }

  get descripcion(): string {
    if (this.esDueno) return 'Consulta el historial clínico de tus mascotas.';
    return 'Historial clínico y registro de atenciones de la clínica.';
  }

  private mostrarExito(mensaje: string): void {
    this.mensajeExito.set(mensaje);
    setTimeout(() => {
      if (this.mensajeExito() === mensaje) this.mensajeExito.set('');
    }, DURACION_MENSAJE_EXITO_MS);
  }

  // ---------- Listado / paginación ----------

  /**
   * Orden cronológico descendente por defecto: un historial clínico se
   * lee empezando por lo más reciente, al contrario que la agenda de
   * Citas (que ordena ascendente, por lo próximo).
   */
  cargar(): void {
    this.error.set('');
    this.cargando.set(true);
    this.api.listar(this.pagina(), TAMANIO_PAGINA, 'fechaConsulta,desc').subscribe({
      next: (res) => {
        this.consultas.set(res.content ?? []);
        this.totalPaginas.set(res.totalPages ?? 0);
        this.totalElementos.set(res.totalElements ?? 0);
        this.cargando.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.consultas.set([]);
        this.cargando.set(false);
        this.error.set(this.problemDetail.mensaje(err));
      },
    });
  }

  irAPagina(nuevaPagina: number): void {
    if (nuevaPagina < 0 || nuevaPagina >= this.totalPaginas()) return;
    this.pagina.set(nuevaPagina);
    this.cargar();
  }

  // ---------- Selectores ----------

  cargarMascotas(forzar = false): void {
    if (this.mascotasCargadas && !forzar) return;
    this.cargandoMascotas.set(true);
    this.errorMascotas.set('');
    this.mascotaApi.listar(0, TAMANIO_PAGINA_SELECTOR_MASCOTAS, 'nombre,asc').subscribe({
      next: (res) => {
        this.mascotasSelect.set(res.content ?? []);
        this.mascotasCargadas = true;
        this.cargandoMascotas.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.cargandoMascotas.set(false);
        this.errorMascotas.set(this.problemDetail.mensaje(err));
      },
    });
  }

  cargarVeterinarios(forzar = false): void {
    if (this.veterinariosCargados && !forzar) return;
    this.cargandoVeterinarios.set(true);
    this.errorVeterinarios.set('');
    this.usuarioSeleccionableApi.listarVeterinarios().subscribe({
      next: (res) => {
        this.veterinarios.set(res ?? []);
        this.veterinariosCargados = true;
        this.cargandoVeterinarios.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.cargandoVeterinarios.set(false);
        this.errorVeterinarios.set(this.problemDetail.mensaje(err));
      },
    });
  }

  // ---------- Formulario crear/editar ----------

  abrirCrear(): void {
    this.editando.set(null);
    this.erroresServidor = null;
    this.form.reset({
      mascotaId: null,
      veterinarioId: null,
      fechaConsulta: '',
      motivo: '',
      diagnostico: '',
      tratamiento: '',
      observaciones: '',
    });
    this.mostrarFormulario.set(true);
    this.cargarMascotas();
    this.cargarVeterinarios();
    this.enfocarPrimerCampo();
  }

  abrirEditar(c: Consulta): void {
    this.editando.set(c);
    this.erroresServidor = null;
    this.form.reset({
      mascotaId: c.mascotaId,
      veterinarioId: c.veterinarioId,
      fechaConsulta: instantAFechaHoraLocal(c.fechaConsulta),
      motivo: c.motivo,
      diagnostico: c.diagnostico ?? '',
      tratamiento: c.tratamiento ?? '',
      observaciones: c.observaciones ?? '',
    });
    this.mostrarFormulario.set(true);
    this.cargarMascotas();
    this.cargarVeterinarios();
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
    const orden = ['mascotaId', 'veterinarioId', 'fechaConsulta', 'motivo'];
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
    const payload: ConsultaRequestPayload = {
      mascotaId: v.mascotaId as number,
      veterinarioId: v.veterinarioId as number,
      fechaConsulta: fechaHoraLocalAInstant(v.fechaConsulta as string),
      motivo: v.motivo as string,
      diagnostico: v.diagnostico || null,
      tratamiento: v.tratamiento || null,
      observaciones: v.observaciones || null,
    };

    this.guardando.set(true);
    const actual = this.editando();
    const peticion = actual ? this.api.actualizar(actual.id, payload) : this.api.crear(payload);

    peticion.subscribe({
      next: () => {
        this.guardando.set(false);
        this.mostrarExito(actual ? 'Consulta actualizada correctamente.' : 'Consulta registrada correctamente.');
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

  // ---------- Dar de baja ----------

  pedirConfirmacionBaja(c: Consulta): void {
    this.error.set('');
    this.consultaABajar.set(c);
  }

  cancelarBaja(): void {
    this.consultaABajar.set(null);
  }

  confirmarBaja(): void {
    const c = this.consultaABajar();
    if (!c) return;

    this.dandoDeBaja.set(true);
    this.api.eliminar(c.id).subscribe({
      next: () => {
        this.dandoDeBaja.set(false);
        this.consultaABajar.set(null);
        this.mostrarExito(`La consulta de ${c.mascotaNombre} fue dada de baja.`);
        if (this.consultas().length === 1 && this.pagina() > 0) {
          this.pagina.set(this.pagina() - 1);
        }
        this.cargar();
      },
      error: (err: HttpErrorResponse) => {
        this.dandoDeBaja.set(false);
        this.consultaABajar.set(null);
        this.error.set(this.problemDetail.mensaje(err));
      },
    });
  }
}
