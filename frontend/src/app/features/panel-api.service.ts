import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

/**
 * Espejo de DashboardResumenResponse del backend
 * (GET /api/dashboard/resumen). Solo 5 números reales de
 * fn_reporte_dashboard + el rango que efectivamente se usó para
 * calcularlos (eco del servidor, no un valor recalculado en cliente).
 *
 * IMPORTANTE — no los 5 están acotados por el rango: `mascotasActivas`,
 * `citasProgramadas` y `mascotasSinConsulta` son agregados SIEMPRE
 * globales (todo el historial activo), independientes de
 * `desde`/`hasta`. Solo `consultasEnRango` y `vacunasEnRango` están
 * realmente filtrados por el rango. Ver el mismo aviso, más detallado,
 * en DashboardResumenResponse.java — este comentario no debe perder
 * sincronía con ese.
 */
export interface DashboardResumen {
  desde: string; // ISO yyyy-MM-dd
  hasta: string; // ISO yyyy-MM-dd
  mascotasActivas: number;
  citasProgramadas: number;
  consultasEnRango: number;
  vacunasEnRango: number;
  mascotasSinConsulta: number;
}

/**
 * Encapsula GET /api/dashboard/resumen. Servicio propio, deliberadamente
 * no mezclado con MascotaApiService/CitaApiService/ConsultaApiService/
 * VacunaApiService: no es un CRUD, es un único agregado de solo lectura.
 */
@Injectable({ providedIn: 'root' })
export class PanelApiService {
  private readonly base = '/api/dashboard';

  constructor(private http: HttpClient) {}

  resumen(desde: string, hasta: string): Observable<DashboardResumen> {
    const params = new HttpParams().set('desde', desde).set('hasta', hasta);
    return this.http.get<DashboardResumen>(`${this.base}/resumen`, { params });
  }
}
