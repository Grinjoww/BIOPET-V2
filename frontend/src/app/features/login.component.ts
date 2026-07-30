import { Component } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { ProblemDetailService } from '../core/problem-detail.service';

// NOTA DE ACCESIBILIDAD (pendiente, fuera de alcance de este commit):
// falta asociar <label for> a cada input, foco visible y aria-describedby
// para el mensaje de error. Se deja anotado para un commit posterior
// fix(accessibility): ... conforme al punto 5.4 del plan de Zaida.
@Component({
  standalone: true,
  imports: [FormsModule],
  template: `
  <div class="container">
    <h2>Iniciar sesión</h2>
    <input [(ngModel)]="email" placeholder="Email" />
    <input [(ngModel)]="password" placeholder="Contraseña" type="password" />
    <button (click)="login()" [disabled]="cargando">Entrar</button>
    <p class="error" *ngIf="error">{{ error }}</p>
  </div>`
})
export class LoginComponent {
  email = 'jaime@biopet.com';
  password = 'ClaveSegura123*';
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