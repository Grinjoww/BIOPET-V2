import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { RolBiopet } from './roles';
import { PageResponse } from '../features/mascota-api.service';

/** Espejo de UsuarioSeleccionableResponse del backend. Deliberadamente sin
 *  `activo` ni ningún campo administrativo: no es el CRUD de usuarios. */
export interface UsuarioSeleccionable {
  id: number;
  nombre: string;
  email: string;
  rol: RolBiopet;
}

/**
 * Encapsula GET /api/usuarios/duenios y GET /api/usuarios/veterinarios
 * (Corrección B del backend) — los únicos dos listados de usuarios a los
 * que ADMIN/VETERINARIO/AUXILIAR tienen acceso, pensados exclusivamente
 * para poblar selectores. Nunca llama a GET /api/usuarios (el CRUD
 * administrativo completo, restringido a ADMIN): son endpoints distintos
 * con audiencias distintas, y este servicio no debe mezclarlos.
 *
 * Auditoría de usabilidad con datos masivos (biopet_db_1m_v2: 2.002
 * usuarios): ambos endpoints ahora devuelven `Page<...>` y aceptan `q`
 * (nombre o email) — antes traían TODOS los dueños/veterinarios activos de
 * una sola vez. `q` es opcional para no romper a nadie que solo pagine sin
 * buscar (por ejemplo, mostrar los primeros N como sugerencia inicial).
 */
@Injectable({ providedIn: 'root' })
export class UsuarioSeleccionableApiService {
  private readonly base = '/api/usuarios';

  constructor(private http: HttpClient) {}

  buscarDuenios(q: string | null, page: number, size: number): Observable<PageResponse<UsuarioSeleccionable>> {
    return this.buscar('duenios', q, page, size);
  }

  buscarVeterinarios(q: string | null, page: number, size: number): Observable<PageResponse<UsuarioSeleccionable>> {
    return this.buscar('veterinarios', q, page, size);
  }

  private buscar(recurso: string, q: string | null, page: number, size: number): Observable<PageResponse<UsuarioSeleccionable>> {
    let params = new HttpParams().set('page', page).set('size', size).set('sort', 'nombre,asc');
    if (q && q.trim().length > 0) params = params.set('q', q.trim());
    return this.http.get<PageResponse<UsuarioSeleccionable>>(`${this.base}/${recurso}`, { params });
  }
}
