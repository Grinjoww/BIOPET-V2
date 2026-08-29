import { Component, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { AuthService } from '../core/auth.service';
import { ProblemDetailService } from '../core/problem-detail.service';
import { DatosFacturacion, DatosFacturacionApiService, DatosFacturacionRequestPayload } from './datos-facturacion-api.service';
import { etiquetaTipoIdentificacion } from './factura-presentacion';
import { IconComponent } from '../shared/icons/icon.component';

/**
 * Panel de "Datos de facturación" embebido en Perfil (solo DUENO — ver
 * PerfilComponent). SIEMPRE opera sobre `AuthService.usuarioActual()!.id`,
 * tomado de la sesión real, nunca de un id que este componente reciba desde
 * afuera: un DUENO jamás puede pasar el usuarioId de otra persona porque
 * este componente no expone ninguna forma de hacerlo. El backend vuelve a
 * comprobar el ownership de todas formas (DatosFacturacionService), pero
 * aquí ni siquiera hay una superficie para intentarlo.
 */
@Component({
  selector: 'app-datos-facturacion',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, IconComponent],
  template: `
  <p class="alert alert--danger" role="alert" aria-live="assertive" *ngIf="error()">
    <strong>Error:</strong> {{ error() }}
  </p>
  <p class="alert alert--success" role="status" aria-live="polite" *ngIf="mensajeExito()">
    <strong>Listo:</strong> {{ mensajeExito() }}
  </p>

  <span class="sr-only" role="status" aria-live="polite" *ngIf="cargando()">Cargando datos de facturación…</span>

  <div class="table-wrap" *ngIf="!cargando() && datos().length > 0">
    <table class="table-clinico">
      <caption class="sr-only">Tus datos de facturación</caption>
      <thead>
        <tr><th scope="col">Identificación</th><th scope="col">Razón social</th><th scope="col">Predeterminado</th><th scope="col"><span class="sr-only">Acciones</span></th></tr>
      </thead>
      <tbody>
        <tr *ngFor="let d of datos()">
          <td data-label="Identificación">{{ etiquetaTipoIdentificacion(d.tipoIdentificacion) }} · {{ d.identificacion }}</td>
          <td data-label="Razón social">{{ d.razonSocial }}</td>
          <td data-label="Predeterminado">
            <span class="chip chip--success" *ngIf="d.predeterminado">Predeterminado</span>
            <button type="button" class="btn btn--ghost btn--sm" *ngIf="!d.predeterminado" [disabled]="procesando() === d.id" (click)="marcarPredeterminado(d)">
              Marcar predeterminado
            </button>
          </td>
          <td data-label="" class="actions">
            <button type="button" class="btn-icon" (click)="abrirEditar(d)" [attr.aria-label]="'Editar datos de ' + d.razonSocial">
              <app-icon name="editar"></app-icon>
            </button>
            <button type="button" class="btn-icon btn-icon--danger" [disabled]="procesando() === d.id" (click)="desactivar(d)" [attr.aria-label]="'Desactivar datos de ' + d.razonSocial">
              <app-icon name="eliminar"></app-icon>
            </button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>

  <p class="field-hint" *ngIf="!cargando() && datos().length === 0 && !mostrarFormulario()">
    Todavía no tienes datos de facturación registrados. Regístralos para que la clínica pueda emitirte comprobantes.
  </p>

  <button type="button" class="btn btn--secondary btn--sm" style="margin-top: var(--space-4);" (click)="abrirCrear()" *ngIf="!mostrarFormulario()">
    <app-icon name="anadir" [size]="16"></app-icon>
    {{ datos().length === 0 ? 'Registrar datos de facturación' : 'Agregar otra identidad tributaria' }}
  </button>

  <form [formGroup]="form" (ngSubmit)="guardar()" novalidate *ngIf="mostrarFormulario()" style="margin-top: var(--space-4);">
    <div class="field">
      <label for="df-tipo">Tipo de identificación<span class="required-mark" aria-hidden="true">*</span></label>
      <select id="df-tipo" formControlName="tipoIdentificacion">
        <option value="CEDULA">Cédula</option>
        <option value="RUC">RUC</option>
        <option value="PASAPORTE">Pasaporte</option>
        <option value="CONSUMIDOR_FINAL">Consumidor final</option>
        <option value="IDENTIFICACION_EXTERIOR">Identificación del exterior</option>
      </select>
    </div>
    <div class="field">
      <label for="df-identificacion">Identificación<span class="required-mark" aria-hidden="true">*</span></label>
      <input id="df-identificacion" type="text" formControlName="identificacion" />
      <p class="field-error" *ngIf="tieneError('identificacion')">{{ mensajeError('identificacion') }}</p>
    </div>
    <div class="field">
      <label for="df-razonSocial">Razón social / Nombres<span class="required-mark" aria-hidden="true">*</span></label>
      <input id="df-razonSocial" type="text" formControlName="razonSocial" />
      <p class="field-error" *ngIf="tieneError('razonSocial')">{{ mensajeError('razonSocial') }}</p>
    </div>
    <div class="field">
      <label for="df-direccion">Dirección</label>
      <input id="df-direccion" type="text" formControlName="direccion" />
    </div>
    <div class="field">
      <label for="df-telefono">Teléfono</label>
      <input id="df-telefono" type="text" formControlName="telefono" />
    </div>
    <div class="field">
      <label for="df-email">Email de facturación</label>
      <input id="df-email" type="email" formControlName="emailFacturacion" />
      <p class="field-error" *ngIf="tieneError('emailFacturacion')">{{ mensajeError('emailFacturacion') }}</p>
    </div>
    <div class="modal-panel__actions">
      <button type="submit" class="btn btn--primary" [disabled]="guardando()">
        {{ guardando() ? 'Guardando…' : (editando() ? 'Guardar cambios' : 'Registrar') }}
      </button>
      <button type="button" class="btn btn--secondary" (click)="cerrarFormulario()" [disabled]="guardando()">Cancelar</button>
    </div>
  </form>
  `,
})
export class DatosFacturacionComponent implements OnInit {
  readonly etiquetaTipoIdentificacion = etiquetaTipoIdentificacion;

  datos = signal<DatosFacturacion[]>([]);
  cargando = signal(false);
  error = signal('');
  mensajeExito = signal('');

  mostrarFormulario = signal(false);
  editando = signal<DatosFacturacion | null>(null);
  guardando = signal(false);
  procesando = signal<number | null>(null);

  form = this.fb.group({
    tipoIdentificacion: ['CEDULA', [Validators.required]],
    identificacion: ['', [Validators.required, Validators.maxLength(20)]],
    razonSocial: ['', [Validators.required, Validators.maxLength(300)]],
    direccion: [''],
    telefono: [''],
    emailFacturacion: ['', [Validators.email]],
  });

  constructor(
    private auth: AuthService,
    private api: DatosFacturacionApiService,
    private problemDetail: ProblemDetailService,
    private fb: FormBuilder
  ) {}

  ngOnInit(): void {
    this.cargar();
  }

  /** Siempre el propio id de sesión: nunca un parámetro externo. */
  private get usuarioId(): number {
    return this.auth.usuarioActual()!.id;
  }

  cargar(): void {
    this.error.set('');
    this.cargando.set(true);
    this.api.listar(this.usuarioId).subscribe({
      next: (res) => {
        this.datos.set(res ?? []);
        this.cargando.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.cargando.set(false);
        this.error.set(this.problemDetail.mensaje(err));
      },
    });
  }

  abrirCrear(): void {
    this.editando.set(null);
    this.form.reset({ tipoIdentificacion: 'CEDULA', identificacion: '', razonSocial: '', direccion: '', telefono: '', emailFacturacion: '' });
    this.mostrarFormulario.set(true);
  }

  abrirEditar(d: DatosFacturacion): void {
    this.editando.set(d);
    this.form.reset(d);
    this.mostrarFormulario.set(true);
  }

  cerrarFormulario(): void {
    this.mostrarFormulario.set(false);
    this.editando.set(null);
  }

  guardar(): void {
    this.error.set('');
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const payload: DatosFacturacionRequestPayload = {
      tipoIdentificacion: v.tipoIdentificacion as DatosFacturacionRequestPayload['tipoIdentificacion'],
      identificacion: v.identificacion as string,
      razonSocial: v.razonSocial as string,
      direccion: v.direccion || null,
      telefono: v.telefono || null,
      emailFacturacion: v.emailFacturacion || null,
    };

    this.guardando.set(true);
    const actual = this.editando();
    const peticion = actual
      ? this.api.actualizar(this.usuarioId, actual.id, payload)
      : this.api.crear(this.usuarioId, payload);

    peticion.subscribe({
      next: () => {
        this.guardando.set(false);
        this.mostrarExito(actual ? 'Datos de facturación actualizados.' : 'Datos de facturación registrados.');
        this.cerrarFormulario();
        this.cargar();
      },
      error: (err: HttpErrorResponse) => {
        this.guardando.set(false);
        this.error.set(this.problemDetail.mensaje(err));
      },
    });
  }

  marcarPredeterminado(d: DatosFacturacion): void {
    this.error.set('');
    this.procesando.set(d.id);
    this.api.marcarPredeterminado(this.usuarioId, d.id).subscribe({
      next: () => {
        this.procesando.set(null);
        this.mostrarExito(`"${d.razonSocial}" ahora es tu predeterminado.`);
        this.cargar();
      },
      error: (err: HttpErrorResponse) => {
        this.procesando.set(null);
        this.error.set(this.problemDetail.mensaje(err));
      },
    });
  }

  /** Baja lógica: el backend nunca borra físicamente (puede ser snapshot de una factura ya emitida). */
  desactivar(d: DatosFacturacion): void {
    this.error.set('');
    this.procesando.set(d.id);
    this.api.desactivar(this.usuarioId, d.id).subscribe({
      next: () => {
        this.procesando.set(null);
        this.mostrarExito(`"${d.razonSocial}" fue desactivado.`);
        this.cargar();
      },
      error: (err: HttpErrorResponse) => {
        this.procesando.set(null);
        this.error.set(this.problemDetail.mensaje(err));
      },
    });
  }

  tieneError(campo: string): boolean {
    const control = this.form.get(campo);
    return !!control && control.invalid && (control.touched || control.dirty);
  }

  mensajeError(campo: string): string {
    const control = this.form.get(campo);
    if (control?.hasError('required')) return 'Este campo es obligatorio.';
    if (control?.hasError('email')) return 'Correo inválido.';
    if (control?.hasError('maxlength')) {
      const max = control.getError('maxlength')?.requiredLength;
      return `Máximo ${max} caracteres.`;
    }
    return 'Valor inválido.';
  }

  private mostrarExito(mensaje: string): void {
    this.mensajeExito.set(mensaje);
    setTimeout(() => {
      if (this.mensajeExito() === mensaje) this.mensajeExito.set('');
    }, 4000);
  }
}
