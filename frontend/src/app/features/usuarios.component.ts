import { Component, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { AuthService } from '../core/auth.service';
import { ProblemDetailService } from '../core/problem-detail.service';
import { humanizarRol, RolBiopet } from '../core/roles';
import { Usuario, UsuarioApiService, UsuarioRequestPayload } from './usuario-api.service';
import { PageHeaderComponent } from '../shared/page-header/page-header.component';
import { IconComponent } from '../shared/icons/icon.component';
import { FocusTrapDirective } from '../shared/focus-trap/focus-trap.directive';

const TAMANIO_PAGINA = 10;
const DURACION_MENSAJE_EXITO_MS = 4000;
const FILAS_SKELETON = 4;

/** Los 4 roles reales del backend (com.biopet.entity.Rol) — ningún valor inventado. */
const ROLES_DISPONIBLES: RolBiopet[] = ['ROLE_ADMIN', 'ROLE_VETERINARIO', 'ROLE_AUXILIAR', 'ROLE_DUENO'];

/**
 * IMPORTANTE — reglas reales de UsuarioController/UsuarioService
 * (auditadas en esta fase, no inventadas):
 *
 *  - Los 4 métodos (GET listar, GET buscar, POST, PUT, DELETE) exigen
 *    @PreAuthorize("hasRole('ADMIN')"). Ningún otro rol tiene acceso —
 *    a diferencia de /duenios y /veterinarios (selectores de solo
 *    lectura para ADMIN/VETERINARIO/AUXILIAR, sin tocar en esta fase).
 *  - GET /api/usuarios SOLO devuelve usuarios con activo=true
 *    (`findAllByActivoTrue`): no existe forma de ver ni reactivar un
 *    usuario dado de baja desde esta pantalla ni desde ningún endpoint
 *    — por eso esta UI nunca ofrece "reactivar", y por lo que la
 *    columna Estado siempre mostrará "Activo" en cada fila listada
 *    (documentado también en el informe de esta fase, no es un error).
 *  - POST exige password (validado en el Service, no con @NotBlank).
 *    PUT NO exige password: vacío/null conserva la actual
 *    (UsuarioService.actualizar). Ninguno de los dos permite tocar
 *    `activo` — UsuarioRequest ni siquiera tiene ese campo.
 *  - PUT rechaza con 403 que un ADMIN cambie su PROPIO rol
 *    (`autenticado.getId().equals(usuario.getId()) && rol distinto`).
 *    Esta UI deshabilita el selector de rol al editarse a sí mismo para
 *    no ofrecer un cambio que el backend rechazaría, y sigue enviando
 *    el rol actual sin modificar (campo obligatorio en el payload).
 *  - DELETE (baja lógica, activo=false) NO tiene ninguna protección de
 *    autodesactivación ni de "último admin" en el backend — ver
 *    hallazgo detallado en el informe de esta fase. Esta UI oculta la
 *    acción "Dar de baja" sobre la fila del propio usuario autenticado
 *    como mitigación de UX (evita el accidente más común), pero esto NO
 *    es la corrección real: el backend sigue sin protegerlo si se llama
 *    directamente a la API.
 */
@Component({
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, PageHeaderComponent, IconComponent, FocusTrapDirective],
  template: `
  <app-page-header
    eyebrow="Administración"
    title="Usuarios"
    description="Gestiona las cuentas y roles con acceso a BIOPET."
    [hasActions]="true">
    <button type="button" class="btn btn--primary" (click)="abrirCrear()">
      <app-icon name="anadir"></app-icon>
      Nuevo usuario
    </button>
  </app-page-header>

  <div class="toolbar" role="toolbar" aria-label="Acciones de usuarios">
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
      <h2 id="form-titulo">{{ editando() ? 'Editar usuario' : 'Nuevo usuario' }}</h2>
      <p class="field-hint" *ngIf="editandoASiMismo()">
        Estás editando tu propia cuenta: no puedes cambiar tu propio rol.
      </p>
    </div>

    <form [formGroup]="form" (ngSubmit)="guardar()" novalidate>
      <div class="field">
        <label for="f-nombre">Nombre completo<span class="required-mark" aria-hidden="true">*</span></label>
        <input
          id="f-nombre"
          type="text"
          formControlName="nombre"
          [attr.aria-invalid]="tieneError('nombre')"
          [attr.aria-describedby]="tieneError('nombre') ? 'err-nombre' : null" />
        <p class="field-error" id="err-nombre" *ngIf="tieneError('nombre')">{{ mensajeError('nombre') }}</p>
      </div>

      <div class="field">
        <label for="f-email">Correo electrónico<span class="required-mark" aria-hidden="true">*</span></label>
        <input
          id="f-email"
          type="email"
          formControlName="email"
          autocomplete="off"
          [attr.aria-invalid]="tieneError('email')"
          [attr.aria-describedby]="tieneError('email') ? 'err-email' : null" />
        <p class="field-error" id="err-email" *ngIf="tieneError('email')">{{ mensajeError('email') }}</p>
      </div>

      <div class="field">
        <label for="f-rol">Rol<span class="required-mark" aria-hidden="true">*</span></label>
        <select
          id="f-rol"
          formControlName="rol"
          [attr.aria-invalid]="tieneError('rol')"
          [attr.aria-describedby]="tieneError('rol') ? 'err-rol' : null">
          <option *ngFor="let r of roles" [ngValue]="r">{{ humanizarRol(r) }}</option>
        </select>
        <p class="field-error" id="err-rol" *ngIf="tieneError('rol')">{{ mensajeError('rol') }}</p>
      </div>

      <div class="field">
        <label for="f-password">
          Contraseña<span class="required-mark" aria-hidden="true" *ngIf="!editando()">*</span>
        </label>
        <div class="password-field">
          <input
            id="f-password"
            [type]="mostrarPassword() ? 'text' : 'password'"
            formControlName="password"
            autocomplete="new-password"
            [attr.aria-invalid]="tieneError('password')"
            [attr.aria-describedby]="tieneError('password') ? 'err-password' : 'hint-password'" />
          <button
            type="button"
            class="btn btn--ghost btn--sm"
            (click)="mostrarPassword.set(!mostrarPassword())"
            [attr.aria-pressed]="mostrarPassword()"
            aria-label="Mostrar u ocultar la contraseña escrita">
            {{ mostrarPassword() ? 'Ocultar' : 'Mostrar' }}
          </button>
        </div>
        <p class="field-hint" id="hint-password" *ngIf="!tieneError('password') && editando()">
          Déjala en blanco para no cambiar la contraseña actual.
        </p>
        <p class="field-error" id="err-password" *ngIf="tieneError('password')">{{ mensajeError('password') }}</p>
      </div>

      <div class="modal-panel__actions">
        <button type="submit" class="btn btn--primary" [disabled]="guardando()">
          {{ guardando() ? 'Guardando…' : (editando() ? 'Guardar cambios' : 'Crear usuario') }}
        </button>
        <button type="button" class="btn btn--secondary" (click)="cerrarFormulario()" [disabled]="guardando()">
          Cancelar
        </button>
      </div>
    </form>
  </section>

  <!-- ===== Confirmación de baja ===== -->
  <div class="modal-overlay" *ngIf="usuarioABajar() as u">
    <div
      class="modal-panel"
      appFocusTrap
      (keydown.escape)="cancelarBaja()"
      role="alertdialog"
      aria-modal="true"
      aria-labelledby="confirm-titulo"
      aria-describedby="confirm-texto">
      <h2 id="confirm-titulo">Dar de baja usuario</h2>
      <p id="confirm-texto">
        ¿Dar de baja la cuenta de <strong>{{ u.nombre }}</strong> ({{ u.email }})? La cuenta dejará de poder
        utilizar BIOPET, pero su registro no se eliminará de la base de datos.
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

  <!-- ===== Listado ===== -->
  <span class="sr-only" role="status" aria-live="polite" *ngIf="cargando()">Cargando usuarios…</span>

  <div class="table-wrap">
    <table class="table-clinico">
      <caption class="sr-only">Listado de usuarios</caption>
      <thead>
        <tr>
          <th scope="col">Nombre</th>
          <th scope="col">Correo</th>
          <th scope="col">Rol</th>
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
            <td></td>
          </tr>
        </ng-container>

        <tr class="table-clinico__empty-row" *ngIf="!cargando() && !error() && usuarios().length === 0">
          <td class="table-clinico__empty-cell" colspan="5">
            <p class="table-clinico__empty-title">Sin usuarios registrados</p>
            <p class="table-clinico__empty-text">Crea el primer usuario para empezar a dar acceso a BIOPET.</p>
            <button type="button" class="btn btn--primary" (click)="abrirCrear()">
              <app-icon name="anadir"></app-icon>
              Nuevo usuario
            </button>
          </td>
        </tr>

        <ng-container *ngIf="!cargando()">
          <tr *ngFor="let u of usuarios()">
            <td data-label="Nombre">{{ u.nombre }}</td>
            <td data-label="Correo">{{ u.email }}</td>
            <td data-label="Rol">{{ humanizarRol(u.rol) }}</td>
            <td data-label="Estado">
              <span class="chip chip--success">{{ u.activo ? 'Activo' : 'Inactivo' }}</span>
            </td>
            <td data-label="" class="actions">
              <button
                type="button"
                class="btn-icon"
                (click)="abrirEditar(u)"
                [attr.aria-label]="'Editar usuario ' + u.nombre">
                <app-icon name="editar"></app-icon>
              </button>
              <button
                type="button"
                class="btn-icon btn-icon--danger"
                *ngIf="!esUnoMismo(u)"
                (click)="pedirConfirmacionBaja(u)"
                [attr.aria-label]="'Dar de baja a ' + u.nombre">
                <app-icon name="eliminar"></app-icon>
              </button>
            </td>
          </tr>
        </ng-container>
      </tbody>
    </table>
  </div>

  <!-- ===== Paginación ===== -->
  <nav class="pagination" aria-label="Paginación de usuarios" *ngIf="!cargando() && totalPaginas() > 0">
    <button type="button" class="btn btn--secondary btn--sm" (click)="irAPagina(pagina() - 1)" [disabled]="pagina() === 0">
      Anterior
    </button>
    <span class="pagination__status" aria-live="polite">
      Página {{ pagina() + 1 }} de {{ totalPaginas() }} ({{ totalElementos() }} usuarios)
    </span>
    <button type="button" class="btn btn--secondary btn--sm" (click)="irAPagina(pagina() + 1)" [disabled]="pagina() + 1 >= totalPaginas()">
      Siguiente
    </button>
  </nav>
  `,
})
export class UsuariosComponent implements OnInit {
  readonly humanizarRol = humanizarRol;
  readonly roles = ROLES_DISPONIBLES;
  readonly filasSkeleton = Array.from({ length: FILAS_SKELETON });

  usuarios = signal<Usuario[]>([]);
  pagina = signal(0);
  totalPaginas = signal(0);
  totalElementos = signal(0);

  cargando = signal(false);
  error = signal('');
  mensajeExito = signal('');

  mostrarFormulario = signal(false);
  editando = signal<Usuario | null>(null);
  guardando = signal(false);
  mostrarPassword = signal(false);
  private erroresServidor: Record<string, string[]> | null = null;

  usuarioABajar = signal<Usuario | null>(null);
  dandoDeBaja = signal(false);

  form = this.fb.group({
    nombre: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    rol: ['ROLE_DUENO' as RolBiopet, [Validators.required]],
    password: ['', [Validators.minLength(8), Validators.maxLength(80)]],
  });

  constructor(
    private api: UsuarioApiService,
    private auth: AuthService,
    private problemDetail: ProblemDetailService,
    private fb: FormBuilder
  ) {}

  ngOnInit(): void {
    this.cargar();
  }

  esUnoMismo(u: Usuario): boolean {
    return this.auth.usuarioActual()?.id === u.id;
  }

  editandoASiMismo(): boolean {
    const actual = this.editando();
    return !!actual && this.esUnoMismo(actual);
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
    this.api.listar(this.pagina(), TAMANIO_PAGINA).subscribe({
      next: (res) => {
        this.usuarios.set(res.content ?? []);
        this.totalPaginas.set(res.totalPages ?? 0);
        this.totalElementos.set(res.totalElements ?? 0);
        this.cargando.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.usuarios.set([]);
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

  // ---------- Formulario crear/editar ----------

  abrirCrear(): void {
    this.editando.set(null);
    this.erroresServidor = null;
    this.mostrarPassword.set(false);
    this.form.reset({ nombre: '', email: '', rol: 'ROLE_DUENO', password: '' });
    this.form.get('password')?.addValidators(Validators.required);
    this.form.get('password')?.updateValueAndValidity();
    this.mostrarFormulario.set(true);
    this.enfocarPrimerCampo();
  }

  abrirEditar(u: Usuario): void {
    this.editando.set(u);
    this.erroresServidor = null;
    this.mostrarPassword.set(false);
    this.form.get('password')?.removeValidators(Validators.required);
    this.form.get('password')?.updateValueAndValidity();
    this.form.reset({ nombre: u.nombre, email: u.email, rol: u.rol, password: '' });
    if (this.esUnoMismo(u)) {
      this.form.get('rol')?.disable();
    } else {
      this.form.get('rol')?.enable();
    }
    this.mostrarFormulario.set(true);
    this.enfocarPrimerCampo();
  }

  cerrarFormulario(): void {
    this.mostrarFormulario.set(false);
    this.editando.set(null);
    this.erroresServidor = null;
    this.form.get('rol')?.enable();
  }

  private enfocarPrimerCampo(): void {
    queueMicrotask(() => document.getElementById('f-nombre')?.focus());
  }

  private enfocarPrimerCampoInvalido(): void {
    const orden = ['nombre', 'email', 'rol', 'password'];
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
    const payload: UsuarioRequestPayload = {
      nombre: v.nombre as string,
      email: v.email as string,
      rol: v.rol as RolBiopet,
      password: v.password || null,
    };

    this.guardando.set(true);
    const actual = this.editando();
    const peticion = actual ? this.api.actualizar(actual.id, payload) : this.api.crear(payload);

    peticion.subscribe({
      next: () => {
        this.guardando.set(false);
        this.mostrarExito(actual ? 'Usuario actualizado correctamente.' : 'Usuario creado correctamente.');
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
    if (control?.hasError('email')) return 'Escribe un correo electrónico válido.';
    if (control?.hasError('minlength')) {
      const min = control.getError('minlength')?.requiredLength;
      return `Mínimo ${min} caracteres.`;
    }
    if (control?.hasError('maxlength')) {
      const max = control.getError('maxlength')?.requiredLength;
      return `Máximo ${max} caracteres.`;
    }
    return 'Valor inválido.';
  }

  // ---------- Dar de baja ----------

  pedirConfirmacionBaja(u: Usuario): void {
    this.error.set('');
    this.usuarioABajar.set(u);
  }

  cancelarBaja(): void {
    this.usuarioABajar.set(null);
  }

  confirmarBaja(): void {
    const u = this.usuarioABajar();
    if (!u) return;

    this.dandoDeBaja.set(true);
    this.api.eliminar(u.id).subscribe({
      next: () => {
        this.dandoDeBaja.set(false);
        this.usuarioABajar.set(null);
        this.mostrarExito(`La cuenta de ${u.nombre} fue dada de baja.`);
        if (this.usuarios().length === 1 && this.pagina() > 0) {
          this.pagina.set(this.pagina() - 1);
        }
        this.cargar();
      },
      error: (err: HttpErrorResponse) => {
        this.dandoDeBaja.set(false);
        this.usuarioABajar.set(null);
        this.error.set(this.problemDetail.mensaje(err));
      },
    });
  }
}
