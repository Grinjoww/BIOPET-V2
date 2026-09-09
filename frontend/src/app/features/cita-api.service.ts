import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PageResponse } from './mascota-api.service';

/** Estados reales de com.biopet.entity.EstadoCita — no derivados, no inventados. */
export type EstadoCita = 'PROGRAMADA' | 'CANCELADA' | 'COMPLETADA';

/** Espejo de CitaResponse del backend. */
export interface Cita {
  id: number;
  mascotaId: number;
  mascotaNombre: string;
  veterinarioId: number;
  veterinarioNombre: string;
  fechaHora: string; // ISO Instant (UTC)
  estado: EstadoCita;
  motivo: string | null;
  activo: boolean;
  creadoEn: string;
  actualizadoEn: string;
}

/**
 * Espejo de CitaRequest del backend. `estado` solo tiene efecto real en
 * PUT (CitaService.actualizar lo aplica tal cual); en POST el backend lo
 * ignora y fuerza siempre PROGRAMADA (CitaService.crear), así que este
 * componente nunca debe ofrecer un selector de estado inicial al crear.
 */
export interface CitaRequestPayload {
  mascotaId: number;
  veterinarioId: number;
  fechaHora: string; // ISO Instant (UTC)
  estado: EstadoCita;
  motivo: string | null;
}

/** Filtros opcionales de GET /api/citas — reflejan los @RequestParam de CitaController. */
export interface FiltrosCitas {
  mascotaId?: number;
  veterinarioId?: number;
  estado?: EstadoCita;
  desde?: string; // ISO Instant (UTC)
  hasta?: string; // ISO Instant (UTC)
}

/**
 * Encapsula /api/citas completo: el listado general paginado y el CRUD
 * (Citas V2), además de GET /mascota/{id} que ya usaba la ficha de
 * mascota (solo lectura, sin cambios). El alcance por rol (ROLE_DUENO ve
 * solo citas de sus propias mascotas; ROLE_VETERINARIO ve y edita
 * ÚNICAMENTE sus propias citas asignadas, tanto en listado como en
 * lectura/escritura individual -corrección "demo local", fase de roles-)
 * ocurre en el servidor (CitaService); este servicio nunca reimplementa
 * esas reglas ni las filtra de nuevo en el cliente.
 */
@Injectable({ providedIn: 'root' })
export class CitaApiService {
  private readonly base = '/api/citas';

  constructor(private http: HttpClient) {}

  listar(page: number, size: number, filtros: FiltrosCitas = {}, sort = 'fechaHora,desc'): Observable<PageResponse<Cita>> {
    let params = new HttpParams().set('page', page).set('size', size).set('sort', sort);
    if (filtros.mascotaId != null) params = params.set('mascotaId', filtros.mascotaId);
    if (filtros.veterinarioId != null) params = params.set('veterinarioId', filtros.veterinarioId);
    if (filtros.estado) params = params.set('estado', filtros.estado);
    if (filtros.desde) params = params.set('desde', filtros.desde);
    if (filtros.hasta) params = params.set('hasta', filtros.hasta);
    return this.http.get<PageResponse<Cita>>(this.base, { params });
  }

  listarPorMascota(mascotaId: number, page: number, size: number): Observable<PageResponse<Cita>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<Cita>>(`${this.base}/mascota/${mascotaId}`, { params });
  }

  crear(payload: CitaRequestPayload): Observable<Cita> {
    return this.http.post<Cita>(this.base, payload);
  }

  actualizar(id: number, payload: CitaRequestPayload): Observable<Cita> {
    return this.http.put<Cita>(`${this.base}/${id}`, payload);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
