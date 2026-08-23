import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { RolBiopet } from './roles';

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
 */
@Injectable({ providedIn: 'root' })
export class UsuarioSeleccionableApiService {
  private readonly base = '/api/usuarios';

  constructor(private http: HttpClient) {}

  listarDuenios(): Observable<UsuarioSeleccionable[]> {
    return this.http.get<UsuarioSeleccionable[]>(`${this.base}/duenios`);
  }

  listarVeterinarios(): Observable<UsuarioSeleccionable[]> {
    return this.http.get<UsuarioSeleccionable[]>(`${this.base}/veterinarios`);
  }
}
