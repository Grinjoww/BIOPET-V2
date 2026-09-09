import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PageResponse } from './mascota-api.service';

/** Espejo de AuditoriaEventoResponse (com.biopet.audit.dto). */
export interface AuditoriaEvento {
  id: number;
  fechaHora: string;
  usuarioEmail: string | null;
  accion: string;
  modulo: string;
  recurso: string | null;
  metodoHttp: string | null;
  resultado: string;
  statusHttp: number | null;
}

/** Espejo de AuditoriaOpcionesResponse: valores REALMENTE presentes, para los 3 selects del filtro. */
export interface AuditoriaOpciones {
  usuarios: string[];
  acciones: string[];
  modulos: string[];
}

export interface FiltrosAuditoria {
  usuario?: string;
  accion?: string;
  modulo?: string;
  desde?: string; // ISO-8601 (datetime-local -> se le agrega ":00" si falta)
  hasta?: string;
}

/**
 * Encapsula /api/admin/auditoria{,/opciones}. Solo ROLE_ADMIN tiene acceso
 * real en el backend (AuditoriaController); esta pantalla nunca se ofrece
 * a otro rol (ver roles.ts/app.routes.ts). Solo lectura: la escritura la
 * hacen AuditoriaHttpFilter y AuditoriaService desde el propio backend.
 */
@Injectable({ providedIn: 'root' })
export class AuditoriaApiService {
  private readonly base = '/api/admin/auditoria';

  constructor(private http: HttpClient) {}

  buscar(filtros: FiltrosAuditoria, pagina: number, tamanio: number): Observable<PageResponse<AuditoriaEvento>> {
    let params = new HttpParams().set('page', pagina).set('size', tamanio);
    if (filtros.usuario) params = params.set('usuario', filtros.usuario);
    if (filtros.accion) params = params.set('accion', filtros.accion);
    if (filtros.modulo) params = params.set('modulo', filtros.modulo);
    if (filtros.desde) params = params.set('desde', filtros.desde);
    if (filtros.hasta) params = params.set('hasta', filtros.hasta);
    return this.http.get<PageResponse<AuditoriaEvento>>(this.base, { params });
  }

  opciones(): Observable<AuditoriaOpciones> {
    return this.http.get<AuditoriaOpciones>(`${this.base}/opciones`);
  }
}
