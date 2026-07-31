import { Component } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { ProblemDetailService } from '../core/problem-detail.service';

@Component({
  standalone: true,
  imports: [ReactiveFormsModule],
  template: `
  <div class="container">
    <h1>Iniciar sesión</h1>

    <!-- Un <form> real con (ngSubmit) permite enviar con Enter desde
         cualquier campo, sin necesidad de manejar keydown manualmente. -->
    <form [formGroup]="form" (ngSubmit)="login()" novalidate>
      <div class="campo">
        <label for="f-email">Correo electrónico</label>
        <input
          id="f-email"
          type="email"
          formControlName="email"
          autocomplete="username"
          [attr.aria-invalid]="tieneError('email')"
          [attr.aria-describedby]="tieneError('email') ? 'err-email' : null" />
        <p class="campo-error" id="err-email" *ngIf="tieneError('email')">
          {{ mensajeErrorCampo('email') }}
        </p>
      </div>

      <div class="campo">
        <label for="f-password">Contraseña</label>
        <input
          id="f-password"
          type="password"
          formControlName="password"
          autocomplete="current-password"
          [attr.aria-invalid]="tieneError('password')"
          [attr.aria-describedby]="tieneError('password') ? 'err-password' : null" />
        <p class="campo-error" id="err-password" *ngIf="tieneError('password')">
          {{ mensajeErrorCampo('password') }}
        </p>
      </div>

      <button type="submit" [disabled]="cargando">
        {{ cargando ? 'Entrando…' : 'Entrar' }}
      </button>

      <!-- role="alert" + aria-live="assertive": un lector de pantalla anuncia
           el error sin que el usuario tenga que buscarlo; el color rojo del
           CSS es un refuerzo visual, no el único canal del mensaje. -->
      <p class="alerta alerta-error" role="alert" aria-live="assertive" *ngIf="error">
        <strong>Error:</strong> {{ error }}
      </p>
    </form>
  </div>`
})
export class LoginComponent {
  error = '';
  cargando = false;

  form = this.fb.group({
    email: ['jaime@biopet.com', [Validators.required, Validators.email]],
    password: ['ClaveSegura123*', [Validators.required]]
  });

  constructor(
    private auth: AuthService,
    private router: Router,
    private problemDetail: ProblemDetailService,
    private fb: FormBuilder
  ) {}

  login(): void {
    this.error = '';

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.enfocarPrimerCampoInvalido();
      return;
    }

    this.cargando = true;
    const { email, password } = this.form.getRawValue();

    this.auth.login(email as string, password as string).subscribe({
      next: () => {
        this.cargando = false;
        this.router.navigate(['/mascotas']);
      },
      error: (err: HttpErrorResponse) => {
        this.cargando = false;
        this.error = this.problemDetail.mensaje(err);
      }
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

  private enfocarPrimerCampoInvalido(): void {
    const orden = ['email', 'password'];
    const primerInvalido = orden.find((c) => this.tieneError(c));
    if (primerInvalido) {
      queueMicrotask(() => document.getElementById(`f-${primerInvalido}`)?.focus());
    }
  }
}