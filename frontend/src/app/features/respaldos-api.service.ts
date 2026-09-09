import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PageResponse } from './mascota-api.service';

/**
 * Espejo de java.time.DayOfWeek, tal cual lo usa
 * com.biopet.backup.entity.RespaldoConfiguracion.diaSemana (se reutiliza el
 * enum de Java, no uno propio -MONDAY..SUNDAY son exactamente los valores
 * pedidos).
 */
export type DiaSemana = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';

/** Espejo de com.biopet.backup.entity.TipoRespaldo. */
export type TipoRespaldo = 'MANUAL' | 'AUTOMATICO';

/** Espejo de com.biopet.backup.entity.EstadoRespaldo. */
export type EstadoRespaldo = 'EN_PROCESO' | 'EXITOSO' | 'FALLIDO';

/**
 * Espejo de RespaldoConfiguracionResponse. {@code hora} llega como
 * "HH:mm:ss" (java.time.LocalTime serializado por Jackson) -el
 * formulario solo pide/muestra HH:mm, ver respaldos.component.ts.
 */
export interface RespaldoConfiguracion {
  activo: boolean;
  diaSemana: DiaSemana;
  hora: string;
  ultimoRespaldoEn: string | null;
  proximoRespaldoEn: string | null;
  actualizadoEn: string;
  actualizadoPor: string | null;
}

/** Espejo de RespaldoConfiguracionRequest (mismo cuerpo para guardar). {@code hora} en formato "HH:mm". */
export interface RespaldoConfiguracionRequestPayload {
  activo: boolean;
  diaSemana: DiaSemana;
  hora: string;
}

/** Espejo de RespaldoHistorialResponse. */
export interface RespaldoHistorial {
  id: number;
  tipo: TipoRespaldo;
  estado: EstadoRespaldo;
  iniciadoEn: string;
  finalizadoEn: string | null;
  duracionMs: number | null;
  nombreArchivo: string | null;
  tamanoBytes: number | null;
  mensajeErrorSeguro: string | null;
  ejecutadoPor: string | null;
}

/**
 * Encapsula /api/admin/respaldos/{configuracion,ejecutar,historial}. Solo
 * ROLE_ADMIN tiene acceso real en el backend (RespaldoController); esta
 * pantalla nunca se ofrece a otro rol (ver roles.ts/app.routes.ts).
 */
@Injectable({ providedIn: 'root' })
export class RespaldosApiService {
  private readonly base = '/api/admin/respaldos';

  constructor(private http: HttpClient) {}

  obtenerConfiguracion(): Observable<RespaldoConfiguracion> {
    return this.http.get<RespaldoConfiguracion>(`${this.base}/configuracion`);
  }

  guardarConfiguracion(payload: RespaldoConfiguracionRequestPayload): Observable<RespaldoConfiguracion> {
    return this.http.put<RespaldoConfiguracion>(`${this.base}/configuracion`, payload);
  }

  /** 202: el respaldo queda EN_PROCESO al responder; el resultado final se ve en historial(). */
  generarRespaldoAhora(): Observable<RespaldoHistorial> {
    return this.http.post<RespaldoHistorial>(`${this.base}/ejecutar`, {});
  }

  historial(pagina: number, tamanio: number): Observable<PageResponse<RespaldoHistorial>> {
    const params = new HttpParams().set('page', pagina).set('size', tamanio);
    return this.http.get<PageResponse<RespaldoHistorial>>(`${this.base}/historial`, { params });
  }
}
