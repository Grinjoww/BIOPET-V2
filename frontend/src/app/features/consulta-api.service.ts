import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PageResponse } from './mascota-api.service';

/** Espejo de ConsultaResponse del backend. */
export interface Consulta {
  id: number;
  mascotaId: number;
  mascotaNombre: string;
  veterinarioId: number;
  veterinarioNombre: string;
  fechaConsulta: string; // ISO Instant
  motivo: string;
  diagnostico: string | null;
  tratamiento: string | null;
  observaciones: string | null;
  activo: boolean;
  creadoEn: string;
  actualizadoEn: string;
}

/** Espejo de ConsultaRequest del backend. */
export interface ConsultaRequestPayload {
  mascotaId: number;
  veterinarioId: number;
  fechaConsulta: string; // ISO Instant
  motivo: string;
  diagnostico: string | null;
  tratamiento: string | null;
  observaciones: string | null;
}

/** Filtros opcionales de GET /api/consultas — reflejan los @RequestParam de ConsultaController. */
export interface FiltrosConsultas {
  mascotaId?: number;
  veterinarioId?: number;
  q?: string; // busca en motivo y diagnóstico
  desde?: string; // ISO Instant (UTC)
  hasta?: string; // ISO Instant (UTC)
}

/**
 * Encapsula /api/consultas completo: el registro clínico general
 * (Consultas V2) y GET /mascota/{id} que ya usaba la pestaña "Consultas"
 * de la ficha de mascota (solo lectura, sin cambios). El alcance por rol
 * (ROLE_DUENO ve solo consultas de sus propias mascotas — Corrección A;
 * ROLE_VETERINARIO ve y edita ÚNICAMENTE sus propias consultas asignadas
 * -corrección "demo local", fase de roles-) ocurre en el servidor
 * (ConsultaService); este servicio nunca reimplementa esas reglas.
 */
@Injectable({ providedIn: 'root' })
export class ConsultaApiService {
  private readonly base = '/api/consultas';

  constructor(private http: HttpClient) {}

  listar(page: number, size: number, filtros: FiltrosConsultas = {}, sort = 'fechaConsulta,desc'): Observable<PageResponse<Consulta>> {
    let params = new HttpParams().set('page', page).set('size', size).set('sort', sort);
    if (filtros.mascotaId != null) params = params.set('mascotaId', filtros.mascotaId);
    if (filtros.veterinarioId != null) params = params.set('veterinarioId', filtros.veterinarioId);
    if (filtros.q) params = params.set('q', filtros.q);
    if (filtros.desde) params = params.set('desde', filtros.desde);
    if (filtros.hasta) params = params.set('hasta', filtros.hasta);
    return this.http.get<PageResponse<Consulta>>(this.base, { params });
  }

  listarPorMascota(mascotaId: number, page: number, size: number): Observable<PageResponse<Consulta>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<Consulta>>(`${this.base}/mascota/${mascotaId}`, { params });
  }

  crear(payload: ConsultaRequestPayload): Observable<Consulta> {
    return this.http.post<Consulta>(this.base, payload);
  }

  actualizar(id: number, payload: ConsultaRequestPayload): Observable<Consulta> {
    return this.http.put<Consulta>(`${this.base}/${id}`, payload);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
