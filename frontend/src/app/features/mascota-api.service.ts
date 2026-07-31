import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Mascota {
  id: number;
  duenioId: number;
  duenioNombre: string;
  nombre: string;
  especie: string;
  raza: string;
  fechaNacimiento: string; // ISO yyyy-MM-dd
  activo: boolean;
  creadoEn: string;
  actualizadoEn: string;
}

/** Espejo de MascotaRequest del backend. */
export interface MascotaRequestPayload {
  duenioId: number;
  nombre: string;
  especie: string;
  raza: string;
  fechaNacimiento: string; // yyyy-MM-dd
}

export interface ResumenEspecie {
  especie: string;
  total: number;
}

/** Espejo de org.springframework.data.domain.Page<T> serializado por Jackson. */
export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number; // pagina actual, 0-indexada
  size: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

/**
 * Encapsula las llamadas HTTP de /api/mascotas. El componente de UI
 * (MascotasComponent) no arma URLs ni HttpParams directamente: así se
 * mantiene testeable por separado y evita que la paginación/orden se
 * reimplemente distinto en cada pantalla futura.
 *
 * IMPORTANTE (regla del backend, ver MascotaService.listar): el filtrado
 * por dueño para ROLE_DUENO ocurre en el servidor. Este servicio nunca debe
 * agregar un filtro de dueño "por si acaso": eso duplicaría una regla de
 * seguridad que ya vive, y debe seguir viviendo, únicamente en el backend.
 */
@Injectable({ providedIn: 'root' })
export class MascotaApiService {
  private readonly base = '/api/mascotas';

  constructor(private http: HttpClient) {}

  listar(page: number, size: number, sort = 'id,asc'): Observable<PageResponse<Mascota>> {
    const params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', sort);
    return this.http.get<PageResponse<Mascota>>(this.base, { params });
  }

  crear(payload: MascotaRequestPayload): Observable<Mascota> {
    return this.http.post<Mascota>(this.base, payload);
  }

  actualizar(id: number, payload: MascotaRequestPayload): Observable<Mascota> {
    return this.http.put<Mascota>(`${this.base}/${id}`, payload);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  /**
   * duenioId es opcional: el backend ya restringe a "solo lo mío" cuando
   * quien pregunta es ROLE_DUENO, y agrega el total general cuando es
   * ADMIN/VETERINARIO/AUXILIAR y no se pasa duenioId.
   */
  resumenPorEspecies(duenioId?: number): Observable<ResumenEspecie[]> {
    let params = new HttpParams();
    if (duenioId != null) params = params.set('duenioId', duenioId);
    return this.http.get<ResumenEspecie[]>(`${this.base}/resumen-especies`, { params });
  }
}
