import { IconName } from '../shared/icons/icon-registry';

/**
 * Roles reales del backend (com.biopet.entity.Rol). No crear roles
 * nuevos aquí: esta unión debe reflejar exactamente el enum del servidor.
 */
export type RolBiopet = 'ROLE_ADMIN' | 'ROLE_VETERINARIO' | 'ROLE_AUXILIAR' | 'ROLE_DUENO';

export interface NavItem {
  path: string;
  label: string;
  icon: IconName;
}

export interface NavGroup {
  /** Eyebrow de la sección en el sidebar (PACIENTES, ADMINISTRACIÓN…). */
  label: string;
  items: NavItem[];
}

/**
 * Navegación del shell autenticado, agrupada y etiquetada por rol.
 *
 * Esto NO es control de seguridad: es exclusivamente wayfinding de UI.
 * Cada ruta sigue protegida en el backend por @PreAuthorize
 * independientemente de qué vea el sidebar (ver core/role.guard.ts).
 *
 * Diferencias deliberadas sobre una lista fija por rol:
 * - "Mis mascotas" / "Mis citas" / "Mis consultas" / "Mis vacunas" solo
 *   para ROLE_DUENO, porque el backend realmente le filtra las 4 a lo
 *   suyo (MascotaService/CitaService/ConsultaService/VacunaService); para
 *   los demás roles el listado es global y llamarlo "mis" sería falso.
 *   (Corregido en esta fase: antes "Consultas"/"Vacunas" quedaban con la
 *   etiqueta genérica también para ROLE_DUENO pese a estar igual de
 *   filtradas por propiedad que Mascotas/Citas — inconsistencia real,
 *   no un contrato deseado; ver informe de la fase anterior.)
 * - "Usuarios" solo aparece para ROLE_ADMIN, que es el único rol que
 *   puede listar/crear/editar/dar de baja usuarios (UsuarioController).
 *
 * El grupo "Administración" nunca queda vacío para ningún rol: "Perfil"
 * vive ahí y existe siempre, así que no hace falta lógica para ocultar
 * grupos sin contenido (solo su ítem "Usuarios" es condicional).
 */
export function navGroupsParaRol(rol: RolBiopet): NavGroup[] {
  const esDueno = rol === 'ROLE_DUENO';

  const clinica: NavItem[] = [];
  // Panel V2 (GET /api/dashboard/resumen) expone agregados de TODA la
  // clínica — fn_reporte_dashboard no admite filtrar por dueño, a
  // diferencia de fn_resumen_mascotas_por_especie. No hay una versión
  // "personal" honesta de este resumen para ROLE_DUENO sin inventar un
  // endpoint nuevo, así que directamente no se le ofrece el enlace (su
  // entrada real sigue siendo "Mis mascotas"). roleGuard en
  // app.routes.ts respalda esto igual si alguien teclea /panel a mano.
  if (!esDueno) {
    clinica.push({ path: '/panel', label: 'Panel', icon: 'panel' });
  }
  clinica.push(
    { path: '/mascotas', label: esDueno ? 'Mis mascotas' : 'Mascotas', icon: 'mascotas' },
    { path: '/citas', label: esDueno ? 'Mis citas' : 'Citas', icon: 'citas' },
    { path: '/consultas', label: esDueno ? 'Mis consultas' : 'Consultas', icon: 'consultas' },
    { path: '/vacunas', label: esDueno ? 'Mis vacunas' : 'Vacunas', icon: 'vacunas' }
  );

  const administracion: NavItem[] = [];
  if (rol === 'ROLE_ADMIN') {
    administracion.push({ path: '/usuarios', label: 'Usuarios', icon: 'usuarios' });
  }
  administracion.push({ path: '/perfil', label: 'Perfil', icon: 'perfil' });

  return [
    { label: 'Clínica', items: clinica },
    { label: 'Administración', items: administracion },
  ];
}

export function humanizarRol(rol: RolBiopet | string | undefined | null): string {
  switch (rol) {
    case 'ROLE_ADMIN':
      return 'Administrador';
    case 'ROLE_VETERINARIO':
      return 'Veterinario';
    case 'ROLE_AUXILIAR':
      return 'Auxiliar';
    case 'ROLE_DUENO':
      return 'Dueño';
    default:
      return 'Usuario';
  }
}

/**
 * Ruta de aterrizaje tras login (y raíz "/" / wildcard "**") según rol.
 * Única fuente de esta decisión — LoginComponent y el guard de
 * redirección de ruta vacía/wildcard (core/home-redirect.guard.ts) llaman
 * a esta misma función en vez de repetir el criterio cada uno por su
 * lado.
 *
 * ADMIN/VETERINARIO/AUXILIAR aterrizan en Panel porque los 3 tienen
 * acceso real (@PreAuthorize de GET /api/dashboard/resumen). ROLE_DUENO
 * sigue en "Mis mascotas": no tiene acceso a Panel (fn_reporte_dashboard
 * no admite filtrar por dueño — mismo motivo por el que roles.ts no le
 * ofrece el enlace "Panel" en el sidebar) y Mascotas es su aterrizaje
 * natural y completo.
 */
export function rutaInicioParaRol(rol: RolBiopet): string {
  if (rol === 'ROLE_ADMIN' || rol === 'ROLE_VETERINARIO' || rol === 'ROLE_AUXILIAR') {
    return '/panel';
  }
  return '/mascotas';
}

/** Iniciales para el avatar del pie del sidebar (máximo 2 letras). */
export function inicialesDe(nombre: string | undefined | null): string {
  if (!nombre) return '?';
  const partes = nombre.trim().split(/\s+/).filter(Boolean);
  const iniciales = partes.slice(0, 2).map((parte) => parte[0]?.toUpperCase() ?? '');
  return iniciales.join('') || '?';
}
