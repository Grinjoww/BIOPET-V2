import { Component, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { ProblemDetailService } from '../core/problem-detail.service';
import { UsuarioSeleccionable, UsuarioSeleccionableApiService } from '../core/usuario-seleccionable-api.service';
import { Mascota, MascotaApiService, MascotaRequestPayload, ResumenEspecie } from './mascota-api.service';
import { PageHeaderComponent } from '../shared/page-header/page-header.component';
import { IconComponent } from '../shared/icons/icon.component';
import { FocusTrapDirective } from '../shared/focus-trap/focus-trap.directive';
import { formatearExpediente } from '../shared/presentacion';

const TAMANIO_PAGINA = 10;
const DURACION_MENSAJE_EXITO_MS = 4000;
const FILAS_SKELETON = 4;

/**
 * IMPORTANTE: este componente NO decide qué mascotas puede ver cada rol.
 * El backend (MascotaService.listar) ya filtra por dueño cuando el rol es
 * ROLE_DUENO y devuelve el listado completo para ADMIN/VETERINARIO/AUXILIAR.
 * Aquí solo consumimos tal cual lo que llega en `content` — filtrar de
 * nuevo en Angular sería redundante y, peor, podría ocultar un bug real
 * del backend en vez de exponerlo.
 *
 * Lo único que decide este componente es la VISIBILIDAD de los botones de
 * acción (crear/editar/eliminar), que es una mejora de UX, no un control
 * de seguridad: aunque alguien manipule el DOM y "reaparezca" el botón,
 * el backend seguirá rechazando la petición con 403 vía @PreAuthorize.
 */
@Component({
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, PageHeaderComponent, IconComponent, FocusTrapDirective],
  template: `
  <app-page-header
    eyebrow="Pacientes"
    title="Mascotas"
    [description]="esDueno ? 'Mascotas asociadas a tu cuenta.' : 'Listado de mascotas registradas en el sistema.'"
    [hasActions]="puedeGestionar">
    <button type="button" class="btn btn--primary" (click)="abrirCrear()">
      <app-icon name="anadir"></app-icon>
      Nueva mascota
    </button>
  </app-page-header>

  <div class="toolbar" role="toolbar" aria-label="Acciones de mascotas">
    <button type="button" class="btn btn--ghost btn--sm" (click)="cargar()" [disabled]="cargando()">
      Actualizar
    </button>
    <button type="button" class="btn btn--secondary btn--sm" (click)="alternarResumen()" [attr.aria-expanded]="mostrarResumen()">
      {{ mostrarResumen() ? 'Ocultar resumen por especie' : 'Ver resumen por especie' }}
    </button>
  </div>

  <!-- Mensajes de estado: aria-live para que lectores de pantalla los anuncien,
       y siempre con texto/ícono además de color (nunca solo color). -->
  <p class="alert alert--danger" role="alert" aria-live="assertive" *ngIf="error()">
    <strong>Error:</strong> {{ error() }}
  </p>
  <p class="alert alert--success" role="status" aria-live="polite" *ngIf="mensajeExito()">
    <strong>Listo:</strong> {{ mensajeExito() }}
  </p>

  <!-- ===== Resumen por especie ===== -->
  <section *ngIf="mostrarResumen()" aria-labelledby="resumen-titulo" class="panel">
    <h2 id="resumen-titulo" class="panel__title">Resumen por especie</h2>
    <p *ngIf="cargandoResumen()">Cargando resumen…</p>
    <p *ngIf="!cargandoResumen() && resumen().length === 0">No hay datos para mostrar.</p>
    <div class="table-wrap" *ngIf="!cargandoResumen() && resumen().length > 0">
      <table class="table-clinico">
        <caption class="sr-only">Cantidad de mascotas activas por especie</caption>
        <thead>
          <tr><th scope="col">Especie</th><th scope="col">Total</th></tr>
        </thead>
        <tbody>
          <tr *ngFor="let r of resumen()">
            <td>{{ r.especie }}</td>
            <td class="data">{{ r.total }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>

  <!-- ===== Formulario crear/editar ===== -->
  <section *ngIf="mostrarFormulario()" class="panel panel--record" aria-labelledby="form-titulo">
    <div class="panel__title">
      <h2 id="form-titulo">{{ editando() ? 'Editar mascota' : 'Nueva mascota' }}</h2>
      <p class="field-hint" *ngIf="editando() as m">
        Expediente <span class="data">{{ formatearExpediente(m.id) }}</span>
      </p>
    </div>

    <form [formGroup]="form" (ngSubmit)="guardar()" novalidate>
      <div class="field">
        <label for="f-duenioId">Dueño<span class="required-mark" aria-hidden="true">*</span></label>
        <select
          id="f-duenioId"
          formControlName="duenioId"
          [disabled]="cargandoDuenios()"
          [attr.aria-invalid]="tieneError('duenioId')"
          [attr.aria-describedby]="tieneError('duenioId') ? 'err-duenioId' : (errorDuenios() ? 'hint-duenioId' : null)">
          <option [ngValue]="null" disabled>
            {{ cargandoDuenios() ? 'Cargando dueños…' : (duenios().length === 0 && !errorDuenios() ? 'No hay dueños registrados todavía' : 'Selecciona un dueño') }}
          </option>
          <option *ngFor="let d of duenios()" [ngValue]="d.id">{{ d.nombre }} — {{ d.email }}</option>
        </select>
        <p class="field-error" id="err-duenioId" *ngIf="tieneError('duenioId')">
          {{ mensajeError('duenioId') }}
        </p>
        <p class="field-hint" id="hint-duenioId" *ngIf="!tieneError('duenioId') && errorDuenios()">
          {{ errorDuenios() }}
          <button type="button" class="btn btn--ghost btn--sm" (click)="cargarDuenios(true)">Reintentar</button>
        </p>
      </div>

      <div class="field">
        <label for="f-nombre">Nombre<span class="required-mark" aria-hidden="true">*</span></label>
        <input
          id="f-nombre"
          type="text"
          formControlName="nombre"
          [attr.aria-invalid]="tieneError('nombre')"
          [attr.aria-describedby]="tieneError('nombre') ? 'err-nombre' : null" />
        <p class="field-error" id="err-nombre" *ngIf="tieneError('nombre')">
          {{ mensajeError('nombre') }}
        </p>
      </div>

      <div class="field">
        <label for="f-especie">Especie<span class="required-mark" aria-hidden="true">*</span></label>
        <input
          id="f-especie"
          type="text"
          formControlName="especie"
          [attr.aria-invalid]="tieneError('especie')"
          [attr.aria-describedby]="tieneError('especie') ? 'err-especie' : null" />
        <p class="field-error" id="err-especie" *ngIf="tieneError('especie')">
          {{ mensajeError('especie') }}
        </p>
      </div>

      <div class="field">
        <label for="f-raza">Raza<span class="required-mark" aria-hidden="true">*</span></label>
        <input
          id="f-raza"
          type="text"
          formControlName="raza"
          [attr.aria-invalid]="tieneError('raza')"
          [attr.aria-describedby]="tieneError('raza') ? 'err-raza' : null" />
        <p class="field-error" id="err-raza" *ngIf="tieneError('raza')">
          {{ mensajeError('raza') }}
        </p>
      </div>

      <div class="field">
        <label for="f-fecha">Fecha de nacimiento<span class="required-mark" aria-hidden="true">*</span></label>
        <input
          id="f-fecha"
          type="date"
          formControlName="fechaNacimiento"
          [attr.aria-invalid]="tieneError('fechaNacimiento')"
          [attr.aria-describedby]="tieneError('fechaNacimiento') ? 'err-fecha' : null" />
        <p class="field-error" id="err-fecha" *ngIf="tieneError('fechaNacimiento')">
          {{ mensajeError('fechaNacimiento') }}
        </p>
      </div>

      <div class="modal-panel__actions">
        <button type="submit" class="btn btn--primary" [disabled]="guardando()">
          {{ guardando() ? 'Guardando…' : (editando() ? 'Guardar cambios' : 'Crear mascota') }}
        </button>
        <button type="button" class="btn btn--secondary" (click)="cerrarFormulario()" [disabled]="guardando()">
          Cancelar
        </button>
      </div>
    </form>
  </section>

  <!-- ===== Confirmación de borrado ===== -->
  <div class="modal-overlay" *ngIf="mascotaAEliminar() as m">
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
        ¿Eliminar a <strong>{{ m.nombre }}</strong> ({{ m.especie }})? Esta acción no se puede deshacer.
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

  <!-- ===== Listado =====
       Una sola tabla para los tres estados (cargando/vacío/con datos): la
       cabecera de columnas —incluida EXPEDIENTE— es siempre la misma
       estructura, así que la pantalla conserva su arquitectura de
       expediente incluso con 0 mascotas, en vez de colapsar a un mensaje
       suelto sobre fondo vacío. -->
  <span class="sr-only" role="status" aria-live="polite" *ngIf="cargando()">Cargando mascotas…</span>

  <div class="table-wrap">
    <table class="table-clinico">
      <caption class="sr-only">Listado de mascotas</caption>
      <thead>
        <tr>
          <th scope="col">Expediente</th>
          <th scope="col">Nombre</th>
          <th scope="col">Especie</th>
          <th scope="col">Raza</th>
          <th scope="col">Dueño</th>
          <th scope="col">Nace</th>
          <th scope="col" *ngIf="puedeGestionar"><span class="sr-only">Acciones</span></th>
        </tr>
      </thead>
      <tbody>
        <!-- Cargando: filas skeleton con la misma geometría que la tabla real. -->
        <ng-container *ngIf="cargando()">
          <tr class="skeleton-row" *ngFor="let fila of filasSkeleton" aria-hidden="true">
            <td><span class="skeleton-block"></span></td>
            <td><span class="skeleton-block"></span></td>
            <td><span class="skeleton-block"></span></td>
            <td><span class="skeleton-block"></span></td>
            <td><span class="skeleton-block"></span></td>
            <td><span class="skeleton-block"></span></td>
            <td *ngIf="puedeGestionar"></td>
          </tr>
        </ng-container>

        <!-- Vacío: una sola fila con colspan, dentro de la misma tabla. -->
        <tr class="table-clinico__empty-row" *ngIf="!cargando() && !error() && mascotas().length === 0">
          <td class="table-clinico__empty-cell" [attr.colspan]="puedeGestionar ? 7 : 6">
            <p class="table-clinico__empty-title">Sin expedientes todavía</p>
            <p class="table-clinico__empty-text" *ngIf="puedeGestionar">
              Registra la primera mascota para abrir su expediente clínico: vacunas,
              citas y consultas quedarán asociadas a partir de ese momento.
            </p>
            <p class="table-clinico__empty-text" *ngIf="!puedeGestionar">
              Cuando la clínica registre una mascota a tu nombre, aparecerá aquí.
            </p>
            <button type="button" class="btn btn--primary" *ngIf="puedeGestionar" (click)="abrirCrear()">
              <app-icon name="anadir"></app-icon>
              Registrar mascota
            </button>
          </td>
        </tr>

        <!-- Con datos. -->
        <ng-container *ngIf="!cargando()">
          <tr *ngFor="let m of mascotas()">
            <td data-label="Expediente" class="data">{{ formatearExpediente(m.id) }}</td>
            <td data-label="Nombre">
              <a class="record-link" [routerLink]="['/mascotas', m.id]" [attr.aria-label]="'Ver expediente de ' + m.nombre">
                {{ m.nombre }}
              </a>
            </td>
            <td data-label="Especie">{{ m.especie }}</td>
            <td data-label="Raza">{{ m.raza }}</td>
            <td data-label="Dueño">{{ m.duenioNombre }}</td>
            <td data-label="Nace" class="data">{{ m.fechaNacimiento }}</td>
            <td data-label="" class="actions" *ngIf="puedeGestionar">
              <button type="button" class="btn-icon" (click)="abrirEditar(m)" [attr.aria-label]="'Editar ' + m.nombre">
                <app-icon name="editar"></app-icon>
              </button>
              <button type="button" class="btn-icon btn-icon--danger" (click)="pedirConfirmacionEliminar(m)" [attr.aria-label]="'Eliminar ' + m.nombre">
                <app-icon name="eliminar"></app-icon>
              </button>
            </td>
          </tr>
        </ng-container>
      </tbody>
    </table>
  </div>

  <!-- ===== Paginación ===== -->
  <nav class="pagination" aria-label="Paginación de mascotas" *ngIf="!cargando() && totalPaginas() > 0">
    <button type="button" class="btn btn--secondary btn--sm" (click)="irAPagina(pagina() - 1)" [disabled]="pagina() === 0">
      Anterior
    </button>
    <span class="pagination__status" aria-live="polite">
      Página {{ pagina() + 1 }} de {{ totalPaginas() }} ({{ totalElementos() }} mascotas)
    </span>
    <button type="button" class="btn btn--secondary btn--sm" (click)="irAPagina(pagina() + 1)" [disabled]="pagina() + 1 >= totalPaginas()">
      Siguiente
    </button>
  </nav>
  `
})
export class MascotasComponent implements OnInit {
  mascotas = signal<Mascota[]>([]);
  pagina = signal(0);
  totalPaginas = signal(0);
  totalElementos = signal(0);

  cargando = signal(false);
  error = signal('');
  mensajeExito = signal('');

  mostrarResumen = signal(false);
  cargandoResumen = signal(false);
  resumen = signal<ResumenEspecie[]>([]);

  mostrarFormulario = signal(false);
  editando = signal<Mascota | null>(null);
  guardando = signal(false);
  private erroresServidor: Record<string, string[]> | null = null;

  mascotaAEliminar = signal<Mascota | null>(null);
  eliminando = signal(false);

  form = this.fb.group({
    duenioId: [null as number | null, [Validators.required, Validators.min(1)]],
    nombre: ['', [Validators.required, Validators.maxLength(50)]],
    especie: ['', [Validators.required, Validators.maxLength(30)]],
    raza: ['', [Validators.required, Validators.maxLength(50)]],
    fechaNacimiento: ['', [Validators.required]]
  });

  constructor(
    private api: MascotaApiService,
    private auth: AuthService,
    private problemDetail: ProblemDetailService,
    private usuarioSeleccionableApi: UsuarioSeleccionableApiService,
    private fb: FormBuilder
  ) {}

  ngOnInit(): void {
    this.cargar();
  }

  /**
   * ROLE_DUENO no puede crear, editar ni eliminar (regla del backend en
   * MascotaController: @PreAuthorize("hasAnyRole('ADMIN','VETERINARIO',
   * 'AUXILIAR')") en POST/PUT/DELETE). Esto solo oculta los botones para
   * esos usuarios; la regla real vive en el backend.
   */
  get puedeGestionar(): boolean {
    const rol = this.auth.usuarioActual()?.rol;
    return rol === 'ROLE_ADMIN' || rol === 'ROLE_VETERINARIO' || rol === 'ROLE_AUXILIAR';
  }

  get esDueno(): boolean {
    return this.auth.usuarioActual()?.rol === 'ROLE_DUENO';
  }

  /** Solo para iterar N filas skeleton en el template durante la carga. */
  readonly filasSkeleton = Array.from({ length: FILAS_SKELETON });

  /** Ver shared/presentacion.ts: formato VISUAL de Mascota.id, no un campo nuevo. */
  readonly formatearExpediente = formatearExpediente;

  // ---------- Selector real de dueño (Corrección B: GET /api/usuarios/duenios) ----------
  duenios = signal<UsuarioSeleccionable[]>([]);
  cargandoDuenios = signal(false);
  errorDuenios = signal('');
  private dueniosCargados = false;

  /**
   * Se carga de forma perezosa, solo al abrir el formulario por primera
   * vez (no en ngOnInit): un ROLE_DUENO nunca ve el botón que abre este
   * formulario, así que para esa sesión esta llamada nunca debe dispararse
   * — y si se disparara, el backend la rechazaría con 403 de todas formas
   * (@PreAuthorize en /api/usuarios/duenios exige ADMIN/VETERINARIO/AUXILIAR).
   */
  cargarDuenios(forzar = false): void {
    if (this.dueniosCargados && !forzar) return;
    this.cargandoDuenios.set(true);
    this.errorDuenios.set('');
    this.usuarioSeleccionableApi.listarDuenios().subscribe({
      next: (res) => {
        this.duenios.set(res ?? []);
        this.dueniosCargados = true;
        this.cargandoDuenios.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.cargandoDuenios.set(false);
        this.errorDuenios.set(this.problemDetail.mensaje(err));
      }
    });
  }

  /**
   * Muestra el banner de éxito y lo limpia solo tras un tiempo, en vez de
   * dejarlo en pantalla hasta la siguiente recarga (defecto real de la V1:
   * el mensaje se acumulaba indefinidamente).
   */
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
    this.api.listar(this.pagina(), TAMANIO_PAGINA).subscribe({
      next: (res) => {
        this.mascotas.set(res.content ?? []);
        this.totalPaginas.set(res.totalPages ?? 0);
        this.totalElementos.set(res.totalElements ?? 0);
        this.cargando.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.mascotas.set([]);
        this.cargando.set(false);
        this.error.set(this.problemDetail.mensaje(err));
      }
    });
  }

  irAPagina(nuevaPagina: number): void {
    if (nuevaPagina < 0 || nuevaPagina >= this.totalPaginas()) return;
    this.pagina.set(nuevaPagina);
    this.cargar();
  }

  // ---------- Resumen por especie ----------

  alternarResumen(): void {
    const nuevoValor = !this.mostrarResumen();
    this.mostrarResumen.set(nuevoValor);
    if (nuevoValor && this.resumen().length === 0) {
      this.cargarResumen();
    }
  }

  private cargarResumen(): void {
    this.cargandoResumen.set(true);
    this.api.resumenPorEspecies().subscribe({
      next: (res) => {
        this.resumen.set(res ?? []);
        this.cargandoResumen.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.cargandoResumen.set(false);
        this.error.set(this.problemDetail.mensaje(err));
      }
    });
  }

  // ---------- Formulario crear/editar ----------

  abrirCrear(): void {
    this.editando.set(null);
    this.erroresServidor = null;
    this.form.reset({ duenioId: null, nombre: '', especie: '', raza: '', fechaNacimiento: '' });
    this.mostrarFormulario.set(true);
    this.cargarDuenios();
    this.enfocarPrimerCampo();
  }

  abrirEditar(m: Mascota): void {
    this.editando.set(m);
    this.erroresServidor = null;
    this.form.reset({
      duenioId: m.duenioId,
      nombre: m.nombre,
      especie: m.especie,
      raza: m.raza,
      fechaNacimiento: m.fechaNacimiento
    });
    this.mostrarFormulario.set(true);
    this.cargarDuenios();
    this.enfocarPrimerCampo();
  }

  cerrarFormulario(): void {
    this.mostrarFormulario.set(false);
    this.editando.set(null);
    this.erroresServidor = null;
  }

  private enfocarPrimerCampo(): void {
    // Foco en el primer campo del formulario al abrirlo; si el guardado
    // falla por validación, enfocarPrimerCampoInvalido() se encarga de
    // mover el foco al campo con error real.
    queueMicrotask(() => document.getElementById('f-duenioId')?.focus());
  }

  private enfocarPrimerCampoInvalido(): void {
    const orden = ['duenioId', 'nombre', 'especie', 'raza', 'fechaNacimiento'];
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
    const payload: MascotaRequestPayload = {
      duenioId: v.duenioId as number,
      nombre: v.nombre as string,
      especie: v.especie as string,
      raza: v.raza as string,
      fechaNacimiento: v.fechaNacimiento as string
    };

    this.guardando.set(true);
    const actual = this.editando();
    const peticion = actual ? this.api.actualizar(actual.id, payload) : this.api.crear(payload);

    peticion.subscribe({
      next: () => {
        this.guardando.set(false);
        this.mostrarExito(actual ? 'Mascota actualizada correctamente.' : 'Mascota creada correctamente.');
        this.cerrarFormulario();
        this.cargar();
        if (this.mostrarResumen()) this.cargarResumen();
      },
      error: (err: HttpErrorResponse) => {
        this.guardando.set(false);
        this.erroresServidor = this.problemDetail.erroresPorCampo(err);
        if (this.erroresServidor) {
          // Errores 422 por campo: se muestran debajo de cada input.
          this.enfocarPrimerCampoInvalido();
        } else {
          // Cualquier otro error (401/403/404/409/429/500…) va al banner general.
          this.error.set(this.problemDetail.mensaje(err));
        }
      }
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
    if (control?.hasError('min')) return 'El valor debe ser mayor a cero.';
    if (control?.hasError('maxlength')) {
      const max = control.getError('maxlength')?.requiredLength;
      return `Máximo ${max} caracteres.`;
    }
    return 'Valor inválido.';
  }

  // ---------- Eliminar ----------

  pedirConfirmacionEliminar(m: Mascota): void {
    this.error.set('');
    this.mascotaAEliminar.set(m);
  }

  cancelarEliminar(): void {
    this.mascotaAEliminar.set(null);
  }

  confirmarEliminar(): void {
    const m = this.mascotaAEliminar();
    if (!m) return;

    this.eliminando.set(true);
    this.api.eliminar(m.id).subscribe({
      next: () => {
        this.eliminando.set(false);
        this.mascotaAEliminar.set(null);
        this.mostrarExito(`"${m.nombre}" fue eliminada.`);
        // Si esta era la última mascota de la página actual y no es la
        // primera página, retrocede una página para no quedar en blanco.
        if (this.mascotas().length === 1 && this.pagina() > 0) {
          this.pagina.set(this.pagina() - 1);
        }
        this.cargar();
        if (this.mostrarResumen()) this.cargarResumen();
      },
      error: (err: HttpErrorResponse) => {
        // Incluye el caso 403 (por ejemplo, si el rol cambió en otra pestaña
        // o el usuario manipuló el DOM para reaparecer el botón) y el caso
        // 404 (la mascota ya fue eliminada por otro usuario).
        this.eliminando.set(false);
        this.mascotaAEliminar.set(null);
        this.error.set(this.problemDetail.mensaje(err));
      }
    });
  }
}