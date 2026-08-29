import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { TipoIdentificacionSri } from './factura-api.service';

/** Espejo de DatosFacturacionResponse. Nunca incluye usuarioId: viaja en el path, no en el cuerpo. */
export interface DatosFacturacion {
  id: number;
  tipoIdentificacion: TipoIdentificacionSri;
  identificacion: string;
  razonSocial: string;
  direccion: string | null;
  telefono: string | null;
  emailFacturacion: string | null;
  predeterminado: boolean;
  activo: boolean;
}

/** Espejo de DatosFacturacionRequest (mismo cuerpo para alta y edición). Sin `predeterminado`: eso es el PATCH dedicado. */
export interface DatosFacturacionRequestPayload {
  tipoIdentificacion: TipoIdentificacionSri;
  identificacion: string;
  razonSocial: string;
  direccion: string | null;
  telefono: string | null;
  emailFacturacion: string | null;
}

/**
 * Encapsula /api/usuarios/{usuarioId}/datos-facturacion (Fase 8B): cierra el
 * hueco operativo de la Fase 8A (antes solo se podían SELECCIONAR datos ya
 * existentes al armar un borrador).
 *
 * `usuarioId` es SIEMPRE explícito en cada llamada — nunca implícito ni
 * derivado aquí de la sesión — pero quien decide qué id pasar es el
 * componente que llama (para DUENO, siempre el suyo propio, tomado de
 * `AuthService.usuarioActual()`; jamás un id escrito a mano). El backend
 * vuelve a comprobar el ownership real de todas formas: un DUENO pidiendo el
 * usuarioId de otro recibe 403 (DatosFacturacionService.exigirAcceso), pase
 * lo que pase aquí.
 */
@Injectable({ providedIn: 'root' })
export class DatosFacturacionApiService {
  private base(usuarioId: number): string {
    return `/api/usuarios/${usuarioId}/datos-facturacion`;
  }

  constructor(private http: HttpClient) {}

  listar(usuarioId: number): Observable<DatosFacturacion[]> {
    return this.http.get<DatosFacturacion[]>(this.base(usuarioId));
  }

  obtenerPredeterminado(usuarioId: number): Observable<DatosFacturacion> {
    return this.http.get<DatosFacturacion>(`${this.base(usuarioId)}/predeterminado`);
  }

  buscar(usuarioId: number, id: number): Observable<DatosFacturacion> {
    return this.http.get<DatosFacturacion>(`${this.base(usuarioId)}/${id}`);
  }

  crear(usuarioId: number, payload: DatosFacturacionRequestPayload): Observable<DatosFacturacion> {
    return this.http.post<DatosFacturacion>(this.base(usuarioId), payload);
  }

  actualizar(usuarioId: number, id: number, payload: DatosFacturacionRequestPayload): Observable<DatosFacturacion> {
    return this.http.put<DatosFacturacion>(`${this.base(usuarioId)}/${id}`, payload);
  }

  marcarPredeterminado(usuarioId: number, id: number): Observable<DatosFacturacion> {
    return this.http.patch<DatosFacturacion>(`${this.base(usuarioId)}/${id}/predeterminado`, {});
  }

  /** Baja lógica (activo=false) — el backend nunca borra físicamente un registro que pudo ser el snapshot de una factura. */
  desactivar(usuarioId: number, id: number): Observable<void> {
    return this.http.delete<void>(`${this.base(usuarioId)}/${id}`);
  }
}
