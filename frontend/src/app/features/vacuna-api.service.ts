import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PageResponse } from './mascota-api.service';

export interface Vacuna {
  id: number;
  mascotaId: number;
  mascotaNombre: string;
  veterinarioId: number | null;
  veterinarioNombre: string | null;
  tipo: string;
  fechaAplicacion: string; // ISO yyyy-MM-dd
  proximaFecha: string | null;
  observaciones: string | null;
  activo: boolean;
  creadoEn: string;
  actualizadoEn: string;
}

/** Espejo de VacunaRequest del backend. */
export interface VacunaRequestPayload {
  mascotaId: number;
  veterinarioId: number | null;
  tipo: string;
  fechaAplicacion: string; // yyyy-MM-dd
  proximaFecha: string | null;
  observaciones: string | null;
}

/**
 * Encapsula las llamadas HTTP de /api/vacunas. Igual que MascotaApiService:
 * el filtrado por dueño (ROLE_DUENO ve solo las vacunas de sus propias
 * mascotas) ocurre en el servidor (VacunaService.listar); este servicio
 * nunca debe reimplementar ese filtro en el cliente.
 */
@Injectable({ providedIn: 'root' })
export class VacunaApiService {
  private readonly base = '/api/vacunas';

  constructor(private http: HttpClient) {}

  listar(page: number, size: number, sort = 'id,desc'): Observable<PageResponse<Vacuna>> {
    const params = new HttpParams().set('page', page).set('size', size).set('sort', sort);
    return this.http.get<PageResponse<Vacuna>>(this.base, { params });
  }

  listarPorMascota(mascotaId: number, page: number, size: number): Observable<PageResponse<Vacuna>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<Vacuna>>(`${this.base}/mascota/${mascotaId}`, { params });
  }

  crear(payload: VacunaRequestPayload): Observable<Vacuna> {
    return this.http.post<Vacuna>(this.base, payload);
  }

  actualizar(id: number, payload: VacunaRequestPayload): Observable<Vacuna> {
    return this.http.put<Vacuna>(`${this.base}/${id}`, payload);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
