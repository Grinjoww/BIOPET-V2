import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

import { AuthService } from '../core/auth.service';
import { ProblemDetailService } from '../core/problem-detail.service';

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="container">
      <h2>Iniciar sesión</h2>

      <label for="email">Correo electrónico</label>
      <input
        id="email"
        name="email"
        type="email"
        [(ngModel)]="email"
        autocomplete="email"
      />

      <label for="password">Contraseña</label>
      <input
        id="password"
        name="password"
        type="password"
        [(ngModel)]="password"
        autocomplete="current-password"
        [attr.aria-describedby]="error ? 'login-error' : null"
      />

      <button type="button" (click)="login()" [disabled]="cargando">
        {{ cargando ? 'Ingresando...' : 'Entrar' }}
      </button>

      <p
        id="login-error"
        class="error"
        role="alert"
        aria-live="polite"
        *ngIf="error"
      >
        {{ error }}
      </p>
    </div>
  `
})
export class LoginComponent {
  email = '';
  password = '';
  error = '';
  cargando = false;

  constructor(
    private auth: AuthService,
    private router: Router,
    private problemDetail: ProblemDetailService
  ) {}

  login(): void {
    this.error = '';
    this.cargando = true;

    this.auth.login(this.email, this.password).subscribe({
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
}