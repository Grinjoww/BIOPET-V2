import { Component, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../core/auth.service';
import { ProblemDetailService } from '../core/problem-detail.service';
import { Mascota, MascotaApiService, MascotaRequestPayload, ResumenEspecie } from './mascota-api.service';

const TAMANIO_PAGINA = 10;

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
  imports: [CommonModule, ReactiveFormsModule],
  template: `
  <div class="container">
    <h2>Mascotas registradas</h2>

    <div class="toolbar" role="toolbar" aria-label="Acciones de mascotas">
      <button type="button" (click)="cargar()" [disabled]="cargando()">
        Actualizar
      </button>
      <button type="button" (click)="alternarResumen()" [attr.aria-expanded]="mostrarResumen()">
        {{ mostrarResumen() ? 'Ocultar resumen por especie' : 'Ver resumen por especie' }}
      </button>
      <button type="button" *ngIf="puedeGestionar" (click)="abrirCrear()">
        + Nueva mascota
      </button>
    </div>

    <!-- Mensajes de estado: aria-live para que lectores de pantalla los anuncien,
         y siempre con texto/ícono además de color (nunca solo color). -->
    <p class="alerta alerta-error" role="alert" aria-live="assertive" *ngIf="error()">
      <strong>Error:</strong> {{ error() }}
    </p>
    <p class="alerta alerta-exito" role="status" aria-live="polite" *ngIf="mensajeExito()">
      <strong>Listo:</strong> {{ mensajeExito() }}
    </p>

    <!-- ===== Resumen por especie ===== -->
    <section *ngIf="mostrarResumen()" aria-labelledby="resumen-titulo" class="card resumen">
      <h3 id="resumen-titulo">Resumen por especie</h3>
      <p *ngIf="cargandoResumen()">Cargando resumen…</p>
      <p *ngIf="!cargandoResumen() && resumen().length === 0">No hay datos para mostrar.</p>
      <table *ngIf="!cargandoResumen() && resumen().length > 0">
        <caption class="sr-only">Cantidad de mascotas activas por especie</caption>
        <thead>
          <tr><th scope="col">Especie</th><th scope="col">Total</th></tr>
        </thead>
        <tbody>
          <tr *ngFor="let r of resumen()">
            <td>{{ r.especie }}</td>
            <td>{{ r.total }}</td>
          </tr>
        </tbody>
      </table>
    </section>

    <!-- ===== Formulario crear/editar ===== -->
    <section *ngIf="mostrarFormulario()" class="card" aria-labelledby="form-titulo">
      <h3 id="form-titulo">{{ editando() ? 'Editar mascota' : 'Nueva mascota' }}</h3>

      <form [formGroup]="form" (ngSubmit)="guardar()" novalidate>
        <div class="campo">
          <label for="f-duenioId">Id del dueño</label>
          <input
            id="f-duenioId"
            type="number"
            formControlName="duenioId"
            [attr.aria-invalid]="tieneError('duenioId')"
            [attr.aria-describedby]="tieneError('duenioId') ? 'err-duenioId' : null" />
          <p class="campo-error" id="err-duenioId" *ngIf="tieneError('duenioId')">
            {{ mensajeError('duenioId') }}
          </p>
        </div>

        <div class="campo">
          <label for="f-nombre">Nombre</label>
          <input
            id="f-nombre"
            type="text"
            formControlName="nombre"
            [attr.aria-invalid]="tieneError('nombre')"
            [attr.aria-describedby]="tieneError('nombre') ? 'err-nombre' : null" />
          <p class="campo-error" id="err-nombre" *ngIf="tieneError('nombre')">
            {{ mensajeError('nombre') }}
          </p>
        </div>

        <div class="campo">
          <label for="f-especie">Especie</label>
          <input
            id="f-especie"
            type="text"
            formControlName="especie"
            [attr.aria-invalid]="tieneError('especie')"
            [attr.aria-describedby]="tieneError('especie') ? 'err-especie' : null" />
          <p class="campo-error" id="err-especie" *ngIf="tieneError('especie')">
            {{ mensajeError('especie') }}
          </p>
        </div>

        <div class="campo">
          <label for="f-raza">Raza</label>
          <input
            id="f-raza"
            type="text"
            formControlName="raza"
            [attr.aria-invalid]="tieneError('raza')"
            [attr.aria-describedby]="tieneError('raza') ? 'err-raza' : null" />
          <p class="campo-error" id="err-raza" *ngIf="tieneError('raza')">
            {{ mensajeError('raza') }}
          </p>
        </div>

        <div class="campo">
          <label for="f-fecha">Fecha de nacimiento</label>
          <input
            id="f-fecha"
            type="date"
            formControlName="fechaNacimiento"
            [attr.aria-invalid]="tieneError('fechaNacimiento')"
            [attr.aria-describedby]="tieneError('fechaNacimiento') ? 'err-fecha' : null" />
          <p class="campo-error" id="err-fecha" *ngIf="tieneError('fechaNacimiento')">
            {{ mensajeError('fechaNacimiento') }}
          </p>
        </div>

        <div class="acciones-form">
          <button type="submit" [disabled]="guardando()">
            {{ guardando() ? 'Guardando…' : (editando() ? 'Guardar cambios' : 'Crear mascota') }}
          </button>
          <button type="button" class="secundario" (click)="cerrarFormulario()" [disabled]="guardando()">
            Cancelar
          </button>
        </div>
      </form>
    </section>

    <!-- ===== Confirmación de borrado ===== -->
    <div
      class="modal-fondo"
      *ngIf="mascotaAEliminar() as m"
      (keydown.escape)="cancelarEliminar()">
      <div
        class="modal"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="confirm-titulo"
        aria-describedby="confirm-texto">
        <h3 id="confirm-titulo">Confirmar eliminación</h3>
        <p id="confirm-texto">
          ¿Eliminar a <strong>{{ m.nombre }}</strong> ({{ m.especie }})? Esta acción no se puede deshacer.
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
    <p *ngIf="cargando()">Cargando mascotas…</p>

    <p *ngIf="!cargando() && !error() && mascotas().length === 0">
      No hay mascotas registradas.
    </p>

    <div class="grid-mascotas" *ngIf="!cargando()">
      <article class="card" *ngFor="let m of mascotas()">
        <h4>{{ m.nombre }}</h4>
        <p>{{ m.especie }} · {{ m.raza }}</p>
        <p>Dueño: {{ m.duenioNombre }}</p>
        <p>Nace: {{ m.fechaNacimiento }}</p>

        <div class="acciones-card" *ngIf="puedeGestionar">
          <button type="button" (click)="abrirEditar(m)">Editar</button>
          <button type="button" class="peligro" (click)="pedirConfirmacionEliminar(m)">Eliminar</button>
        </div>
      </article>
    </div>

    <!-- ===== Paginación ===== -->
    <nav class="paginacion" aria-label="Paginación de mascotas" *ngIf="!cargando() && totalPaginas() > 0">
      <button type="button" (click)="irAPagina(pagina() - 1)" [disabled]="pagina() === 0">
        Anterior
      </button>
      <span aria-live="polite">
        Página {{ pagina() + 1 }} de {{ totalPaginas() }} ({{ totalElementos() }} mascotas)
      </span>
      <button type="button" (click)="irAPagina(pagina() + 1)" [disabled]="pagina() + 1 >= totalPaginas()">
        Siguiente
      </button>
    </nav>
  </div>`
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
        this.mensajeExito.set(actual ? 'Mascota actualizada correctamente.' : 'Mascota creada correctamente.');
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
        this.mensajeExito.set(`"${m.nombre}" fue eliminada.`);
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