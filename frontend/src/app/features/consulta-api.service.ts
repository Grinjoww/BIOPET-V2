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

/**
 * Encapsula /api/consultas completo: el registro clínico general
 * (Consultas V2) y GET /mascota/{id} que ya usaba la pestaña "Consultas"
 * de la ficha de mascota (solo lectura, sin cambios). El filtrado por
 * dueño (ROLE_DUENO ve solo consultas de sus propias mascotas —
 * Corrección A) ocurre en el servidor (ConsultaService.listar); este
 * servicio nunca reimplementa ese filtro en el cliente.
 */
@Injectable({ providedIn: 'root' })
export class ConsultaApiService {
  private readonly base = '/api/consultas';

  constructor(private http: HttpClient) {}

  listar(page: number, size: number, sort = 'fechaConsulta,desc'): Observable<PageResponse<Consulta>> {
    const params = new HttpParams().set('page', page).set('size', size).set('sort', sort);
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
