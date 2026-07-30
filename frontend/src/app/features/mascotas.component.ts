import { Component, OnInit } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { AuthService } from '../core/auth.service';
import { ProblemDetailService } from '../core/problem-detail.service';

interface Mascota {
  id: number;
  duenioId: number;
  duenioNombre: string;
  nombre: string;
  especie: string;
  raza: string;
}

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
  imports: [CommonModule],
  template: `
  <div class="container">
    <h2>Mascotas registradas</h2>

    <button (click)="cargar()">Actualizar</button>
    <button *ngIf="puedeGestionar" (click)="crearDemo()">+ Nueva mascota</button>

    <p class="error" *ngIf="error">{{ error }}</p>

    <div class="card" *ngFor="let m of mascotas">
      <strong>{{ m.nombre }}</strong><br>
      {{ m.especie }} · {{ m.raza }} · Dueño: {{ m.duenioNombre }}

      <ng-container *ngIf="puedeGestionar">
        <button (click)="editarDemo(m)">Editar</button>
        <button (click)="eliminar(m)">Eliminar</button>
      </ng-container>
    </div>

    <p *ngIf="!error && mascotas.length === 0">No hay mascotas registradas.</p>
  </div>`
})
export class MascotasComponent implements OnInit {
  mascotas: Mascota[] = [];
  error = '';

  constructor(
    private http: HttpClient,
    private auth: AuthService,
    private problemDetail: ProblemDetailService
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

  cargar(): void {
    this.error = '';
    this.http.get<{ content: Mascota[] }>('/api/mascotas?page=0&size=10&sort=id,asc').subscribe({
      next: (res) => (this.mascotas = res.content ?? []),
      error: (err: HttpErrorResponse) => {
        this.mascotas = [];
        this.error = this.problemDetail.mensaje(err);
      }
    });
  }

  eliminar(mascota: Mascota): void {
    this.error = '';
    this.http.delete<void>(`/api/mascotas/${mascota.id}`).subscribe({
      next: () => this.cargar(),
      error: (err: HttpErrorResponse) => {
        // Incluye el caso 403 (por ejemplo, si el rol cambió en otra pestaña
        // o el usuario manipuló el DOM para reaparecer el botón) y el caso
        // 404 (la mascota ya fue eliminada por otro usuario).
        this.error = this.problemDetail.mensaje(err);
      }
    });
  }

  // Placeholders de navegación a formularios de creación/edición.
  // La implementación completa de esas pantallas no es parte de este PR
  // (§5.4 se limita al flujo de autenticación); se deja el enganche listo
  // para cuando se construyan esos formularios.
  crearDemo(): void {
    this.error = 'Formulario de creación pendiente de implementar.';
  }

  editarDemo(_mascota: Mascota): void {
    this.error = 'Formulario de edición pendiente de implementar.';
  }
}