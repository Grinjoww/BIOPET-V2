/**
 * Helpers de presentación VISUAL, compartidos entre MascotasComponent y
 * MascotaDetalleComponent. Ninguno de estos valores existe en el backend
 * ni se persiste: son cálculos puros de cliente sobre campos reales.
 */

/**
 * "EXP-000123" a partir de Mascota.id. No es un campo nuevo, no se
 * persiste, no viaja al backend, no modifica MascotaResponse ni ningún
 * DTO. Si en el futuro se autoriza exponer `fn_siguiente_numero_ficha`
 * (número de ficha real, correlativo y generado por el backend), esa
 * sería una fuente de datos distinta — no debe confundirse con esto.
 */
export function formatearExpediente(id: number): string {
  return `EXP-${String(id).padStart(6, '0')}`;
}

/**
 * Edad legible ("3 años" / "4 meses" / "Menos de 1 mes") a partir de
 * Mascota.fechaNacimiento (LocalDate real del backend). Cálculo de
 * calendario simple; no requiere ninguna llamada adicional.
 */
export function calcularEdad(fechaNacimientoIso: string): string {
  const nacimiento = new Date(`${fechaNacimientoIso}T00:00:00`);
  const hoy = new Date();

  let anios = hoy.getFullYear() - nacimiento.getFullYear();
  let meses = hoy.getMonth() - nacimiento.getMonth();
  if (hoy.getDate() < nacimiento.getDate()) meses -= 1;
  if (meses < 0) {
    anios -= 1;
    meses += 12;
  }

  if (anios < 1) {
    return meses <= 0 ? 'Menos de 1 mes' : `${meses} ${meses === 1 ? 'mes' : 'meses'}`;
  }
  return `${anios} ${anios === 1 ? 'año' : 'años'}`;
}

export type EstadoVacuna = 'vencida' | 'proxima' | 'al-dia' | 'sin-programar';

const TREINTA_DIAS_MS = 30 * 24 * 60 * 60 * 1000;

/**
 * Estado de PRESENTACIÓN derivado de Vacuna.proximaFecha vs. hoy — NO es
 * un campo del backend (VacunaResponse no tiene columna "estado"; ver
 * V4__vacunas.sql). Se calcula aquí exclusivamente para dar contexto
 * visual inmediato; el backend nunca envía ni valida este valor.
 */
export function estadoVacuna(proximaFecha: string | null): EstadoVacuna {
  if (!proximaFecha) return 'sin-programar';

  const hoy = new Date();
  hoy.setHours(0, 0, 0, 0);
  const proxima = new Date(`${proximaFecha}T00:00:00`);

  if (proxima.getTime() < hoy.getTime()) return 'vencida';
  if (proxima.getTime() - hoy.getTime() <= TREINTA_DIAS_MS) return 'proxima';
  return 'al-dia';
}

export function etiquetaEstadoVacuna(estado: EstadoVacuna): string {
  switch (estado) {
    case 'vencida':
      return 'Refuerzo vencido';
    case 'proxima':
      return 'Refuerzo próximo';
    case 'al-dia':
      return 'Al día';
    case 'sin-programar':
      return 'Sin refuerzo programado';
  }
}

export function chipClaseEstadoVacuna(estado: EstadoVacuna): string {
  switch (estado) {
    case 'vencida':
      return 'chip--danger';
    case 'proxima':
      return 'chip--warning';
    case 'al-dia':
      return 'chip--success';
    case 'sin-programar':
      return 'chip--neutral';
  }
}

/**
 * A diferencia de EstadoVacuna (arriba), esto NO es un estado derivado:
 * PROGRAMADA/CANCELADA/COMPLETADA son los tres valores reales del enum
 * `com.biopet.entity.EstadoCita` — solo se traduce a una clase visual de
 * chip, sin inventar ningún estado nuevo.
 */
export function chipClaseEstadoCita(estado: 'PROGRAMADA' | 'CANCELADA' | 'COMPLETADA'): string {
  switch (estado) {
    case 'PROGRAMADA':
      return 'chip--info';
    case 'COMPLETADA':
      return 'chip--success';
    case 'CANCELADA':
      return 'chip--neutral';
  }
}

/**
 * Etiqueta legible en español-sentencia para el mismo enum real de arriba
 * (no es un estado nuevo, solo capitalización de presentación en vez del
 * "PROGRAMADA" en mayúsculas fijas que usa el JSON del backend).
 */
export function etiquetaEstadoCita(estado: 'PROGRAMADA' | 'CANCELADA' | 'COMPLETADA'): string {
  switch (estado) {
    case 'PROGRAMADA':
      return 'Programada';
    case 'COMPLETADA':
      return 'Completada';
    case 'CANCELADA':
      return 'Cancelada';
  }
}

/**
 * Extraídas de CitasComponent (misma lógica, sin cambios de comportamiento)
 * exclusivamente para poder cubrirlas con tests unitarios sin instanciar el
 * componente completo (FormBuilder, 3 servicios HTTP, etc.).
 *
 * `<input type="datetime-local">` expone y recibe una hora en la ZONA
 * HORARIA LOCAL DEL NAVEGADOR, sin sufijo. `CitaRequest.fechaHora` es un
 * `java.time.Instant` (UTC absoluto) — el backend no conoce ni expone
 * ninguna zona horaria propia. `new Date(valorLocal)` y `Date.toISOString()`
 * son exactamente la conversión local↔UTC estándar del navegador: no es una
 * conversión "silenciosa", es la traducción correcta y esperada entre un
 * input local y un instante absoluto, documentada aquí a propósito.
 */
export function instantAFechaHoraLocal(iso: string): string {
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export function fechaHoraLocalAInstant(local: string): string {
  return new Date(local).toISOString();
}
