import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import { AuthService } from '../core/auth.service';
import { ProblemDetailService } from '../core/problem-detail.service';
import { rutaInicioParaRol } from '../core/roles';
import { MonogramComponent } from '../shared/icons/monogram.component';

/**
 * Login «Carné Clínico»: composición asimétrica 38/62 (marca/acceso) en
 * desktop, marca compacta sobre el formulario en móvil. Sin foto de
 * stock, sin hero, sin degradado — la única superficie de color es la
 * marca sólida a la izquierda, con un reglado geométrico muy sutil (ver
 * .auth-shell__brand en styles.css).
 */
@Component({
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, MonogramComponent],
  template: `
    <div class="auth-shell">
      <div class="auth-shell__brand">
        <div class="auth-shell__brand-mark">
          <app-monogram [size]="40"></app-monogram>
          <span class="auth-shell__wordmark">BIOPET</span>
        </div>
        <span class="registration-rule" aria-hidden="true"></span>
        <p class="auth-shell__tagline">Gestión clínica veterinaria.</p>
        <p class="auth-shell__tagline auth-shell__tagline--secondary">
          Expedientes, citas y control de vacunación en un solo lugar.
        </p>
      </div>

      <div class="auth-shell__panel">
        <form class="auth-form" [formGroup]="form" (ngSubmit)="ingresar()" novalidate>
          <span class="eyebrow auth-form__eyebrow">Acceso</span>
          <span class="registration-rule" aria-hidden="true"></span>
          <h1>Entrar</h1>
          <p class="auth-form__lead">Ingresa con tu cuenta de BIOPET.</p>

          <p class="alert alert--danger" role="alert" aria-live="assertive" *ngIf="error()">
            <strong>Error:</strong> {{ error() }}
          </p>

          <div class="field">
            <label for="email">Correo electrónico</label>
            <input
              id="email"
              type="email"
              formControlName="email"
              autocomplete="email"
              autofocus
              [attr.aria-invalid]="tieneError('email')"
              [attr.aria-describedby]="tieneError('email') ? 'err-email' : null"
            />
            <p class="field-error" id="err-email" *ngIf="tieneError('email')">
              {{ mensajeErrorCampo('email') }}
            </p>
          </div>

          <div class="field">
            <label for="password">Contraseña</label>
            <input
              id="password"
              type="password"
              formControlName="password"
              autocomplete="current-password"
              [attr.aria-invalid]="tieneError('password')"
              [attr.aria-describedby]="tieneError('password') ? 'err-password' : null"
            />
            <p class="field-error" id="err-password" *ngIf="tieneError('password')">
              {{ mensajeErrorCampo('password') }}
            </p>
          </div>

          <button type="submit" class="btn btn--primary" [disabled]="cargando()">
            {{ cargando() ? 'Ingresando…' : 'Entrar' }}
          </button>
        </form>
      </div>
    </div>
  `,
})
export class LoginComponent {
  error = signal('');
  cargando = signal(false);

  form = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  constructor(
    private auth: AuthService,
    private router: Router,
    private problemDetail: ProblemDetailService,
    private fb: FormBuilder
  ) {}

  ingresar(): void {
    this.error.set('');

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const { email, password } = this.form.getRawValue();
    this.cargando.set(true);

    this.auth.login(email as string, password as string).subscribe({
      next: () => {
        this.cargando.set(false);
        // AuthService.login() ya encadena (no dispara en paralelo) la
        // carga del perfil antes de emitir, así que usuarioActual() aquí
        // ya refleja el usuario recién autenticado. Fallback defensivo a
        // /mascotas por si, pese a un login exitoso, el perfil no llegó a
        // resolverse (no debería ocurrir en la práctica).
        const rol = this.auth.usuarioActual()?.rol;
        this.router.navigateByUrl(rol ? rutaInicioParaRol(rol) : '/mascotas');
      },
      error: (err: HttpErrorResponse) => {
        this.cargando.set(false);
        this.error.set(this.problemDetail.mensaje(err));
      },
    });
  }

  tieneError(campo: string): boolean {
    const control = this.form.get(campo);
    return !!control && control.invalid && (control.touched || control.dirty);
  }

  mensajeErrorCampo(campo: string): string {
    const control = this.form.get(campo);
    if (control?.hasError('required')) return 'Este campo es obligatorio.';
    if (control?.hasError('email')) return 'Ingresa un correo electrónico válido.';
    return 'Valor inválido.';
  }
}
