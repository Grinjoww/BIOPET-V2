import { Injectable } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ProblemDetail } from './http-error.interceptor';

/**
 * Traduce cualquier error HTTP del backend (formato ProblemDetail, RFC 7807)
 * a un mensaje legible para el usuario. Centraliza la interpretación de
 * 400/401/403/404/422/429 para no repetir este switch en cada componente
 * (login, mascotas, y los que vengan después).
 */
@Injectable({ providedIn: 'root' })
export class ProblemDetailService {

  mensaje(err: HttpErrorResponse): string {
    const problem = err.error as ProblemDetail | null;

    switch (err.status) {
      case 400:
        return problem?.detail ?? 'La solicitud no tiene un formato válido.';
      case 401:
        return problem?.detail ?? 'Tu sesión no es válida. Inicia sesión nuevamente.';
      case 403:
        return problem?.detail ?? 'No tienes permiso para realizar esta acción.';
      case 404:
        return problem?.detail ?? 'El recurso solicitado no existe.';
      case 409:
        return problem?.detail ?? 'Ya existe un registro con esos datos (por ejemplo, un correo duplicado).';
      case 422:
        return this.mensajeValidacion(problem) ?? problem?.detail ?? 'Hay campos con datos inválidos.';
      case 429:
        // El backend aún no implementa el rate limiting general (solo login),
        // pero se deja el caso preparado como pide el punto 3 del encargo.
        return problem?.detail ?? 'Demasiados intentos. Intenta nuevamente más tarde.';
      default:
        return problem?.detail ?? 'No se pudo completar la operación. Intenta nuevamente.';
    }
  }

  /**
   * El 422 de validación (MethodArgumentNotValidException en el backend)
   * agrega una propiedad extra "errors": { campo: [mensajes] } además de
   * "detail". Si existe, arma un resumen más útil que el detail genérico.
   */
  private mensajeValidacion(problem: ProblemDetail | null): string | null {
    const errores = (problem as any)?.errors as Record<string, string[]> | undefined;
    if (!errores || Object.keys(errores).length === 0) return null;

    const partes = Object.entries(errores).map(
      ([campo, mensajes]) => `${campo}: ${mensajes.join(', ')}`
    );
    return partes.join(' · ');
  }

  /**
   * Expone el mapa crudo campo -> mensajes del 422, para que los
   * formularios (p. ej. el de mascotas) puedan pintar el mensaje debajo
   * del campo exacto en lugar de solo mostrar un resumen general.
   * Devuelve null si el error no es un 422 con `errors` o si no hay error.
   */
  erroresPorCampo(err: HttpErrorResponse | null): Record<string, string[]> | null {
    if (!err || err.status !== 422) return null;
    const problem = err.error as ProblemDetail | null;
    const errores = (problem as any)?.errors as Record<string, string[]> | undefined;
    return errores && Object.keys(errores).length ? errores : null;
  }

  /** Primer mensaje de error para un campo concreto, o null si no aplica. */
  primerErrorDeCampo(err: HttpErrorResponse | null, campo: string): string | null {
    const errores = this.erroresPorCampo(err);
    const mensajes = errores?.[campo];
    return mensajes && mensajes.length ? mensajes[0] : null;
  }
}