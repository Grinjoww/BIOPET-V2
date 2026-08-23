import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

/** Espejo de ExternalApiResponse del backend (GET /api/externa/especies). */
export interface InfoEspecie {
  especieConsultada: string;
  nombreCientifico: string | null;
  habitat: string | null;
  dieta: string | null;
  /** "cache" o "api-ninjas" */
  origen: string;
  consultadoEn: string;
}

/**
 * Encapsula GET /api/externa/especies?especie=. Complementaria por
 * naturaleza: el backend puede responder 502 (ExternalApiException) si la
 * API externa no reconoce el término buscado —verificado en vivo: la
 * especie tal como la escribe la clínica ("Perro", "Gato"…) no es la que
 * la API externa espera— o si el servicio externo no está disponible. El
 * consumidor de este servicio (MascotaDetalleComponent) es responsable de
 * degradar con elegancia, nunca de romper la ficha por esto.
 */
@Injectable({ providedIn: 'root' })
export class EspecieApiService {
  private readonly base = '/api/externa/especies';

  constructor(private http: HttpClient) {}

  buscar(especie: string): Observable<InfoEspecie> {
    const params = new HttpParams().set('especie', especie);
    return this.http.get<InfoEspecie>(this.base, { params });
  }
}
