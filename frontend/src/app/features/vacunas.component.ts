import { Component, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../core/auth.service';
import { ProblemDetailService } from '../core/problem-detail.service';
import { Vacuna, VacunaApiService, VacunaRequestPayload } from './vacuna-api.service';

const TAMANIO_PAGINA = 10;

/**
 * IMPORTANTE: este componente NO decide qué vacunas puede ver cada rol.
 * El backend (VacunaService.listar) ya filtra por dueño cuando el rol es
 * ROLE_DUENO (solo vacunas de sus propias mascotas) y devuelve el listado
 * completo para ADMIN/VETERINARIO/AUXILIAR. Aquí solo consumimos tal cual
 * lo que llega en `content`.
 *
 * Lo único que decide este componente es la VISIBILIDAD de los botones de
 * acción (crear/editar/eliminar): mejora de UX, no control de seguridad.
 * El backend sigue rechazando con 403 vía @PreAuthorize aunque el DOM sea
 * manipulado.
 *
 * TODO (integración pendiente, bloque de Fred): cuando el endpoint de API
 * externa + Redis esté disponible, la sección "Datos externos" de abajo
 * debe consumir ese endpoint (ExternalApiController) y mostrarse aquí,
 * para cumplir el requisito de la guía de que la API externa se vea
 * realmente en la interfaz. De momento queda como marcador de posición.
 */
@Component({
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
  <div class="container">
    <h2>Vacunas registradas</h2>

    <div class="toolbar" role="toolbar" aria-label="Acciones de vacunas">
      <button type="button" (click)="cargar()" [disabled]="cargando()">
        Actualizar
      </button>
      <button type="button" *ngIf="puedeGestionar" (click)="abrirCrear()">
        + Nueva vacuna
      </button>
    </div>

    <p class="alerta alerta-error" role="alert" aria-live="assertive" *ngIf="error()">
      <strong>Error:</strong> {{ error() }}
    </p>
    <p class="alerta alerta-exito" role="status" aria-live="polite" *ngIf="mensajeExito()">
      <strong>Listo:</strong> {{ mensajeExito() }}
    </p>

    <!-- ===== Formulario crear/editar ===== -->
    <section *ngIf="mostrarFormulario()" class="card" aria-labelledby="form-titulo">
      <h3 id="form-titulo">{{ editando() ? 'Editar vacuna' : 'Nueva vacuna' }}</h3>

      <form [formGroup]="form" (ngSubmit)="guardar()" novalidate>
        <div class="campo">
          <label for="f-mascotaId">Id de la mascota</label>
          <input
            id="f-mascotaId"
            type="number"
            formControlName="mascotaId"
            [attr.aria-invalid]="tieneError('mascotaId')"
            [attr.aria-describedby]="tieneError('mascotaId') ? 'err-mascotaId' : null" />
          <p class="campo-error" id="err-mascotaId" *ngIf="tieneError('mascotaId')">
            {{ mensajeError('mascotaId') }}
          </p>
        </div>

        <div class="campo">
          <label for="f-veterinarioId">Id del veterinario (opcional)</label>
          <input id="f-veterinarioId" type="number" formControlName="veterinarioId" />
        </div>

        <div class="campo">
          <label for="f-tipo">Tipo de vacuna</label>
          <input
            id="f-tipo"
            type="text"
            formControlName="tipo"
            [attr.aria-invalid]="tieneError('tipo')"
            [attr.aria-describedby]="tieneError('tipo') ? 'err-tipo' : null" />
          <p class="campo-error" id="err-tipo" *ngIf="tieneError('tipo')">
            {{ mensajeError('tipo') }}
          </p>
        </div>

        <div class="campo">
          <label for="f-fechaAplicacion">Fecha de aplicación</label>
          <input
            id="f-fechaAplicacion"
            type="date"
            formControlName="fechaAplicacion"
            [attr.aria-invalid]="tieneError('fechaAplicacion')"
            [attr.aria-describedby]="tieneError('fechaAplicacion') ? 'err-fechaAplicacion' : null" />
          <p class="campo-error" id="err-fechaAplicacion" *ngIf="tieneError('fechaAplicacion')">
            {{ mensajeError('fechaAplicacion') }}
          </p>
        </div>

        <div class="campo">
          <label for="f-proximaFecha">Próxima dosis (opcional)</label>
          <input id="f-proximaFecha" type="date" formControlName="proximaFecha" />
        </div>

        <div class="campo">
          <label for="f-observaciones">Observaciones (opcional)</label>
          <input id="f-observaciones" type="text" formControlName="observaciones" />
        </div>

        <div class="acciones-form">
          <button type="submit" [disabled]="guardando()">
            {{ guardando() ? 'Guardando…' : (editando() ? 'Guardar cambios' : 'Crear vacuna') }}
          </button>
          <button type="button" class="secundario" (click)="cerrarFormulario()" [disabled]="guardando()">
            Cancelar
          </button>
        </div>
      </form>
    </section>

    <!-- ===== Confirmación de borrado ===== -->
    <div class="modal-fondo" *ngIf="vacunaAEliminar() as v" (keydown.escape)="cancelarEliminar()">
      <div class="modal" role="alertdialog" aria-modal="true" aria-labelledby="confirm-titulo" aria-describedby="confirm-texto">
        <h3 id="confirm-titulo">Confirmar eliminación</h3>
        <p id="confirm-texto">
          ¿Eliminar la vacuna <strong>{{ v.tipo }}</strong> de {{ v.mascotaNombre }}? Esta acción no se puede deshacer.
        </p>
        <div class="acciones-form">
          <button type="button" (click)="confirmarEliminar()" [disabled]="eliminando()">
            {{ eliminando() ? 'Eliminando…' : 'Sí, eliminar' }}
          </button>
          <button type="button" class="secundario" (click)="cancelarEliminar()" [disabled]="eliminando()">
            Cancelar
          </button>
        </div>
      </div>
    </div>

    <!-- ===== Listado ===== -->
    <p *ngIf="cargando()">Cargando vacunas…</p>
    <p *ngIf="!cargando() && !error() && vacunas().length === 0">No hay vacunas registradas.</p>

    <div class="grid-mascotas" *ngIf="!cargando()">
      <article class="card" *ngFor="let v of vacunas()">
        <h4>{{ v.tipo }}</h4>
        <p>Mascota: {{ v.mascotaNombre }}</p>
        <p>Aplicada: {{ v.fechaAplicacion }}</p>
        <p *ngIf="v.proximaFecha">Próxima dosis: {{ v.proximaFecha }}</p>
        <p *ngIf="v.veterinarioNombre">Veterinario: {{ v.veterinarioNombre }}</p>
        <p *ngIf="v.observaciones">{{ v.observaciones }}</p>

        <div class="acciones-card" *ngIf="puedeGestionar">
          <button type="button" (click)="abrirEditar(v)">Editar</button>
          <button type="button" class="peligro" (click)="pedirConfirmacionEliminar(v)">Eliminar</button>
        </div>
      </article>
    </div>

    <!-- ===== Paginación ===== -->
    <nav class="paginacion" aria-label="Paginación de vacunas" *ngIf="!cargando() && totalPaginas() > 0">
      <button type="button" (click)="irAPagina(pagina() - 1)" [disabled]="pagina() === 0">Anterior</button>
      <span aria-live="polite">
        Página {{ pagina() + 1 }} de {{ totalPaginas() }} ({{ totalElementos() }} vacunas)
      </span>
      <button type="button" (click)="irAPagina(pagina() + 1)" [disabled]="pagina() + 1 >= totalPaginas()">Siguiente</button>
    </nav>

    <!-- ===== Datos externos (placeholder, pendiente del endpoint de Fred) ===== -->
    <section class="card" aria-labelledby="externo-titulo">
      <h3 id="externo-titulo">Datos externos</h3>
      <p>Pendiente de conectar con el endpoint de API externa (Redis/cache) de Fred.</p>
    </section>
  </div>`
})
export class VacunasComponent implements OnInit {
  vacunas = signal<Vacuna[]>([]);
  pagina = signal(0);
  totalPaginas = signal(0);
  totalElementos = signal(0);

  cargando = signal(false);
  error = signal('');
  mensajeExito = signal('');

  mostrarFormulario = signal(false);
  editando = signal<Vacuna | null>(null);
  guardando = signal(false);
  private erroresServidor: Record<string, string[]> | null = null;

  vacunaAEliminar = signal<Vacuna | null>(null);
  eliminando = signal(false);

  form = this.fb.group({
    mascotaId: [null as number | null, [Validators.required, Validators.min(1)]],
    veterinarioId: [null as number | null],
    tipo: ['', [Validators.required, Validators.maxLength(60)]],
    fechaAplicacion: ['', [Validators.required]],
    proximaFecha: [''],
    observaciones: ['', [Validators.maxLength(255)]]
  });

  constructor(
    private api: VacunaApiService,
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
   * 'AUXILIAR')") en POST/PUT/DELETE). Esto solo oculta los botones; la
   * regla real vive en el backend.
   */
  get puedeGestionar(): boolean {
    const rol = this.auth.usuarioActual()?.rol;
    return rol === 'ROLE_ADMIN' || rol === 'ROLE_VETERINARIO' || rol === 'ROLE_AUXILIAR';
  }

  // ---------- Listado / paginación ----------

  cargar(): void {
    this.error.set('');
    this.cargando.set(true);
    this.api.listar(this.pagina(), TAMANIO_PAGINA).subscribe({
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
      }
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
    this.form.reset({ mascotaId: null, veterinarioId: null, tipo: '', fechaAplicacion: '', proximaFecha: '', observaciones: '' });
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
      observaciones: v.observaciones ?? ''
    });
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
    const orden = ['mascotaId', 'tipo', 'fechaAplicacion'];
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
      observaciones: v.observaciones || null
    };

    this.guardando.set(true);
    const actual = this.editando();
    const peticion = actual ? this.api.actualizar(actual.id, payload) : this.api.crear(payload);

    peticion.subscribe({
      next: () => {
        this.guardando.set(false);
        this.mensajeExito.set(actual ? 'Vacuna actualizada correctamente.' : 'Vacuna creada correctamente.');
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
        this.mensajeExito.set(`Vacuna "${v.tipo}" fue eliminada.`);
        if (this.vacunas().length === 1 && this.pagina() > 0) {
          this.pagina.set(this.pagina() - 1);
        }
        this.cargar();
      },
      error: (err: HttpErrorResponse) => {
        this.eliminando.set(false);
        this.vacunaAEliminar.set(null);
        this.error.set(this.problemDetail.mensaje(err));
      }
    });
  }
}
