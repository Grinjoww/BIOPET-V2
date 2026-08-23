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

/**
 * Encapsula /api/citas completo: el listado general paginado y el CRUD
 * (Citas V2), además de GET /mascota/{id} que ya usaba la ficha de
 * mascota (solo lectura, sin cambios). El filtrado por dueño (ROLE_DUENO
 * ve solo citas de sus propias mascotas) y la restricción de escritura
 * de ROLE_VETERINARIO (solo sus propias citas asignadas) ocurren en el
 * servidor (CitaService); este servicio nunca reimplementa esas reglas.
 */
@Injectable({ providedIn: 'root' })
export class CitaApiService {
  private readonly base = '/api/citas';

  constructor(private http: HttpClient) {}

  listar(page: number, size: number, sort = 'fechaHora,desc'): Observable<PageResponse<Cita>> {
    const params = new HttpParams().set('page', page).set('size', size).set('sort', sort);
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
