import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PageResponse } from './mascota-api.service';
import { RolBiopet } from '../core/roles';

/** Espejo de UsuarioResponse del backend (CRUD administrativo). */
export interface Usuario {
  id: number;
  nombre: string;
  email: string;
  rol: RolBiopet;
  activo: boolean;
}

/**
 * Espejo de UsuarioRequest. `password` es obligatorio al crear (validado
 * por el backend, no por una anotación) y opcional al actualizar: vacío/
 * null en PUT conserva la contraseña actual (UsuarioService.actualizar).
 */
export interface UsuarioRequestPayload {
  nombre: string;
  email: string;
  password: string | null;
  rol: RolBiopet;
}

/** Estado de cuenta filtrable en GET /api/usuarios — reflejan el @RequestParam "estado" de UsuarioController. */
export type EstadoUsuarioFiltro = 'activo' | 'inactivo' | 'todos';

/** Filtros opcionales de GET /api/usuarios. */
export interface FiltrosUsuarios {
  q?: string; // nombre o email
  rol?: RolBiopet;
  estado?: EstadoUsuarioFiltro; // por defecto "activo" si se omite (mismo comportamiento de siempre)
}

/**
 * Encapsula el CRUD administrativo real de /api/usuarios (ADMIN
 * únicamente — @PreAuthorize("hasRole('ADMIN')") en los 4 métodos).
 * Deliberadamente distinto de UsuarioSeleccionableApiService: aquel
 * cubre /duenios y /veterinarios (selectores de solo lectura, audiencia
 * ADMIN/VETERINARIO/AUXILIAR, sin password/activo); este es el CRUD
 * completo, con password y activo, solo para ADMIN. No deben mezclarse.
 */
@Injectable({ providedIn: 'root' })
export class UsuarioApiService {
  private readonly base = '/api/usuarios';

  constructor(private http: HttpClient) {}

  listar(page: number, size: number, filtros: FiltrosUsuarios = {}, sort = 'nombre,asc'): Observable<PageResponse<Usuario>> {
    let params = new HttpParams().set('page', page).set('size', size).set('sort', sort);
    if (filtros.q) params = params.set('q', filtros.q);
    if (filtros.rol) params = params.set('rol', filtros.rol);
    if (filtros.estado) params = params.set('estado', filtros.estado);
    return this.http.get<PageResponse<Usuario>>(this.base, { params });
  }

  crear(payload: UsuarioRequestPayload): Observable<Usuario> {
    return this.http.post<Usuario>(this.base, payload);
  }

  actualizar(id: number, payload: UsuarioRequestPayload): Observable<Usuario> {
    return this.http.put<Usuario>(`${this.base}/${id}`, payload);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
